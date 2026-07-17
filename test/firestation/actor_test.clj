(ns firestation.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [firestation.actor :as actor]
            [firestation.advisor :as advisor]
            [firestation.governor :as governor]
            [firestation.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-crew! st {:crew-id "C-1" :name "FF Alvarez"
                              :station-id "station-9" :verified? true})
    (store/register-station! st {:station-id "station-9"
                                 :max-supply-cost 500 :verified? true})
    st))

;; --- happy paths ------------------------------------------------------

(deftest commits-a-well-formed-equipment-log-entry
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :log-equipment-record :stake :low :crew-id "C-1" :station-id "station-9"
                  :equipment-id "APP-1" :condition :serviceable :timestamp "2026-07-14T10:00:00Z"
                  :maintenance-status :current}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "station-9"))))))

(deftest commits-a-crew-operation-scheduling
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :schedule-crew-operation :stake :low :crew-id "C-1" :station-id "station-9"
                  :operation-type :training :proposed-time "2026-07-20T09:00:00Z" :location "drill yard"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "station-9"))))))

(deftest commits-an-at-or-below-threshold-supply-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :coordinate-supply-order :stake :low :crew-id "C-1" :station-id "station-9"
                  :item "turnout gear" :cost 40 :vendor "FireSupplyCo"}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))))

;; --- hard blocks --------------------------------------------------------

(deftest holds-an-unverified-crew-proposal
  (let [st (fresh-store)]
    (store/register-crew! st {:crew-id "C-2" :name "Unverified"
                              :station-id "station-9" :verified? false})
    (let [graph (actor/build-graph {:store st})
          request {:op :log-equipment-record :stake :low :crew-id "C-2" :station-id "station-9"
                    :equipment-id "APP-1" :condition :serviceable :timestamp "2026-07-14T10:00:00Z"}
          result (actor/run-request! graph request {} "thread-4")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "station-9"))))))

(deftest holds-an-unverified-station-proposal
  (let [st (fresh-store)]
    (store/register-station! st {:station-id "station-2" :max-supply-cost 500 :verified? false})
    (let [graph (actor/build-graph {:store st})
          request {:op :log-equipment-record :stake :low :crew-id "C-1" :station-id "station-2"
                    :equipment-id "APP-1" :condition :serviceable :timestamp "2026-07-14T10:00:00Z"}
          result (actor/run-request! graph request {} "thread-5")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "station-2"))))))

(deftest holds-a-station-mismatch-proposal
  (let [st (fresh-store)]
    (store/register-station! st {:station-id "station-3" :max-supply-cost 500 :verified? true})
    (let [graph (actor/build-graph {:store st})
          request {:op :log-equipment-record :stake :low :crew-id "C-1" :station-id "station-3"
                    :equipment-id "APP-1" :condition :serviceable :timestamp "2026-07-14T10:00:00Z"}
          result (actor/run-request! graph request {} "thread-6")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "station-3"))))))

(deftest holds-a-tactical-assessment-attempt
  (testing "log-equipment-record can never carry a tactical assessment or entry decision, even via a custom advisor"
    (let [st (fresh-store)
          rogue (reify advisor/Advisor
                  (-advise [_ _store _request]
                    {:op :log-equipment-record :effect :propose :crew-id "C-1" :station-id "station-9"
                     :entry-decision "safe to enter" :stake :low :confidence 0.9
                     :rationale "documented log-equipment-record for station station-9"}))
          graph (actor/build-graph {:store st :advisor rogue})
          result (actor/run-request! graph {:op :log-equipment-record} {} "thread-7")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "station-9"))))))

(deftest holds-a-tactical-assignment-attempt
  (testing "schedule-crew-operation can never carry an active-incident tactical assignment, even via a custom advisor"
    (let [st (fresh-store)
          rogue (reify advisor/Advisor
                  (-advise [_ _store _request]
                    {:op :schedule-crew-operation :effect :propose :crew-id "C-1" :station-id "station-9"
                     :incident-tactical-assignment "interior attack team" :stake :low :confidence 0.9
                     :rationale "documented schedule-crew-operation for station station-9"}))
          graph (actor/build-graph {:store st :advisor rogue})
          result (actor/run-request! graph {:op :schedule-crew-operation} {} "thread-8")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "station-9"))))))

(deftest holds-every-scope-excluded-op-attempt-even-via-a-rogue-advisor
  (testing "no path through this actor can decide structure entry, make a casualty-triage/rescue-prioritization
            decision, provide medical treatment, or issue a tactical/operational command — proven by forcing a
            rogue advisor to propose each named op"
    (doseq [op governor/scope-excluded-ops]
      (let [st (fresh-store)
            rogue (reify advisor/Advisor
                    (-advise [_ _store _request]
                      {:op op :effect :propose :crew-id "C-1" :station-id "station-9"
                       :stake :low :confidence 0.99
                       :rationale (str "documented " (name op) " for station station-9")}))
            graph (actor/build-graph {:store st :advisor rogue})
            result (actor/run-request! graph {:op op} {} (str "thread-scope-" (name op)))]
        (is (= :hold (:disposition (:state result))) (str "op " op " was not held"))
        (is (empty? (store/records-of st "station-9")) (str "op " op " committed a record"))))))

(deftest holds-a-scope-excluded-rationale-attempt-even-via-a-rogue-advisor
  (testing "an otherwise-allowed op whose rationale smuggles a finalization/execution action phrase is held"
    (let [st (fresh-store)
          rogue (reify advisor/Advisor
                  (-advise [_ _store _request]
                    {:op :log-equipment-record :effect :propose :crew-id "C-1" :station-id "station-9"
                     :equipment-id "APP-1" :condition :serviceable :timestamp "2026-07-14T10:00:00Z"
                     :stake :low :confidence 0.99
                     :rationale "logged the gear in order to enter the structure"}))
          graph (actor/build-graph {:store st :advisor rogue})
          result (actor/run-request! graph {:op :log-equipment-record} {} "thread-9")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "station-9"))))))

;; --- escalation / human-in-the-loop --------------------------------------

(deftest interrupts-then-approves-flag-readiness-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :flag-readiness-concern :stake :low :crew-id "C-1" :station-id "station-NEW"
                  :reason :equipment-deficiency :note "new station intake needs equipment audit"}
        interrupted (actor/run-request! graph request {} "thread-10")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "station-NEW")))
    (let [resumed (actor/approve! graph "thread-10")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "station-NEW")))))))

(deftest interrupts-then-approves-above-threshold-supply-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :coordinate-supply-order :stake :low :crew-id "C-1" :station-id "station-9"
                  :item "replacement SCBA sets" :cost 5000 :vendor "FireSupplyCo"}
        interrupted (actor/run-request! graph request {} "thread-11")]
    (is (= :interrupted (:status interrupted)))
    (let [resumed (actor/approve! graph "thread-11")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record]))))))
