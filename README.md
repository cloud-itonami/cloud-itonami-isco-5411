# cloud-itonami-isco-5411

Open Occupation Blueprint for **ISCO-08 5411**: Firefighters.

This repository designs a forkable OSS business for a fire-station
equipment-readiness and administrative-logistics coordination
practice: a station-logistics robot manages apparatus/gear readiness
records, crew shift and training scheduling, and equipment/supply
coordination under a governor-gated actor — and structurally **never**
decides hazardous-structure entry, makes a casualty-triage or
resource-prioritization decision, provides medical treatment, or
issues any operational/tactical command during an active emergency
response.

## This actor has no structure-entry, triage, medical-treatment, or tactical-command authority

Firefighters make life-and-death decisions under time pressure: which
structure to enter, how to prioritize casualties/resources when they
are limited, and physical rescue/extraction actions. **This actor is a
station/equipment/logistics coordination robot ONLY.** It has NO op,
anywhere in its allowlist, that resembles deciding whether to enter a
hazardous structure, making a casualty-triage/resource-prioritization
decision (deciding who or what gets rescued first when resources are
limited), providing medical treatment, or issuing any operational/
tactical command during an active emergency response. These are
**structurally absent from the closed op-allowlist entirely**, not
merely gated behind escalation — under any circumstance, at any
confidence level, in any phase. Any observation the robot logs that
suggests a station needs human attention is surfaced ONLY via an
always-escalating `:flag-readiness-concern` op that a human fire
officer reviews and acts on entirely themselves. This mirrors the
Wave4 person-facing-service safety guardrail (ADR-2607152500):
decisions directly touching a person's life or physical safety during
an active emergency always exclude the closed op allowlist and always
escalate. During an active emergency incident, this actor's role ends
at "here is the equipment/personnel roster status" — it has zero
authority over tactical decisions, which remain entirely with the
human incident commander, at all times, with zero exception.

**Maturity: `:implemented`.** `src/firestation/` implements the
`FirestationActor` as a `langgraph.graph/state-graph`
(`firestation.actor`) wired to a `Station Logistics Advisor`
(`firestation.advisor`) and an independent `FirestationGovernor`
(`firestation.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 43 tests / 150 assertions green (`kbb -M:test`).

HARD invariants (always hold, never overridable): crew provenance (a
proposal must resolve to an independently registered AND verified
crew-member record), a closed four-op proposal allowlist (any op
outside it — including anything that would decide structure entry,
make a casualty-triage/resource-prioritization decision, provide
medical treatment, or issue a tactical/operational command — is a
permanent HARD block, because no such op exists in the allowlist to
begin with), no-actuation (`:effect` must be `:propose`), a
registered-and-verified station basis (for the three ops that
reference one), a tactical-assessment-forbidden check
(`:log-equipment-record` may only carry physical equipment condition/
maintenance metadata, never a tactical assessment or entry decision),
a tactical-assignment-forbidden check (`:schedule-crew-operation` may
only carry shift/training scheduling logistics, never an active-
incident tactical assignment), and a content-based scope-exclusion
check: any proposal whose free text names a finalization/execution
action for a structure-entry decision, a casualty-triage/resource-
prioritization decision, a medical-treatment decision, or a tactical/
operational command is a permanent HARD block, independent of and in
addition to the op-allowlist check. This actor **never** exercises,
simulates exercising, or proposes exercising any decision to enter/
not-enter a hazardous structure, any casualty-triage or resource-
prioritization decision, any medical-treatment decision, or any
operational/tactical command decision during an active emergency
response — it only documents equipment/personnel records and
coordinates station logistics.

Always-escalate (human sign-off regardless of confidence, mapping this
repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-readiness-concern` (surfacing an equipment-deficiency,
staffing-shortfall or training-gap concern that needs human fire-
officer review — always requires human review; never auto-resolved,
never in any phase's auto-commit set — this is the ONLY channel by
which a readiness observation may be surfaced) and any
`:coordinate-supply-order` above the registered per-station cost
threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical/administrative domain work**. Here a station-
logistics robot performs apparatus/gear readiness data entry, crew
shift and training scheduling, and equipment/apparatus supply
coordination under an actor that proposes actions and an independent
**FirestationGovernor** that gates them. The governor never dispatches
hardware itself; `:high`/`:safety-critical` actions (such as flagging
a readiness concern, or an above-threshold supply order) require human
sign-off — and no action in this actor's closed op allowlist can ever
decide structure entry, make a casualty-triage/resource-prioritization
decision, provide medical treatment, or issue a tactical/operational
command during an active emergency. During an active incident, this
actor's role ends at "here is the equipment/personnel roster status" —
tactical authority belongs entirely to the human incident commander.

## Core Contract

```text
crew intake queue + station roster directory + supply policy
        |
        v
Station Logistics Advisor -> FirestationGovernor -> log record/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
decide structure entry, make a casualty-triage/resource-prioritization
decision, provide medical treatment, issue a tactical/operational
command, suppress an operating record, or disclose sensitive data
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `5411`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
