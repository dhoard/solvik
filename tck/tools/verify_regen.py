#!/usr/bin/env python3
"""Provenance check for the generated TCK surface.

Why this exists
---------------
The portable corpus and the requirement inventory are the *artifacts of record*,
but most of them were produced by generator scripts that for a long time lived
outside the repository. This tool regenerates the part of the surface that the
committed generators genuinely own -- into a throwaway repository built from an
empty corpus and an empty inventory -- and requires the result to equal the
committed files byte for byte.

It is deliberately *not* a blanket claim that the whole corpus is reproducible.
An earlier version of this check copied the committed corpus into the working
directory before running the tools. Because every generator is idempotent
(skip-if-exists, or verify-equals on rerun), that copy made the comparison
trivially empty: the check reported success over a test file that had just been
deliberately corrupted. Building from empty is what makes the comparison mean
something, and reporting the unowned remainder is what keeps it from
overclaiming.

Two relationships a tool can have to a test directory are distinguished, because
collapsing them was how the false confidence arose:

  * self-contained -- the tool writes both `main.sol` and the manifest, so
    running it from an empty corpus reproduces the whole directory. These are in
    SELF_CONTAINED below and asserted byte-identical.
  * manifest-only  -- gen11.py writes manifests over `main.sol` sources that
    were hand-authored and are *read*, not produced. Its 16 test directories are
    therefore not reproducible *as programs* by anything in the repository, only
    as manifests; they are reported, not counted as owned.
  * unowned        -- committed test directories no committed tool writes at
    all (the earliest batches, whose per-batch steps were not preserved). These
    are *listed* on every run rather than silently tolerated.

The self-contained count is pinned by a recorded floor so the reproducible
surface cannot shrink without a failing check, and the unowned count is printed
every run so the gap stays visible instead of drifting upward unnoticed.

Python-only, like the rest of the TCK gate: no Solvik, GraalVM, Java, or Maven.
"""

import hashlib
import json
import re
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SPEC_VERSION = "2026.09-draft"

# Every generator that writes both sources and manifests. Run order is the slice
# order; each is idempotent against an already-populated inventory, so a fresh
# empty shadow plus this order reproduces the self-contained surface exactly.
SELF_CONTAINED = [
    "gen20.py", "gen14.py", "gen22.py", "gen21.py", "gen12.py",
    "gen1.py", "gen3a.py", "gen3b.py", "gen3c.py", "gen16.py", "gen23.py",
    "gen24.py", "gen25.py", "gen26.py", "gen27.py", "gen28.py", "gen29.py",
    "gen30.py", "gen31.py", "gen32.py", "gen33.py", "gen34.py",
]

# gen11 writes manifests over hand-authored sources it reads; it is exercised
# here only to confirm it still reproduces the committed *manifests* for the
# directories whose sources the corpus already provides.
MANIFEST_ONLY = ["gen11.py"]

# Self-contained test-directory count, recorded as a floor. It rises when a new
# batch commits its generator and falls if a generator stops producing a test it
# used to; either drop is a reviewed decision made in the same change, never a
# silent one.
SELF_CONTAINED_FLOOR = 325

# The earliest batches have no committed generator at all, so their test
# directories cannot be reproduced as programs by anything in the repository.
# This ceiling states how many may be unowned; lowering it (by committing a
# generator for an early batch) is always allowed, and raising it requires
# changing the number here on purpose. A corpus that grows only by batches whose
# generators are committed keeps this satisfied automatically -- which is the
# whole point: new work cannot quietly widen the unprovenance hole.
UNOWNED_CEILING = 75

# Test directories whose manifests a committed tool reproduces over hand-authored
# sources (gen11). They are neither self-contained nor wholly unowned, and are
# reported in their own right.
MANIFEST_ONLY_FLOOR = 16


def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def file_map(base):
    out = {}
    for dirpath, _dirs, files in os.walk(base):
        for name in files:
            full = os.path.join(dirpath, name)
            out[os.path.relpath(full, base)] = digest(full)
    return out


def test_ids(corpus_dir):
    version_dir = os.path.join(corpus_dir, SPEC_VERSION)
    if not os.path.isdir(version_dir):
        return set()
    return {n for n in os.listdir(version_dir)
            if n.startswith("SOL-TCK-")
            and os.path.isdir(os.path.join(version_dir, n))}


def contiguous(ids):
    nums = sorted(int(i.rsplit("-", 1)[1]) for i in ids)
    if not nums:
        return "none"
    parts, lo, prev = [], nums[0], nums[0]
    for n in nums[1:]:
        if n == prev + 1:
            prev = n
            continue
        parts.append((lo, prev))
        lo = prev = n
    parts.append((lo, prev))
    return ", ".join("SOL-TCK-%04d..%04d" % p if p[0] != p[1]
                     else "SOL-TCK-%04d" % p[0] for p in parts)


