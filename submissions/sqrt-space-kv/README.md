# arXiv submission: √S KV residency (Williams transfer)

Uses the **kotoba-lang/arxiv** organism actor (`:actor/arxiv`) workflow:

```text
validate-package → login → create-submission (draft) → upload-source
  → pending-human-final-submit → final-submit (requires human approval)
```

## Package

| Field | Value |
|-------|--------|
| Title | Simulating Autoregressive Memory with Square-Root Space… |
| Primary | `cs.LG` |
| Cross-list | `cs.CL`, `cs.CC` |
| License | CC BY 4.0 |
| Source | `build/sqrt_space_kv-arxiv-source.tar.gz` |
| PDF | `sqrt_space_kv.pdf` (via `make`) |
| Draft id | **7807285** (created 2026-07-09 under account junkawasaki) |

## Build

```bash
cd submissions/sqrt-space-kv
make all      # tectonic → PDF
make arxiv    # tar.gz with .tex + .bib + .bbl
```

## Resume at arXiv (human)

1. Open https://arxiv.org/login → https://arxiv.org/user/
2. Open draft **7807285** or **START NEW SUBMISSION** if expired.
3. On **Start**: certify contact, scroll+accept Submittal Agreement modal,
   author = self, license = CC BY, archive = Computer Science, class = Machine Learning (`cs.LG`).
4. **Add Files**: upload `build/sqrt_space_kv-arxiv-source.tar.gz`.
5. Process → Metadata (title/abstract/comments from `package.edn` / `abstract.txt`).
6. Preview → **Submit** (this is `:arxiv/final-submit` — public record).

## Automation notes

- `submit_browser.py` / `submit_v2.py`: Playwright scaffolding matching actor skills.
- Agreement modal (`#accept-terms`) stays disabled until the agreement is scrolled;
  headless automation is brittle — prefer human completion of the Start form.
- Actor gate: `:arxiv/final-submit` **requires** `:human-approval` (skill
  `actors/arxiv/skills/final-submit.edn`).

## Related code

- Paper body (markdown): `gftdcojp/cloud-murakumo/docs/paper/sqrt_space_llm_kv.md`
- Modal/Mac benches: `gftdcojp/cloud-murakumo/docs/benchmarks/`
