# Governance

`cloud-itonami-isco-5411` is an OSS open-occupation blueprint. Governance covers
both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Advisor cannot directly dispatch robot actions or disclose records.
- Firestation Governor remains independent of the advisor.
- hard policy violations cannot be overridden by human approval.
- the closed proposal-op allowlist NEVER gains an op that decides
  hazardous-structure entry, makes a casualty-triage or resource-
  prioritization decision, provides medical treatment, or issues an
  operational/tactical command during an active emergency response, or
  that otherwise exercises any tactical, triage, or medical authority —
  this is a permanent scope boundary of the project, not subject to
  normal maintainer discretion.
- every commit, hold and approval path is auditable.
- real crew/apparatus/incident/casualty/patient data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and data-flow
review.

Certified operators can lose certification for:

- bypassing policy checks
- mishandling crew/apparatus/incident/casualty/patient/operator data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
- adding, or attempting to add, any capability that decides hazardous-
  structure entry, makes a casualty-triage or resource-prioritization
  decision, provides medical treatment, or issues an operational/
  tactical command during an active emergency response
