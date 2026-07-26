# ADR 0005: Java Orchestrates, Python Provides Bounded AI Capabilities

Status: Accepted

The existing Spring Boot modular monolith remains the application boundary, runtime source of truth and Agent workflow orchestrator for the customer-service competition vertical. Python is used first for offline data analysis, prompt experiments and evaluation; a Python runtime may be deployed only as a stateless model or multimodal capability behind the existing HTTP Tool or model-provider seam. Python does not own business tables, Agent Run state or workflow coordination. This preserves the verified Java runtime while allowing Python-native AI libraries without creating two sources of truth.

## Consequences

- Java owns authentication, tenant/workspace scope, service data, risk lifecycle, Agent Run persistence, audit and SSE events.
- Python contracts are versioned JSON requests and responses with explicit time, size and privacy budgets.
- A Python capability can be removed or replaced without migrating business state.
- Direct database access from Python and circular Java-Python orchestration are prohibited.
