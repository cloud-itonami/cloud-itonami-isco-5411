(ns firestation.advisor
  "Station Logistics Advisor — the advisor named in this repository's
  README, proposing a station equipment/logistics-coordination
  operation (log an apparatus/gear readiness-and-maintenance record,
  schedule a crew shift or training operation, flag a readiness
  concern for human fire-officer review, or coordinate an equipment/
  apparatus supply order) from a crew member's intake queue, station
  roster and supply policy. Swappable mock/llm; the advisor ONLY
  proposes — `firestation.governor` checks crew/station verification
  and scope independently and always escalates readiness-concern flags
  and above-threshold supply orders. Modeled on cloud-itonami-isco-3355's
  advisor.

  This advisor NEVER proposes deciding hazardous-structure entry,
  making a casualty-triage/rescue-prioritization decision, providing
  medical treatment, or issuing an active-incident tactical/operational
  command — no such op exists anywhere in the closed allowlist below
  (`firestation.governor/closed-op-allowlist`), and the rationale text
  this advisor emits never uses a finalization/execution phrase for any
  of those actions (`firestation.governor/scope-excluded-terms`), so
  the advisor's own DEFAULT proposals never self-trip the governor's
  scope-exclusion check (see `firestation.governor-test/
  default-mock-advisor-proposals-never-self-trip-scope-exclusion`).
  Any observation suggesting a station needs human attention is
  surfaced ONLY via `:flag-readiness-concern`, which always escalates
  to a human fire officer and never auto-commits — the robot's role
  ends at \"here is the equipment/personnel roster status\", never
  \"here is what to do about the fire\" or \"here is who to rescue
  first\".

  A proposal:
  {:op :log-equipment-record|:schedule-crew-operation|
       :flag-readiness-concern|:coordinate-supply-order
   :effect :propose :crew-id str :station-id (str or nil, only nil
   for :flag-readiness-concern) :stake kw :confidence n
   :rationale str, plus op-specific fields (:equipment-id/:condition/
   :maintenance-status/:timestamp for log-equipment-record;
   :operation-type/:proposed-time/:location for
   schedule-crew-operation; :reason/:note for
   flag-readiness-concern; :item/:cost/:vendor for
   coordinate-supply-order)}"
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- rationale-for [op station-id]
  (str "documented " (name op)
       (if station-id (str " for station " station-id) " (no station yet — new-station intake)")))

(defn- infer [_store {:keys [op stake crew-id station-id] :as request}]
  (let [base {:op op
              :effect :propose
              :crew-id crew-id
              :station-id station-id
              :stake (or stake :low)
              :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
              :rationale (rationale-for op station-id)}]
    (merge base
           (case op
             :log-equipment-record
             (select-keys request [:equipment-id :condition :maintenance-status :timestamp])
             :schedule-crew-operation
             (select-keys request [:operation-type :proposed-time :location])
             :flag-readiness-concern
             (select-keys request [:reason :note])
             :coordinate-supply-order
             (select-keys request [:item :cost :vendor])
             {}))))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a fire station equipment-readiness and administrative-
   logistics coordination advisor for a fire crew. Given a request,
   propose an :op, the :crew-id and (when relevant) :station-id plus
   the op's own fields, an honest :confidence and a :stake. You are a
   station/equipment/logistics coordination robot ONLY — you help log
   apparatus/gear readiness and maintenance-status records, schedule
   crew shifts and training operations, and coordinate equipment/
   apparatus supply orders. Never propose an op outside the closed
   four-op allowlist (:log-equipment-record, :schedule-crew-operation,
   :flag-readiness-concern, :coordinate-supply-order), and NEVER
   propose deciding whether to enter a hazardous structure, making a
   casualty-triage or resource-prioritization decision, providing
   medical treatment, or issuing any operational/tactical command
   during an active emergency response — that authority does not exist
   for you, under any circumstance, at any confidence level, in any
   phase. A :log-equipment-record entry is equipment condition/
   maintenance metadata only, never a tactical assessment or an entry
   decision. A :schedule-crew-operation proposal is shift/training
   scheduling logistics only — never an active-incident tactical
   assignment. During an active emergency incident this actor's role
   ends at reporting equipment/personnel roster status — tactical
   authority belongs entirely to the human incident commander. Any
   indication that a station needs human fire-officer attention must
   be surfaced only via :flag-readiness-concern, which always requires
   human review regardless of confidence. The governor independently
   verifies crew/station registration and always escalates readiness-
   concern flags and above-threshold supply orders to a human.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
