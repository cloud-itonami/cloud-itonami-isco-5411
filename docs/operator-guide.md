# Operator Guide

## First Deployment

1. Define the operator's station/department scope and crew-intake process.
2. Define consent and purpose categories for equipment/personnel data handling.
3. Run synthetic operating stations (no real crew, apparatus, incident,
   casualty, or patient data in this repository).
4. Enable human-reviewed sign-off for `:high`/`:safety-critical` actions —
   including every `:flag-readiness-concern` and every above-threshold
   `:coordinate-supply-order`.
5. Measure operating outcomes and audit coverage.

## Minimum Production Controls

- consent and disclosure log
- safety-critical escalation path
- provenance for all operating records
- human review for high-risk readiness concerns
- audit export for all gated actions

## No Structure-Entry, Triage, Medical-Treatment, or Tactical-Command Authority

This actor is a station/equipment/logistics coordination robot ONLY.
Operators MUST NOT configure, extend or fork this actor to add an op
that decides hazardous-structure entry, makes a casualty-triage or
resource-prioritization decision, provides medical treatment, or
issues an operational/tactical command during an active emergency
response, or that otherwise exercises any tactical, triage, or medical
authority. Any such change removes the structural guarantee this
repository is built around and voids certification (see
[`GOVERNANCE.md`](../GOVERNANCE.md)). Every readiness observation must
route through `:flag-readiness-concern` to a human fire officer — the
robot's role ends at "here is the equipment/personnel roster status,"
never "here is what to do about the fire" or "here is who to rescue
first." During an active emergency incident, all tactical decisions —
including whether to enter a structure, who or what to rescue first,
and any medical intervention — remain entirely with the human incident
commander, at all times, with zero exception.

## Certification

Certified operators must prove that the governor gates every
safety-critical robot action, that safety-critical risks escalate to
humans, and that no build of this actor has ever added an op resembling
a structure-entry decision, a casualty-triage/resource-prioritization
decision, a medical-treatment decision, or a tactical/operational
command to the closed allowlist.
