# Contributing

`cloud-itonami-isco-5411` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
kbb -M:dev:test
kbb -M:lint
```

Keep changes small and include tests for policy, audit, store or disclosure
behavior.

## Rules

- Do not commit real crew, apparatus, incident, casualty, patient, or
  operator data, credentials or operating documents.
- Keep production writes and disclosures behind Firestation Governor.
- **Never add an op that decides hazardous-structure entry, makes a
  casualty-triage or resource-prioritization decision, provides
  medical treatment, or issues an operational/tactical command during
  an active emergency response, or that otherwise exercises any
  tactical, triage, or medical authority.** This actor's closed
  proposal-op allowlist is a hard scope boundary, not a starting point
  to extend. Any PR that proposes such an op will be rejected.
- Treat this occupation's workflows as high-risk: add tests for permission,
  purpose, safety and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
