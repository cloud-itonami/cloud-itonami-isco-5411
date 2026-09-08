(ns firestation.governor
  "FirestationGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  station/equipment/logistics-coordination operation an advisor may
  propose. The governor never dispatches hardware itself and NEVER
  lets a proposal exercise, simulate exercising, or propose exercising
  ANY hazardous-structure entry decision, casualty-triage/rescue-
  prioritization decision, medical-treatment decision, or active-
  incident tactical/operational command decision — every one of these
  is permanently out of scope for this actor, not merely gated behind
  escalation. This mirrors the Wave4 person-facing-service safety
  guardrail (ADR-2607152500): decisions directly touching a person's
  life/physical safety during an active emergency always exclude the
  closed op allowlist and always escalate to the human incident
  commander. Modeled on cloud-itonami-isco-3355's caseadmin.governor,
  with the same closed proposal-op allowlist + content-based scope-
  exclusion shape, adapted to this vertical's structure-entry/triage/
  medical-treatment/tactical-command guardrail.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. crew provenance          — the proposing crew-member record must
                                be independently registered AND
                                verified before ANY proposal can commit
                                or escalate. Never trusts the
                                proposal's own claim of who the crew
                                member is.
    2. no-actuation              — proposal :effect must be :propose
                                (the governor never dispatches hardware
                                and never itself performs a tactical or
                                medical action; it only gates what the
                                advisor may commit).
    3. closed op allowlist      — the proposal's :op must be one of the
                                four ops this actor is scoped to
                                (`closed-op-allowlist` below). This is
                                the STRUCTURAL guarantee: no op that
                                resembles deciding structure entry,
                                making a casualty-triage/rescue-
                                prioritization decision, providing
                                medical treatment, or issuing a
                                tactical/operational command exists
                                anywhere in this allowlist — such a
                                proposal cannot even reach a check, let
                                alone pass one. Any :op outside the
                                allowlist is a HARD, PERMANENT block.
    4. station basis             — a proposal for `:log-equipment-record`,
                                `:schedule-crew-operation` or
                                `:coordinate-supply-order` must cite a
                                REGISTERED AND VERIFIED station
                                matching the crew member's own station
                                (`:unknown-station` / `:station-
                                unverified` / `:station-mismatch`).
                                `:flag-readiness-concern` does NOT
                                require an existing station (it is the
                                channel by which a brand-new station is
                                surfaced for human intake).
    5. tactical-assessment forbidden — `:log-equipment-record` is an
                                equipment condition/maintenance-status
                                metadata record ONLY (equipment id,
                                timestamp, condition, maintenance
                                status). Any proposal carrying a
                                tactical/entry-decision field
                                (`log-record-forbidden-keys` below —
                                e.g. `:entry-decision`, `:structure-
                                assessment`, `:go-no-go`) is a HARD,
                                PERMANENT block — this actor never
                                records a tactical assessment or an
                                entry decision, only physical equipment
                                condition.
    6. tactical-assignment forbidden — `:schedule-crew-operation` is
                                shift/training scheduling logistics
                                ONLY. Any proposal carrying active-
                                incident tactical-content fields
                                (`schedule-forbidden-keys` below —
                                e.g. `:incident-tactical-assignment`,
                                `:triage-assignment`, `:command-order`)
                                is a HARD, PERMANENT block — this actor
                                never assigns crew to an active-incident
                                tactical role.
    7. scope exclusion           — independent, DEFENSE-IN-DEPTH layer
                                on top of #3/#5/#6: even for an
                                otherwise-allowed op, any proposal whose
                                free text (`:rationale` or `:note`)
                                names a finalization/execution ACTION
                                for a structure-entry decision, a
                                casualty-triage/rescue-prioritization
                                decision, a medical-treatment decision,
                                or a tactical/operational command
                                (`scope-excluded-terms` below) is a
                                HARD, PERMANENT block, evaluated
                                unconditionally on content. This actor
                                never exercises tactical, triage or
                                medical authority — it only documents
                                equipment/personnel records and
                                coordinates station logistics.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off — these
  are :high/:safety-critical regardless of confidence):
    8. :op :flag-readiness-concern (surfacing an equipment-deficiency,
                                staffing-shortfall or training-gap
                                concern that needs human fire-officer
                                review — ALWAYS requires human review;
                                it is never auto-resolved and never
                                appears in any phase's auto-commit set;
                                this is the ONLY path by which a
                                readiness observation may be surfaced,
                                and the robot's role ends at \"here is
                                the equipment/personnel roster status\"
                                — never \"here is what to do about the
                                fire\" or \"here is who to rescue
                                first\").
    9. an above-threshold :coordinate-supply-order (equipment/apparatus
                                procurement above
                                `supply-cost-escalation-threshold`
                                always needs human sign-off, regardless
                                of confidence — this is an escalation,
                                NOT a hard block, since an over-budget
                                supply request is not itself unsafe).
    10. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [firestation.store :as store]))

