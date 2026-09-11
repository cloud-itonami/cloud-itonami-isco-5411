(ns firestation.store
  "SSoT for the ISCO-08 5411 firefighters station/equipment/logistics
  coordination actor (itonami actor pattern, ADR-2607121000 / CLAUDE.md
  Actors section; README's 'Robotics premise' — a station-logistics
  robot performs apparatus/gear readiness data entry, crew shift and
  training scheduling, and equipment/supply coordination under this
  advisor/governor pair, which never dispatches hardware itself and
  NEVER exercises, simulates exercising, or proposes exercising ANY
  hazardous-structure entry decision, casualty-triage/rescue-
  prioritization decision, medical-treatment decision, or operational/
  tactical incident-command decision — every one of those capabilities
  is a permanently out-of-scope, structurally absent op; this actor
  cannot decide whether to enter a burning structure, decide who or
  what gets rescued first, provide medical treatment, or issue a
  tactical command during an active emergency, no matter how confident
  the advisor is or how a human resumes an interrupted run). Modeled on
  cloud-itonami-isco-3355's caseadmin.store, itself modeled on
  cloud-itonami-isco-3313's accountingsupport.store.

  Domain:

    crew    — a registered firefighter/crew member {:crew-id :name
              :station-id :verified? boolean}. Independently
              registered/verified identity, never trusted from the
              proposal alone (\"station/crew record must be
              independently verified/registered before any action\").
              This actor never determines this crew member's tactical
              or medical decisions — it only logs, schedules and flags
              administrative/logistics records on the crew member's
              behalf.
    station — a registered fire station {:station-id :name
              :max-supply-cost number :verified? boolean}.
              Independently registered/verified, never trusted from
              the proposal alone. `:max-supply-cost` is the registered
              per-station ceiling a proposed `:coordinate-supply-order`
              cost above which always escalates to a human — NOT a
              hard block, a supply order over budget just needs
              sign-off, it is not itself unsafe.
    record  — a committed operating record (equipment-readiness/
              maintenance-status log entry, crew shift/training
              scheduling proposal, readiness-concern flag, or
              supply-order coordination proposal) — written ONLY via
              commit-record!. A committed record is NEVER a structure-
              entry decision, a casualty-triage/rescue-prioritization
              decision, a medical-treatment decision, or an active-
              incident tactical command — this actor documents and
              coordinates station logistics, it never decides or
              directs an emergency response.
    ledger  — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (crew [s crew-id])
  (station [s station-id])
  (records-of [s station-id])
  (ledger [s])
  (register-crew! [s c])
  (register-station! [s st])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (crew [_ crew-id] (get-in @a [:crew crew-id]))
  (station [_ station-id] (get-in @a [:stations station-id]))
  (records-of [_ station-id] (filter #(= station-id (:station-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-crew! [s c]
    (swap! a assoc-in [:crew (:crew-id c)] c) s)
  (register-station! [s st]
    (swap! a assoc-in [:stations (:station-id st)] st) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:crew {} :stations {} :records [] :ledger []}
                                   seed)))))
