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
| self-contained | 325 | `SOL-TCK-0092..0416` | the tool writes `main.sol` **and** the manifest; running it from an empty corpus reproduces the whole directory byte-for-byte |
| manifest-only | 16 | `SOL-TCK-0076..0091` | `gen11.py` writes manifests over `main.sol` sources that were **hand-authored and read, not produced**; the programs are not reproducible by anything here |
| unowned | 75 | `SOL-TCK-0001..0075` | no committed tool writes these at all — the earliest batches, whose per-batch steps were not preserved as tools |

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
it — which is precisely why those 75 directories are named on every run instead of
being counted as covered.