(def confidence-floor 0.6)

;; Equipment/apparatus supply orders at or below this estimated cost
;; may be auto-commit-eligible (subject to confidence); above it,
;; ALWAYS escalates to a human regardless of confidence.
(def supply-cost-escalation-threshold 500)

;; The closed proposal-op allowlist. This governor NEVER allows any op
;; outside this set to commit or even escalate — an op outside this
;; set is a HARD, permanent block (see `hard-violations`
;; :op-not-allowed below), not merely un-auto-committable. This is a
;; station/equipment/logistics coordination robot ONLY: it has NO op,
;; anywhere in this allowlist, that resembles deciding hazardous-
;; structure entry, making a casualty-triage/rescue-prioritization
;; decision, providing medical treatment, or issuing an active-
;; incident tactical/operational command. Those capabilities are
;; structurally absent, not gated.
(def closed-op-allowlist
  #{:log-equipment-record :schedule-crew-operation
    :flag-readiness-concern :coordinate-supply-order})

;; :flag-readiness-concern always escalates to a human — never
;; auto-commit-eligible at any phase. It is the ONLY channel through
;; which a readiness observation may be surfaced.
(def ^:private always-escalate-ops #{:flag-readiness-concern})

;; Ops that outside observers might expect a "firefighter actor" to
;; have — named here explicitly (in addition to the closed-allowlist
;; check above) so the exclusion reads as an intentional, documented
;; scope boundary rather than an incidental unknown op. None of these
;; are ever defined as a real op anywhere in this codebase; they exist
;; ONLY as negative-test fixtures proving `closed-op-allowlist` rejects
;; them.
(def scope-excluded-ops
  #{:enter-structure :authorize-structure-entry :commit-to-structure-entry
    :decide-structure-entry :perform-triage :make-triage-decision
    :prioritize-casualty-rescue :prioritize-rescue :provide-medical-treatment
    :administer-medical-treatment :issue-tactical-command
    :direct-incident-response :issue-incident-command-order})

;; log-equipment-record is an equipment condition/maintenance-status
;; metadata record ONLY. A proposal carrying any of these keys is
;; smuggling a tactical assessment or entry decision into what must
;; remain a pure equipment-condition log.
(def log-record-forbidden-keys
  #{:entry-decision :structure-assessment :go-no-go :hazard-clearance
    :entry-authorized? :tactical-assessment :structure-entry-decision})

;; schedule-crew-operation is shift/training scheduling logistics ONLY.
;; A proposal carrying any of these keys is smuggling an active-
;; incident tactical assignment into what must remain pure shift/
;; training scheduling.
(def schedule-forbidden-keys
  #{:incident-tactical-assignment :triage-assignment :command-order
    :incident-command-directive :tactical-role-assignment :rescue-priority})

;; Scope-exclusion terms, phrased as the FINALIZATION/EXECUTION ACTION
;; (never a bare noun like "entry", "triage" or "treatment" alone) — a
;; known self-tripping bug class in this fleet: a bare-noun term list
;; can accidentally match inside the mock advisor's own default
;; rationale text for a legitimate, allowed proposal, causing the
;; actor to self-block on its own happy path. This advisor's default
;; rationale template is "documented <op> for station <id>", which
;; never contains any of these full action phrases. See
;; `firestation.governor-test/
;; default-mock-advisor-proposals-never-self-trip-scope-exclusion`.
(def scope-excluded-terms
  ["commit to structure entry" "committed to structure entry"
   "authorize structure entry" "authorized structure entry"
   "decide to enter the structure" "decided to enter the structure"
   "enter the structure" "entered the structure"
   "make the triage prioritization decision" "made the triage prioritization decision"
   "make the triage decision" "made the triage decision"
   "make the rescue prioritization decision" "made the rescue prioritization decision"
   "prioritize the rescue" "prioritized the rescue"
   "prioritize casualties" "prioritized casualties"
   "provide medical treatment" "provided medical treatment"
   "administer medical treatment" "administered medical treatment"
   "administer treatment" "administered treatment"
   "issue the tactical command" "issued the tactical command"
   "issue the incident command order" "issued the incident command order"
   "direct the incident response" "directed the incident response"
   "建物進入を決定した" "建物進入を許可した" "建物への進入を決定した"
   "トリアージの優先順位を決定した" "救助の優先順位を決定した"
   "医療処置を行った" "応急処置を実施した" "戦術指揮を発令した" "現場指揮命令を発令した"])

