# `tck/tools` — corpus and inventory generators

These scripts are the **merge/generation tools** for the portable corpus and the
requirement inventory. They are history, not build steps: the checked-in corpus,
inventory, and profile are the artifacts of record, and the build never runs a
generator. `verify_regen.py` is what keeps that arrangement honest.

## The relationship a tool can have to a test directory

Collapsing these three was how a false "everything is reproducible" belief arose,
so they are kept separate in `verify_regen.py` and reported separately:

| Relationship | Count | Test IDs | Meaning |
|---|---|---|---|
| self-contained | 412 | `SOL-TCK-0092..0493 plus 0496..0498 plus 0500..0506` | the tool writes `main.sol` **and** the manifest; running it from an empty corpus reproduces the whole directory byte-for-byte |
| manifest-only | 16 | `SOL-TCK-0076..0091` | `gen11.py` writes manifests over `main.sol` sources that were **hand-authored and read, not produced**; the programs are not reproducible by anything here |
| unowned | 76 | `SOL-TCK-0001..0075, 0499` | no committed tool writes these at all — the earliest batches, whose per-batch steps were not preserved as tools |

## `verify_regen.py`

Builds a throwaway repository containing **only** `docs/LANGUAGE_SPEC.md`, an
*empty* corpus, and a *valid-but-empty* inventory and profile; runs the generators
into it; and requires the self-contained output to equal the committed files byte
for byte, and the inventory and profile records those tools write to equal the
committed records. Python-only, consistent with the rest of the TCK gate.

It is wired into `tck/tck-check.sh`, so it runs on every build before compilation.

Two guards make the reported numbers load-bearing rather than decorative:

* `SELF_CONTAINED_FLOOR` — the reproducible surface may not shrink silently.
* `UNOWNED_CEILING` — the corpus may not grow by a batch whose generator was not
  committed. Adding a batch the way recent batches are added keeps this satisfied
  automatically; adding one by hand does not.

The unowned and manifest-only sets are printed on **every** run. They are a
disclosed gap in provenance, not a pass.

## Why this check builds from an empty corpus

An earlier version copied the committed corpus into its working directory first.
Every generator here is idempotent — skip-if-exists, or verify-equals on rerun —
so the copy made the comparison trivially empty and the check reported success
over a test file that had just been deliberately corrupted. Seeding inputs for the
manifest-only tool is still necessary, because that tool's contract is to read
sources; it is done *after* the self-contained tools run and only for that tool's
own directories, so it cannot hand a self-contained tool a directory to skip.

## Adding a batch

Write a generator that emits both sources and manifests, commit it here, and add
it to `SELF_CONTAINED`. `UNOWNED_CEILING` then enforces the new rule for you. A
generator that only records manifests must go in `MANIFEST_ONLY`, and the batch it
covers is then explicitly *not* reproducible as programs — which the tool prints
rather than hides.

## Falsifiability

Each guard in `verify_regen.py` was proven to fire by injection: appending a byte
to a self-contained program, corrupting a manifest-only manifest, editing an owned
requirement away from its generator's record, and adding a test directory with no
generator. Each produced exactly the intended failure; a corrupted *unowned*
program correctly does not fail, because no committed generator claims to produce
it — which is precisely why those 76 directories are named on every run instead of
being counted as covered.

## `sync_counts.py`

`../IMPLEMENTATION_PLAN.md` and this file restate roughly twenty figures — requirement,
manifest, coverage, self-test, oracle-quote, refusal, and provenance counts — and
`tests/run_selftests.py` fails when any of them is stale. `sync_counts.py` recomputes
them from `validate`, a self-test run, and `verify_regen.py`, applies the updates, and
then re-runs the guard so the result is reported rather than assumed.

It is a convenience, not an authority: it changes no oracle and no expectation, only
prose counts, and the guard it satisfies is the same one that catches a manual edit.
Two of its behaviours matter. Every figure is matched by a *search* pattern, so a
sentence that rots into an unrecognisable shape is reported as a warning rather than
leaving the stale number silently in place. And it refuses to sync over a genuine
`MODULE FAILED`, because writing counts over a broken suite would launder the breakage
into the plan; a run whose only failure is the count guard itself is exactly the state
it exists for.

## After a spec revision

`tests/test_oracle_quotes.py` requires every quoted normative passage to appear verbatim
in `docs/LANGUAGE_SPEC.md`, and it also resolves each requirement's `section` citation
against the specification's real heading structure. A revision that rewrites a sentence,
renumbers a section, or moves text between sections therefore breaks the requirements
that quoted it, and each one must be re-derived against the new text rather than
patched to whatever the implementation now prints. `requirements/ORACLE_REVIEW.md` must
gain a row per new requirement in the same change -- the independence record TCK.md
section 6.1 requires -- or the review-coverage guard names every missing one.
