# ADR 0007: Typed Queries Own Live Facts, RAG Owns Unstructured Knowledge

Status: Accepted

Conversation messages, order snapshots, service cases and active risk alerts are retrieved through typed application queries or Tools and retain stable source references. RAG is reserved for versioned policies, service procedures, product guidance and other unstructured knowledge. This prevents stale vector results from being presented as current transactional facts while preserving semantic retrieval where exact keys and relational joins are insufficient.

## Consequences

- Live business facts are never embedded as the authoritative read path.
- Every generated factual claim must cite either a typed source reference or a published knowledge chunk.
- Knowledge re-indexing cannot change an order, case or alert result.
- Workflow evaluation measures factual accuracy separately from knowledge-retrieval relevance.
