(ns firestation.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [firestation.store :as store]
            [firestation.advisor :as advisor]
            [firestation.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-crew! st {:crew-id "C-1" :name "FF Alvarez"
                              :station-id "station-9" :verified? true})
    (store/register-station! st {:station-id "station-9"
                                 :max-supply-cost 500 :verified? true})
    st))

(defn- log-op []
  {:op :log-equipment-record :effect :propose :crew-id "C-1" :station-id "station-9"
   :equipment-id "APP-1" :condition :serviceable :timestamp "2026-07-14T10:00:00Z"
   :maintenance-status :current :stake :low :confidence 0.9
   :rationale "documented log-equipment-record for station station-9"})

(defn- schedule-op []
  {:op :schedule-crew-operation :effect :propose :crew-id "C-1" :station-id "station-9"
   :operation-type :training :proposed-time "2026-07-20T09:00:00Z"
   :location "station-9 drill yard" :stake :low :confidence 0.9
   :rationale "documented schedule-crew-operation for station station-9"})

(defn- flag-op
  ([] (flag-op nil))
  ([station-id]
   {:op :flag-readiness-concern :effect :propose :crew-id "C-1" :station-id station-id
    :reason :equipment-deficiency :note "SCBA tank pressure below threshold" :stake :low :confidence 0.9
    :rationale "documented flag-readiness-concern for station (no station yet — new-station intake)"}))

(defn- supply-op [cost]
  {:op :coordinate-supply-order :effect :propose :crew-id "C-1" :station-id "station-9"
   :item "SCBA replacement tank" :cost cost :vendor "FireSupplyCo" :stake :low :confidence 0.9
   :rationale "documented coordinate-supply-order for station station-9"})

(def ^:private req {})

;; --- happy path -----------------------------------------------------