def build_shadow():
    """A repository-shaped directory holding only what the tools read.

    The corpus starts empty and the inventory/profile start as valid-but-empty
    documents, so every artifact the tools emit here is genuinely regenerated
    rather than echoed back from the committed copy. For the manifest-only tool
    the hand-authored sources are provided, since that tool's contract is to
    read them; that is why its directories are not counted as reproducible.
    """
    shadow = tempfile.mkdtemp(prefix="solvik-regen-")
    os.makedirs(os.path.join(shadow, "docs"))
    shutil.copy(os.path.join(ROOT, "docs", "LANGUAGE_SPEC.md"),
                os.path.join(shadow, "docs", "LANGUAGE_SPEC.md"))
    for rel in ("tck/corpus", "tck/requirements", "tck/profiles", "tck/tools"):
        os.makedirs(os.path.join(shadow, rel), exist_ok=True)
    with open(os.path.join(shadow, "tck/requirements/requirements.json"), "w",
              encoding="utf-8") as fh:
        json.dump({"schemaVersion": 1, "specVersion": SPEC_VERSION,
                   "requirements": []}, fh, indent=2)
        fh.write("\n")
    with open(os.path.join(shadow, "tck/profiles/full-language.profile.json"),
              "w", encoding="utf-8") as fh:
        json.dump({"schemaVersion": 1, "specVersion": SPEC_VERSION,
                   "name": "full-language", "kind": "full",
                   "requirements": []}, fh, indent=2)
        fh.write("\n")
    for gen in SELF_CONTAINED + MANIFEST_ONLY:
        shutil.copy(os.path.join(HERE, gen),
                    os.path.join(shadow, "tck/tools", gen))
    return shadow


def seed_manifest_only_inputs(shadow, self_contained_ids):
    """Provide the hand-authored sources the manifest-only tool reads.

    Only the sources for the manifest-only tool's own test directories are
    copied, and only after the self-contained tools have run, so this seeding can
    never hand a self-contained tool an existing directory to skip over -- which
    is precisely the vacuity that made the first version of this check useless.
    """
    real_corpus = os.path.join(ROOT, "tck/corpus", SPEC_VERSION)
    shadow_corpus = os.path.join(shadow, "tck/corpus", SPEC_VERSION)
    owned_by_manifest_only = set()
    for gen in MANIFEST_ONLY:
        src = open(os.path.join(HERE, gen), encoding="utf-8").read()
        import re
        owned_by_manifest_only |= set(re.findall(r'"(SOL-TCK-\d{4})"', src))
    for tid in sorted(owned_by_manifest_only):
        if tid in self_contained_ids:
            continue
        src_dir = os.path.join(real_corpus, tid)
        src_sol = os.path.join(src_dir, "main.sol")
        if not os.path.exists(src_sol):
            continue
        dst = os.path.join(shadow_corpus, tid)
        os.makedirs(dst, exist_ok=True)
        shutil.copy(src_sol, os.path.join(dst, "main.sol"))
        # a manifest-only tool may also read other files that already exist
        for extra in os.listdir(src_dir):
            if extra != "main.sol" and not extra.endswith(".manifest.json"):
                shutil.copy(os.path.join(src_dir, extra),
                            os.path.join(dst, extra))
    return owned_by_manifest_only


def run(shadow, gens):
    for gen in gens:
        proc = subprocess.run([sys.executable, os.path.join("tck/tools", gen)],
                              cwd=shadow, capture_output=True, text=True)
        if proc.returncode != 0:
            raise SystemExit(
                "verify-regeneration: FAIL: %s exited %d in the shadow repo\n%s"
                % (gen, proc.returncode, (proc.stderr or proc.stdout)[-2000:]))


