---
name: dto-addition-or-field-extension
description: Workflow command scaffold for dto-addition-or-field-extension in gateway.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /dto-addition-or-field-extension

Use this workflow when working on **dto-addition-or-field-extension** in `gateway`.

## Goal

Add new DTOs or extend existing DTOs with new fields, and update related tests and documentation.

## Common Files

- `src/main/java/phiner/de5/net/gateway/dto/*.java`
- `docs/redis_api.md`
- `src/test/java/phiner/de5/net/gateway/dto/*.java`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Modify or add Java DTO class in src/main/java/phiner/de5/net/gateway/dto/
- Update related service/strategy/listener code if needed
- Update docs/redis_api.md with new fields or DTO structure
- Add or update corresponding test in src/test/java/phiner/de5/net/gateway/dto/

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.