(deftest ok-well-formed-log-entry
  (let [st (fresh-store)
        v (governor/check req {} (log-op) st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-well-formed-crew-scheduling
  (let [st (fresh-store)
        v (governor/check req {} (schedule-op) st)]
    (is (:ok? v))))

(deftest ok-at-or-below-threshold-supply-order
  (let [st (fresh-store)
        v (governor/check req {} (supply-op 250) st)]
    (is (:ok? v))))

(deftest ok-at-exact-supply-cost-threshold-boundary
  (testing "the supply-cost escalation threshold is inclusive (exactly-at-threshold does not escalate)"
    (let [st (fresh-store)
          v (governor/check req {} (supply-op governor/supply-cost-escalation-threshold) st)]
      (is (:ok? v))
      (is (not (:escalate? v))))))

;; --- crew provenance ----------------------------------------------

(deftest hard-on-unregistered-crew
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :crew-id "ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-crew (:rule %)) (:violations v)))))

(deftest hard-on-unverified-crew
  (let [st (fresh-store)]
    (store/register-crew! st {:crew-id "C-2" :name "Unverified"
                              :station-id "station-9" :verified? false})
    (let [v (governor/check req {} (assoc (log-op) :crew-id "C-2") st)]
      (is (:hard? v))
      (is (some #(= :crew-unverified (:rule %)) (:violations v))))))

;; --- station provenance ---------------------------------------------

(deftest hard-on-missing-station-id
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :station-id nil) st)]
    (is (:hard? v))
    (is (some #(= :missing-station-id (:rule %)) (:violations v)))))

(deftest hard-on-unknown-station
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :station-id "station-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-station (:rule %)) (:violations v)))))

(deftest hard-on-unverified-station
  (let [st (fresh-store)]
    (store/register-station! st {:station-id "station-2" :max-supply-cost 500 :verified? false})
    (let [v (governor/check req {} (assoc (log-op) :station-id "station-2") st)]
      (is (:hard? v))
      (is (some #(= :station-unverified (:rule %)) (:violations v))))))

(deftest hard-on-station-mismatch
  (let [st (fresh-store)]
    (store/register-station! st {:station-id "station-3" :max-supply-cost 500 :verified? true})
    (let [v (governor/check req {} (assoc (log-op) :station-id "station-3") st)]
      (is (:hard? v))
      (is (some #(= :station-mismatch (:rule %)) (:violations v))))))

(deftest flag-readiness-concern-does-not-require-existing-station
  (testing "flag-readiness-concern is the channel by which a brand-new station is surfaced for human intake"
    (let [st (fresh-store)
          v (governor/check req {} (flag-op nil) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

;; --- no-actuation / closed allowlist ----------------------------------

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-enter-structure
  (testing "no path through this actor can decide structure entry — no such op exists in the allowlist to begin
            with; this asserts the governor also rejects one forged onto a proposal"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :enter-structure) st)]
      (is (:hard? v))
      (is (some #(= :op-not-allowed (:rule %)) (:violations v))))))

(deftest hard-on-op-not-allowed-make-triage-decision
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :make-triage-decision) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-provide-medical-treatment
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :provide-medical-treatment) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-issue-tactical-command
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :issue-tactical-command) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-prioritize-casualty-rescue
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :prioritize-casualty-rescue) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest every-scope-excluded-op-name-is-rejected
  (testing "every explicitly named scope-excluded op fixture is a hard, permanent block"
    (let [st (fresh-store)]
      (doseq [op governor/scope-excluded-ops]
        (let [v (governor/check req {} (assoc (log-op) :op op) st)]
          (is (:hard? v) (str "op " op " was not hard-blocked"))
          (is (some #(= :op-not-allowed (:rule %)) (:violations v))
              (str "op " op " did not trip :op-not-allowed")))))))

;; --- tactical-assessment / tactical-assignment forbidden -------

(deftest hard-on-tactical-assessment-forbidden
  (testing "log-equipment-record is a physical equipment-condition record only — tactical assessment is forbidden"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :entry-decision "safe to enter") st)]
      (is (:hard? v))
      (is (some #(= :tactical-assessment-forbidden (:rule %)) (:violations v))))))

(deftest hard-on-tactical-assessment-forbidden-go-no-go-key
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :go-no-go :go) st)]
    (is (:hard? v))
    (is (some #(= :tactical-assessment-forbidden (:rule %)) (:violations v)))))

(deftest hard-on-tactical-assignment-forbidden
  (testing "schedule-crew-operation never records an active-incident tactical assignment"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op) :incident-tactical-assignment "interior attack team") st)]
      (is (:hard? v))
      (is (some #(= :tactical-assignment-forbidden (:rule %)) (:violations v))))))

(deftest hard-on-tactical-assignment-forbidden-triage-assignment-key
  (let [st (fresh-store)
        v (governor/check req {} (assoc (schedule-op) :triage-assignment "red tag first") st)]
    (is (:hard? v))
    (is (some #(= :tactical-assignment-forbidden (:rule %)) (:violations v)))))

;; --- scope-excluded rationale (defense-in-depth) -----------------------

(deftest hard-on-scope-excluded-structure-entry-rationale
  (testing "a proposal on an otherwise-allowed op whose rationale names a finalization action for structure entry
            is a permanent HARD block, independent of the op-allowlist check"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :rationale "logged the gear in order to enter the structure") st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-triage-rationale
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :rationale "logged the roster to make the triage decision") st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-medical-treatment-rationale
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :rationale "logged the kit in order to provide medical treatment") st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-tactical-command-rationale
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :rationale "logged the apparatus to issue the tactical command") st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-note-field
  (testing "the scope-exclusion check also inspects :note (used by flag-readiness-concern)"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (flag-op "station-9") :note "recommend we enter the structure now") st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded (:rule %)) (:violations v))))))

;; --- escalation ---------------------------------------------------------

(deftest always-escalates-flag-readiness-concern-even-at-high-confidence
  (testing "surfacing a readiness concern always requires human review"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (flag-op "station-9") :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-above-threshold-supply-order
  (testing "an equipment/apparatus supply order above the cost threshold always needs human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (supply-op (+ governor/supply-cost-escalation-threshold 1))
                                          :confidence 0.99)
                            st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

;; --- fleet-known self-trip regression -----------------------------------

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own rationale text for every op in the closed allowlist never contains
            a scope-excluded finalization/execution phrase for a structure-entry decision, casualty-triage/
            rescue-prioritization decision, medical-treatment decision, or tactical/operational command"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          requests [{:op :log-equipment-record :crew-id "C-1" :station-id "station-9" :stake :low
                     :equipment-id "APP-1" :condition :serviceable :timestamp "2026-07-14T10:00:00Z"}
                    {:op :schedule-crew-operation :crew-id "C-1" :station-id "station-9" :stake :low
                     :operation-type :shift :proposed-time "2026-07-20T09:00:00Z" :location "station-9"}
                    {:op :flag-readiness-concern :crew-id "C-1" :station-id "station-9" :stake :low
                     :reason :training-gap :note "annual SCBA recertification lapsing in 30 days"}
                    {:op :flag-readiness-concern :crew-id "C-1" :station-id nil :stake :low
                     :reason :equipment-deficiency :note "new station intake needs equipment audit"}
                    {:op :coordinate-supply-order :crew-id "C-1" :station-id "station-9" :stake :low
                     :item "turnout gear" :cost 40 :vendor "FireSupplyCo"}]]
      (doseq [req' requests]
        (let [proposal (advisor/-advise adv st req')]
          (is (false? (governor/out-of-scope? proposal))
              (str "op " (:op req') " self-tripped scope-exclusion: " (:rationale proposal)))
          (let [v (governor/check {} {} proposal st)]
            (is (not (contains? (set (map :rule (:violations v))) :scope-excluded))
                (str "op " (:op req') " tripped :scope-excluded in governor/check"))
            (is (not (contains? (set (map :rule (:violations v))) :op-not-allowed))
                (str "op " (:op req') " tripped :op-not-allowed in governor/check"))))))))
