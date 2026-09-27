#!/usr/bin/env python3
"""Aggregate runner for all TCK runner self-tests.

Executes every self-test module in a fresh subprocess and reports a single
exit status. These self-tests require only Python: no Solvik, Java, GraalVM,
Maven, or built distribution is invoked (TCK.md acceptance criteria 2 and 14).

After the modules run, the recorded per-module and aggregate assertion counts in
`IMPLEMENTATION_PLAN.md` are compared against the counts actually executed, so a
documented number that no longer matches reality fails the build instead of
quietly overstating how much of the TCK is verified.

Usage:  python3 tck/tests/run_selftests.py
"""

import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TCK_ROOT = os.path.dirname(HERE)

MODULES = [
    "test_strict_json_and_schema.py",
    "test_manifest_and_inventory.py",
    "test_protocol.py",
    "test_preflight_and_determinism.py",
    "test_integration_fake.py",
    "test_differential.py",
    "test_solvik_adapter.py",
    "test_reference_adapter.py",
    "test_oracle_quotes.py",
]

# The document whose tables quote these counts.
PLAN = os.path.join(TCK_ROOT, "IMPLEMENTATION_PLAN.md")

COUNT_RE = r"(\d+)/(\d+) passed"
TOTAL_RE = r"\*\*(\d+) self-test assertions\*\*"

# The `tck_cli.py validate` summary line quoted in the plan, and the pure-Python
# validator that produces it. Counting requirements and manifests is schema work on
# checked-in files: no Solvik, Java, GraalVM, or built distribution is involved, so
# checking these numbers stays inside the Python-only gate.
VALIDATE_RE = (r"OK: \*\*(\d+) requirements\*\*, (\d+) profile, "
               r"\*\*(\d+) manifests\*\*; coverage \*\*(\d+)/(\d+) active\*\*")
TCK_CLI = os.path.join(TCK_ROOT, "runner", "tck_cli.py")


def run_module(module):
    """Run one module, echoing its output; return its assertion counts.

    Returns (returncode, passed, total) where passed/total are None when the
    module did not print a recognizable count line.
    """
    path = os.path.join(HERE, module)
    print("=" * 70)
    print("== %s" % module)
    print("=" * 70)
    proc = subprocess.run([sys.executable, path], capture_output=True, text=True)
    sys.stdout.write(proc.stdout)
    sys.stderr.write(proc.stderr)
    if proc.returncode != 0:
        print("MODULE FAILED: %s (exit %s)" % (module, proc.returncode))
    counts = re.findall(COUNT_RE, proc.stdout)
    if not counts:
        return proc.returncode, None, None
    # A module may print its tally more than once (e.g. a summary after a
    # per-section line); the last occurrence is its final total.
    passed, total = counts[-1]
    return proc.returncode, int(passed), int(total)


def documented_plan_counts(plan_text):
    """Map module name -> (passed, total) as stated in the plan tables."""
    out = {}
    for line in plan_text.split("\n"):
        name = next((m for m in MODULES if m in line), None)
        if name is None:
            continue
        m = re.search(COUNT_RE, line)
        if m:
            out[name] = (int(m.group(1)), int(m.group(2)))
    return out


def documented_total(plan_text):
    m = re.search(TOTAL_RE, plan_text)
    return int(m.group(1)) if m else None


def check_documented_counts(results):
    """Fail if IMPLEMENTATION_PLAN.md states counts that were not executed.

    Count claims in documentation are otherwise unfalsifiable: they drift whenever a
    module grows, and a stale number makes the suite look stronger or weaker than it
    is. Here the driver already observes the true counts, so the documents are held
    to them.
    """
    problems = []
    try:
        plan_text = open(PLAN, encoding="utf-8").read()
    except OSError as exc:
        return ["cannot read %s: %s" % (PLAN, exc)]

    stated = documented_plan_counts(plan_text)
    for module, (_rc, passed, total) in sorted(results.items()):
        if module not in stated:
            # Not every module has to be tabulated; only a *stated* number can be wrong.
            continue
        if passed is None:
            problems.append("%s printed no assertion count but %s states one"
                            % (module, os.path.basename(PLAN)))
            continue
        if stated[module] != (passed, total):
            problems.append("%s: plan states %d/%d, actually executed %d/%d"
                            % (module, stated[module][0], stated[module][1], passed, total))

    stated_total = documented_total(plan_text)
    if stated_total is not None:
        executed = sum(t for _rc, _p, t in results.values() if t is not None)
        if stated_total != executed:
            problems.append("plan states %d self-test assertions, actually executed %d"
                            % (stated_total, executed))

    # Inventory and corpus counts stated in the plan must match what `validate` reports.
    m = re.search(VALIDATE_RE, plan_text)
    if m:
        rc, out = _run_validator()
        actual = re.search(r"(\d+) requirements, (\d+) profiles, (\d+) manifests", out)
        cover = re.search(r"coverage: (\d+) tested / (\d+) active", out)
        if rc != 0 or not actual or not cover:
            problems.append("`tck_cli.py validate` failed (exit %s); cannot verify the "
                            "counts the plan states" % rc)
        else:
            want = (int(m.group(1)), int(m.group(2)), int(m.group(3)),
                    int(m.group(4)), int(m.group(5)))
            got = (int(actual.group(1)), int(actual.group(2)), int(actual.group(3)),
                   int(cover.group(1)), int(cover.group(2)))
            if want[1] != got[1]:
                problems.append("plan states %d profile(s), validate reports %d"
                                % (want[1], got[1]))
            if want[0] != got[0]:
                problems.append("plan states %d requirements, validate reports %d"
                                % (want[0], got[0]))
            if want[2] != got[2]:
                problems.append("plan states %d manifests, validate reports %d"
                                % (want[2], got[2]))
            if want[3:] != got[3:]:
                problems.append("plan states coverage %d/%d active, validate reports "
                                "%d/%d active" % (want[3], want[4], got[3], got[4]))
    return problems


def _run_validator():
    proc = subprocess.run([sys.executable, TCK_CLI, "validate"],
                          capture_output=True, text=True)
    return proc.returncode, proc.stdout + proc.stderr


def main():
    total_fail = 0
    results = {}
    for module in MODULES:
        rc, passed, total = run_module(module)
        results[module] = (rc, passed, total)
        if rc != 0:
            total_fail += 1

    print("=" * 70)
    drift = check_documented_counts(results)
    if drift:
        print("DOCUMENTED SELF-TEST COUNTS DO NOT MATCH WHAT WAS EXECUTED:")
        for d in drift:
            print("  - %s" % d)
        print("Update the counts in tck/IMPLEMENTATION_PLAN.md to the values above.")
        total_fail += 1

    print("=" * 70)
    if total_fail:
        print("SELF-TESTS FAILED: %d failure(s)" % total_fail)
        return 1
    executed = sum(t for _rc, _p, t in results.values() if t is not None)
    print("ALL SELF-TESTS PASSED (%d modules, %d assertions)" % (len(MODULES), executed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