(defn out-of-scope?
  "True if any free-text field on `proposal` (:rationale or :note)
  contains a scope-excluded finalization/execution phrase for a
  structure-entry decision, casualty-triage/rescue-prioritization
  decision, medical-treatment decision, or tactical/operational
  command."
  [proposal]
  (let [text (str (:rationale proposal) " " (:note proposal))]
    (boolean (some #(str/includes? text %) scope-excluded-terms))))

(defn- forbidden-keys-present [proposal forbidden-keys]
  (seq (filter #(contains? proposal %) forbidden-keys)))

(def ^:private station-required-ops
  #{:log-equipment-record :schedule-crew-operation :coordinate-supply-order})

(defn- hard-violations [{:keys [proposal]} crew-record station-record]
  (let [{:keys [op station-id]} proposal
        needs-station? (contains? station-required-ops op)]
    (cond-> []
      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は戦術/医療行為を直接実行しない）"})

      (not (contains? closed-op-allowlist op))
      (conj {:rule :op-not-allowed
             :detail "closed allowlist 外の op（建物進入決定・トリアージ/救助優先順位決定・医療処置・戦術指揮の直接実行を含む一切の確定は許可されない）"})

      (nil? crew-record)
      (conj {:rule :unknown-crew :detail "未登録 crew への提案は不可"})

      (and crew-record (not (:verified? crew-record)))
      (conj {:rule :crew-unverified :detail "未検証 crew への提案は不可（登録のみでは不十分）"})

      (and needs-station? (nil? station-id))
      (conj {:rule :missing-station-id :detail "この op には station-id が必須"})

      (and needs-station? station-id (nil? station-record))
      (conj {:rule :unknown-station :detail "未登録 station への提案は不可"})

      (and needs-station? station-record (not (:verified? station-record)))
      (conj {:rule :station-unverified :detail "未検証 station への提案は不可（登録のみでは不十分）"})

      (and needs-station? station-record crew-record
           (not= (:station-id station-record) (:station-id crew-record)))
      (conj {:rule :station-mismatch :detail "station が crew の所属と別 station のもの"})

      (and (= :log-equipment-record op) (seq (forbidden-keys-present proposal log-record-forbidden-keys)))
      (conj {:rule :tactical-assessment-forbidden
             :detail "log-equipment-record は物理的な装備状態記録のみ — 戦術評価・進入決定は永久に禁止"})

      (and (= :schedule-crew-operation op) (seq (forbidden-keys-present proposal schedule-forbidden-keys)))
      (conj {:rule :tactical-assignment-forbidden
             :detail "schedule-crew-operation は日程調整のみ — 現場戦術任務の割当は永久に禁止"})

      (out-of-scope? proposal)
      (conj {:rule :scope-excluded
             :detail "建物進入決定・トリアージ/救助優先順位決定・医療処置・戦術/現場指揮命令を直接確定する提案は恒久的に許可されない（このactorは文書化とロジスティクス調整のみを行う）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `firestation.store/Store`. Pure — never mutates
  the store, never decides structure entry, never makes a triage or
  rescue-prioritization decision, never provides medical treatment,
  never issues a tactical/operational command."
  [_request _context proposal store]
  (let [crew-record (some->> (:crew-id proposal) (store/crew store))
        station-record (some->> (:station-id proposal) (store/station store))
        hard (hard-violations {:proposal proposal} crew-record station-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))
        over-threshold-supply-order?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-escalation-threshold))]
    {:ok? (and (not hard?) (not low?) (not always-risky?) (not over-threshold-supply-order?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky? over-threshold-supply-order?))}))