def compare(shadow, sc, manifest_only_ids):
    """Assert the self-contained surface is byte-identical, and that the
    manifest-only tool still reproduces the committed manifests it owns.

    `sc` is the id set captured *before* the manifest-only tool ran. Recomputing
    it afterwards would fold gen11's seeded directories into the byte
    comparison, where `main.sol` was copied rather than generated, and report a
    spurious "not produced by the generator" for every one of them.
    """
    problems = []
    shadow_corpus = os.path.join(shadow, "tck/corpus")
    real_corpus = os.path.join(ROOT, "tck/corpus")
    committed = test_ids(real_corpus)
    self_contained = sc & committed

    extra = sc - committed
    if extra:
        # An owned directory absent from the committed tree would slip through a
        # file-by-file comparison limited to directories present on both sides.
        problems.append("generators produce test directories absent from the "
                        "corpus: %s" % contiguous(extra))

    for tid in sorted(self_contained):
        s_map = file_map(os.path.join(shadow_corpus, SPEC_VERSION, tid))
        c_map = file_map(os.path.join(real_corpus, SPEC_VERSION, tid))
        if s_map == c_map:
            continue
        differ = sorted(k for k in set(s_map) & set(c_map) if s_map[k] != c_map[k])
        only_real = sorted(set(c_map) - set(s_map))
        only_shadow = sorted(set(s_map) - set(c_map))
        problems.append("%s differs from its generator: %d file(s) differ%s%s"
                        % (tid, len(differ),
                           "; not produced by the generator: %s" % ", ".join(only_real)
                           if only_real else "",
                           "; produced but not committed: %s" % ", ".join(only_shadow)
                           if only_shadow else ""))

    s_inv = {r["id"]: r for r in json.load(
        open(os.path.join(shadow, "tck/requirements/requirements.json"),
             encoding="utf-8"))["requirements"]}
    c_inv = {r["id"]: r for r in json.load(
        open(os.path.join(ROOT, "tck/requirements/requirements.json"),
             encoding="utf-8"))["requirements"]}
    for rid, rec in sorted(s_inv.items()):
        if rid not in c_inv:
            problems.append("a generator writes %s, which the committed inventory "
                            "lacks" % rid)
        elif c_inv[rid] != rec:
            problems.append("committed %s differs from its generator's record" % rid)

    s_prof = set(json.load(open(os.path.join(
        shadow, "tck/profiles/full-language.profile.json"),
        encoding="utf-8"))["requirements"])
    c_prof = set(json.load(open(os.path.join(
        ROOT, "tck/profiles/full-language.profile.json"),
        encoding="utf-8"))["requirements"])
    if not s_prof <= c_prof:
        problems.append("generators register %d requirement(s) absent from the "
                        "committed profile" % len(s_prof - c_prof))

    # The manifest-only tool: only the manifest file is comparable, because the
    # source it reads was supplied rather than produced.
    reproduced_manifests = 0
    for tid in sorted(manifest_only_ids & committed):
        man = "%s.manifest.json" % tid
        sp = os.path.join(shadow_corpus, SPEC_VERSION, tid, man)
        cp_ = os.path.join(real_corpus, SPEC_VERSION, tid, man)
        if not os.path.exists(sp):
            problems.append("%s: the manifest-only tool produced no manifest"
                            % tid)
            continue
        if not os.path.exists(cp_):
            problems.append("%s: manifest-only tool produced a manifest the "
                            "corpus lacks" % tid)
            continue
        if digest(sp) != digest(cp_):
            problems.append("%s: manifest differs from the committed one" % tid)
        else:
            reproduced_manifests += 1

    dangling = set()
    for tid in sorted(self_contained):
        man = os.path.join(shadow_corpus, SPEC_VERSION, tid,
                           "%s.manifest.json" % tid)
        if os.path.exists(man):
            refs = json.load(open(man, encoding="utf-8")).get("requirements", [])
            dangling |= {r for r in refs if r not in c_inv}
    if dangling:
        problems.append("regenerated tests reference requirement(s) absent from "
                        "the committed inventory: %s" % ", ".join(sorted(dangling)))
    return self_contained, committed, reproduced_manifests, problems


def main():
    shadow = build_shadow()
    try:
        run(shadow, SELF_CONTAINED)
        self_contained_ids = test_ids(os.path.join(shadow, "tck/corpus"))
        manifest_only_ids = seed_manifest_only_inputs(shadow, self_contained_ids)
        run(shadow, MANIFEST_ONLY)
        sc, committed, reproduced_manifests, problems = compare(
            shadow, self_contained_ids, manifest_only_ids)
    finally:
        shutil.rmtree(shadow, ignore_errors=True)

    total = len(committed)
    manifest_only = (manifest_only_ids & committed) - sc
    unowned = committed - sc - manifest_only
    print("verify-regeneration: %d/%d committed test directories reproduce "
          "byte-for-byte from the self-contained generators in tck/tools"
          % (len(sc), total))
    if manifest_only:
        print("verify-regeneration: note -- %d further directories have their "
              "manifest reproduced by a manifest-only tool over hand-authored "
              "sources (%d of %d), so their programs are not reproducible by "
              "anything in the repository: %s"
              % (len(manifest_only), reproduced_manifests, len(manifest_only),
                 contiguous(manifest_only)))
    if unowned:
        print("verify-regeneration: note -- %d committed test director%s not "
              "reproducible as programs by any committed generator: %s"
              % (len(unowned),
                 "y is" if len(unowned) == 1 else "ies are",
                 contiguous(unowned)))
    if len(sc) < SELF_CONTAINED_FLOOR:
        problems.insert(0, "the reproducible surface shrank to %d test "
                        "directories; the recorded floor is %d"
                        % (len(sc), SELF_CONTAINED_FLOOR))
    if reproduced_manifests < MANIFEST_ONLY_FLOOR:
        problems.insert(0, "the manifest-only tool reproduced only %d of %d "
                        "expected manifests" % (reproduced_manifests,
                                                MANIFEST_ONLY_FLOOR))
    if len(unowned) > UNOWNED_CEILING:
        problems.insert(0, "%d test directories are unowned, above the recorded "
                        "ceiling of %d: the corpus grew by a batch whose "
                        "generator was not committed" % (len(unowned),
                                                         UNOWNED_CEILING))
    if problems:
        for p in problems:
            print("verify-regeneration: FAIL: %s" % p, file=sys.stderr)
        return 1
    print("verify-regeneration: OK -- the reproducible surface matches its "
          "generators byte for byte, and the inventory and profile records they "
          "write match the committed ones")
    return 0


if __name__ == "__main__":
    sys.exit(main())
