#!/usr/bin/env python3
"""Sync the counts that `tck/tests/run_selftests.py` guards, from `validate` and a self-test run.

The plan/README count guard deliberately fails on any stale figure rather than letting prose drift
away from the artifacts. Recomputing those figures by hand each time a batch lands is error-prone,
and the guard already prints the authoritative values -- this tool just applies them, so the sync is
one command instead of twenty careful edits.

Design notes, because a count-syncing tool fails in exactly one interesting way: quietly.

* Every pattern is a *search* pattern, so a figure that rots into an unrecognisable sentence is
  reported as "no longer matched" instead of leaving the stale number in place. A silent no-op is
  the failure mode this tool must not have.
* Patterns are declared per file. A figure the plan restates has no business being sought in the
  tools README, and emitting a "no longer matched" note for a pattern that was never meant for that
  file would train the reader to ignore the notes.
* The self-test totals are read from a run whose only failure is the count guard itself, because
  that guard is what is stale; a genuine `MODULE FAILED` stops the sync, since writing counts over a
  broken suite would launder the breakage into the plan.
* The reference-adapter rows carry their own `compared` figure (the number of programs the subset
  adapter judges, not the corpus size), so those patterns are anchored to their row rather than to
  the token `compared=`.

Python-only, like the rest of the TCK gate.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAN = os.path.join(ROOT, "IMPLEMENTATION_PLAN.md")
TOOLS_README = os.path.join(ROOT, "tools", "README.md")


def validate_counts():
    out = subprocess.run([sys.executable, os.path.join(ROOT, "runner", "tck_cli.py"), "validate"],
                         capture_output=True, text=True, cwd=ROOT).stdout
    m = re.search(r"(\d+) requirements?, \d+ profiles?, (\d+) manifests", out)
    c = re.search(r"requirement coverage: (\d+) tested / (\d+) active", out)
    if not m or not c:
        raise SystemExit("could not parse validate output:\n" + out[-1500:])
    return int(m.group(1)), int(m.group(2)), int(c.group(1)), int(c.group(2))


def _id_spans(text):
    """Expand an ID list like `SOL-TCK-0001..0075, SOL-TCK-0499` into a sorted int list."""
    ids = []
    for m in re.finditer(r"SOL-TCK-(\d{4})(?:\.\.(\d{4}))?", text):
        start = int(m.group(1))
        end = int(m.group(2) or m.group(1))
        ids.extend(range(start, end + 1))
    return sorted(set(ids))


def _compress(ids):
    """Compress a sorted int list into [(first, last), ...] spans."""
    spans = []
    for i in ids:
        if spans and i == spans[-1][1] + 1:
            spans[-1][1] = i
        else:
            spans.append([i, i])
    return [tuple(s) for s in spans]


def _fmt_spans(spans, sep):
    """Format spans the way verify_regen names them: first span prefixed, singles bare."""
    parts = []
    for index, (first, last) in enumerate(spans):
        prefix = "SOL-TCK-" if index == 0 else ""
        if first == last:
            parts.append("%s%04d" % (prefix, first))
        else:
            parts.append("%s%04d..%04d" % (prefix, first, last))
    return sep.join(parts)


def provenance_counts():
    """The three-tier split, derived from verify_regen's own words -- never hardcoded.

    verify_regen states the self-contained count, the manifest-only ID list, and the
    unowned ID list. The self-contained *IDs* are the complement of those two lists in
    the corpus directory set, so the README's ID ranges stay exact when a batch lands
    IDs out of sequence (an in-place count bump would silently misname the ranges).
    """
    out = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "verify_regen.py")],
                         capture_output=True, text=True, cwd=ROOT).stdout
    m = re.search(r"(\d+)/(\d+) committed test directories", out)
    mo = re.search(r"(\d+) further directories have their manifest reproduced[^:]*: (\S[^\n]*)",
                   out)
    un = re.search(r"(\d+) committed test directories are not reproducible[^:]*: (\S[^\n]*)", out)
    if not m or not mo or not un:
        raise SystemExit("could not parse verify_regen output:\n" + out[-1500:])
    selfcont, total = int(m.group(1)), int(m.group(2))
    manifest_only, unowned = _id_spans(mo.group(2)), _id_spans(un.group(2))
    if int(mo.group(1)) != len(manifest_only) or int(un.group(1)) != len(unowned):
        raise SystemExit("verify_regen's ID lists disagree with its counts:\n" + out[-1500:])
    corpus = set()
    for entry in os.listdir(os.path.join(ROOT, "corpus")):
        base = os.path.join(ROOT, "corpus", entry)
        if not os.path.isdir(base):
            continue
        for name in os.listdir(base):
            d = re.fullmatch(r"SOL-TCK-(\d{4})", name)
            if d and os.path.isdir(os.path.join(base, name)):
                corpus.add(int(d.group(1)))
    claimed = set(manifest_only) | set(unowned)
    self_ids = sorted(corpus - claimed)
    if len(self_ids) != selfcont:
        raise SystemExit("self-contained ID set (%d) disagrees with verify_regen (%d)"
                         % (len(self_ids), selfcont))
    return {"self-contained": selfcont, "total": total,
            "manifest-only": len(manifest_only), "unowned": len(unowned),
            "self-contained-ids": _compress(self_ids),
            "manifest-only-ids": _compress(manifest_only),
            "unowned-ids": _compress(unowned)}


def selftest_counts():
    """The executed self-test totals, tolerating a run whose only failure is the count guard."""
    proc = subprocess.run([sys.executable, os.path.join(ROOT, "tests", "run_selftests.py")],
                          capture_output=True, text=True, cwd=ROOT)
    out = proc.stdout
    if "MODULE FAILED" in out:
        raise SystemExit("a self-test module genuinely failed; fix it before syncing counts:\n"
                         + out[-2500:] + proc.stderr[-1500:])
    quotes = re.search(r"oracle quotes: (\d+)/\d+ passed", out)
    if not quotes:
        raise SystemExit("could not determine the oracle-quote count:\n" + out[-2500:])
    total = re.search(r"ALL SELF-TESTS PASSED \((\d+) modules, (\d+) assertions\)", out)
    if total:
        return int(total.group(2)), int(quotes.group(1))
    total = re.search(r"self-test assertions, actually executed (\d+)", out)
    if total:
        return int(total.group(1)), int(quotes.group(1))
    raise SystemExit("could not determine the executed self-test totals:\n" + out[-2500:])


def plan_patterns(reqs, manifests, cov, active, selftotal, quotes, tiers):
    """The figures IMPLEMENTATION_PLAN.md restates. The reference-adapter refusal count is
    `manifests - compared`, where `compared` is the 16 programs the subset adapter judges."""
    refusal = manifests - 16
    return [
        (r"\*\*\d+ requirements\*\*, 1 profile, \*\*\d+ manifests\*\*; coverage \*\*\d+/\d+ active\*\*",
         "**%d requirements**, 1 profile, **%d manifests**; coverage **%d/%d active**"
         % (reqs, manifests, cov, active)),
        (r"(disagreements=0 inconclusive=0 )compared=\d+( unconstrained=\d+)",
         r"\1compared=%d\2" % manifests),
        (r"inconclusive=\d+ compared=16 unconstrained=(\d+)` over \d+ tests",
         "inconclusive=%d compared=16 unconstrained=\\1` over %d tests" % (refusal, manifests)),
        (r"refuses \d+ programs it does not implement, reported as an absence of observation "
         r"rather than \d+ fabricated",
         "refuses %d programs it does not implement, reported as an absence of observation "
         "rather than %d fabricated" % (refusal, refusal)),
        (r"Today \d+ of the \d+ portable test directories",
         "Today %d of the %d portable test directories"
         % (tiers["self-contained"], tiers["total"])),
        (r"\*\*\d+ self-test assertions\*\*", "**%d self-test assertions**" % selftotal),
        (r"\d+/\d+ passed \(every quoted normative passage",
         "%d/%d passed (every quoted normative passage" % (quotes, quotes)),
        (r"`PASS=\d+ FAIL=0 NOT_RUN=0 INFRA=0`", "`PASS=%d FAIL=0 NOT_RUN=0 INFRA=0`" % manifests),
        (r"against the full \d+-test corpus", "against the full %d-test corpus" % manifests),
        (r"never \d+ language failures", "never %d language failures" % manifests),
        (r"`PASS=0 FAIL=0 INFRA=\d+`", "`PASS=0 FAIL=0 INFRA=%d`" % manifests),
        (r"`NOT_RUN=\d+`", "`NOT_RUN=%d`" % manifests),
        (r"The inventory now holds \d+ requirements with \d+ portable tests",
         "The inventory now holds %d requirements with %d portable tests" % (reqs, manifests)),
        # NB: no pattern here matches `(\d+/\d+ active)` in backticks. The plan once carried a
        # coverage pair in that shape; the revision that re-baselined the specification rewrote the
        # sentence, and the pattern was left behind -- it matched nothing and warned on every run while
        # the figure it claimed to own was already written by the headline pattern above. A search
        # pattern for text that no longer exists is worse than no pattern, because the warning it emits
        # is permanently true and so trains the reader to ignore the warning that would mean a real
        # rot. The plan states its coverage pairs in exactly three shapes, and the three patterns here
        # for `**N/M active**`, ``N/M` therefore means`, and `Coverage `N/M` is full` are each
        # independently mirrored by a guard in run_selftests.py, so removing the dead one loses no
        # coverage: a stale figure in any of those three sentences still fails the self-test.
        (r"Reported coverage `\d+/\d+` therefore means",
         "Reported coverage `%d/%d` therefore means" % (cov, active)),
        (r"\*\*\d+ requirements, still not an enumeration\*\*",
         "**%d requirements, still not an enumeration**" % reqs),
        (r"rules than \d+ entries", "rules than %d entries" % reqs),
        (r"reports `16 PASS / \d+ FAIL` over the \d+-test corpus",
         "reports `16 PASS / %d FAIL` over the %d-test corpus" % (refusal, manifests)),
        (r"other \d+\. Those \d+ refusals are precisely the \d+ `FAIL`s",
         "other %d. Those %d refusals are precisely the %d `FAIL`s" % (refusal, refusal, refusal)),
        (r"Coverage `\d+/\d+` is full", "Coverage `%d/%d` is full" % (cov, active)),
    ]


def tools_readme_patterns(tiers):
    """The three-tier provenance split table and the prose that restates the unowned count.
    The ID ranges are the exact complements computed from verify_regen's own lists, so a
    batch landing IDs outside the contiguous run (or a hand-authored unowned test at a
    fresh ID) keeps the table truthful instead of silently misnaming the ranges."""
    selfc = _fmt_spans(tiers["self-contained-ids"], " plus ")
    unowned = _fmt_spans(tiers["unowned-ids"], ", ")
    return [
        (r"\| self-contained \| \d+ \| `[^|`]*`[^|]*\|",
         "| self-contained | %d | `%s` |" % (tiers["self-contained"], selfc)),
        (r"\| manifest-only \| \d+ \| `[^|`]*`[^|]*\|",
         "| manifest-only | %d | `%s` |" % (tiers["manifest-only"],
                                            _fmt_spans(tiers["manifest-only-ids"], ", "))),
        (r"\| unowned \| \d+ \| `[^|`]*`[^|]*\|",
         "| unowned | %d | `%s` |" % (tiers["unowned"], unowned)),
        (r"those \d+ directories are named", "those %d directories are named" % tiers["unowned"]),
    ]


def apply(path, mapping):
    text = open(path, encoding="utf-8").read()
    stale = []
    for pattern, replacement in mapping:
        text, n = re.subn(pattern, replacement, text)
        if n == 0:
            stale.append(pattern)
    open(path, "w", encoding="utf-8").write(text)
    return stale


def main():
    reqs, manifests, cov, active = validate_counts()
    selftotal, quotes = selftest_counts()
    tiers = provenance_counts()

    stale = apply(PLAN, plan_patterns(reqs, manifests, cov, active, selftotal, quotes, tiers))
    stale += apply(TOOLS_README, tools_readme_patterns(tiers))
    for pattern in stale:
        print("WARNING: a stated figure no longer matches /%s/ -- the sentence rotted and the "
              "count guard may have stopped checking it" % pattern)

    # The guard is the authority: re-run it and report honestly rather than trusting the edits.
    check = subprocess.run([sys.executable, os.path.join(ROOT, "tests", "run_selftests.py")],
                           capture_output=True, text=True, cwd=ROOT)
    if "ALL SELF-TESTS PASSED" not in check.stdout:
        tail = [ln for ln in check.stdout.splitlines() if "plan states" in ln or "FAILED" in ln]
        print("counts are still stale:")
        for ln in tail[:20]:
            print("  " + ln.strip())
        return 1
    print("synced: %d requirements, %d manifests, coverage %d/%d, %d self-test assertions, "
          "%d oracle quotes, %d self-contained directories"
          % (reqs, manifests, cov, active, selftotal, quotes, tiers["self-contained"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
