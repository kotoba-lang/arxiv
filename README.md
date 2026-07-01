# kotoba-lang/arxiv

`arxiv` as an organism actor: a Clojure/EDN knowledge and capability repo for
policy-aware arXiv search, package validation, submission preparation, and
human-approved publication workflows.

This repo implements the ADR-0023 pattern:

```text
Actor -> Capability -> Skill -> Yorishiro route
```

The arXiv actor is not an official arXiv service. It is a local organism actor
that represents arXiv-facing work inside an agent world, with domain facts,
policy, skills, route selection, and approval gates.

## Layout

```text
actors/arxiv/actor.edn          actor manifest
actors/arxiv/facts/*.edn        domain knowledge
actors/arxiv/skills/*.edn       executable skill recipes
actors/arxiv/yorishiro.edn      API/browser/computer/human surfaces
src/arxiv_kotoba/*.cljc         pure dispatcher/runtime
test/arxiv_kotoba/*_test.cljc   contract tests
```

## Commands

```bash
clojure -M:test
```
