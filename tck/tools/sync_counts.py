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


def provenance_counts():
    out = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "verify_regen.py")],
                         capture_output=True, text=True, cwd=ROOT).stdout
    m = re.search(r"(\d+)/(\d+) committed test directories", out)
    if not m:
        raise SystemExit("could not parse verify_regen output:\n" + out[-1500:])
    return int(m.group(1)), int(m.group(2))


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


def plan_patterns(reqs, manifests, cov, active, selftotal, quotes, selfcont, total_dirs):
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
         "Today %d of the %d portable test directories" % (selfcont, total_dirs)),
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
        (r"\(`\d+/\d+ active`\)", "(%d/%d active)" % (cov, active)),
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


def tools_readme_patterns(selfcont):
    """The three-tier provenance split. The self-contained range is derived, not guessed: unowned
    and manifest-only occupy SOL-TCK-0001..0091, so self-contained ends where the corpus ends."""
    unowned, manifest_only = 75, 16
    last = unowned + manifest_only + selfcont
    return [
        (r"\| self-contained \| \d+ \| `SOL-TCK-\d+\.\.\d+` \|",
         "| self-contained | %d | `SOL-TCK-0092..%04d` |" % (selfcont, last)),
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
    selfcont, total_dirs = provenance_counts()

    stale = apply(PLAN, plan_patterns(reqs, manifests, cov, active, selftotal, quotes,
                                      selfcont, total_dirs))
    stale += apply(TOOLS_README, tools_readme_patterns(selfcont))
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
          % (reqs, manifests, cov, active, selftotal, quotes, selfcont))
    return 0


if __name__ == "__main__":
    sys.exit(main())
