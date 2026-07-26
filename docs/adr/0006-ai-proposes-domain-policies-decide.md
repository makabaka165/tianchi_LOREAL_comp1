# ADR 0006: AI Proposes, Domain Policies and Humans Decide

Status: Accepted

Model output is limited to structured assessments, reply drafts, suggested actions and evidence-bound risk signals. Deterministic domain policies validate and deduplicate signals before opening a risk alert, and a human must confirm any customer-visible reply or action that can create a service commitment, change a case, trigger compensation or escalate externally. This trades some automation for auditability and prevents hallucinated facts or promises from becoming business state.

## Consequences

- Models cannot directly update orders, service cases, payments, refunds or risk-alert status.
- Suggested actions use an allow-listed command type and validated parameters.
- Critical deterministic rules set a severity floor that model output cannot lower.
- Acceptance, editing, rejection and execution outcomes are retained as evaluation facts.
