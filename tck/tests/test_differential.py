#!/usr/bin/env python3
"""Differential-comparator self-tests (TCK.md section 15).

Differential mode has no oracle to lean on, so its only guarantees are structural: the
axes it reports, the axes it refuses to report, and the exit code it derives. Every
condition here is constructed directly, because the two conditions that matter most -- an
adapter that declines to run a program, and two implementations that differ only in
non-normative wording -- are exactly the ones a real end-to-end run produces as noise and
that a fake-adapter suite would never isolate.

Each guard is tested in both directions: once showing it fires, and once showing the
opposite classification does *not* fire. A guard tested only in the firing direction is
indistinguishable from a guard that always fires.

Only Python; no Solvik/Java/GraalVM/Maven.
"""

import base64
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "runner"))

from tck_runner import differential as D  # noqa: E402

RESULTS = []


def check(label, cond):
    RESULTS.append((label, bool(cond)))
    if not cond:
        print("FAIL:", label)


def _raises(fn):
    """True only when fn raises -- used for fail-safe guards that must refuse."""
    try:
        fn()
    except Exception:  # noqa: BLE001
        return True
    return False


B64 = lambda s: base64.b64encode(s.encode() if isinstance(s, str) else s).decode()


def accepted(stdout=b"", stderr=b"", exit0=0):
    """A well-formed 'compiled and ran' observation."""
    return {"compileStatus": "COMPILE_ACCEPTED", "executeStatus": "NORMAL_EXIT",
            "stdoutBase64": B64(stdout), "stderrBase64": B64(stderr),
            "languageExit": exit0, "runtimeCategory": None,
            "status": "PASS", "phases": {"compile": "COMPILE_ACCEPTED"},
            "diagnostics": [], "infrastructureEvents": []}


def rejected(code=None, family=None, start=None, end=None):
    diag = [] if code is None else [{"family": family, "code": code,
                                     "startByteOffset": start, "endByteOffset": end}]
    return {"compileStatus": "COMPILE_REJECTED", "executeStatus": None,
            "stdoutBase64": "", "stderrBase64": "", "languageExit": None,
            "runtimeCategory": None, "status": "PASS", "diagnostics": diag,
            "infrastructureEvents": []}


def accepted_only():
    """A compile-only adapter's acceptance: a position on legality and nothing else.

    The program was accepted and never executed, so `executeStatus` is null and there are no
    output digests. This is the shape a `compile-only` partner produces, and it is the shape
    that once made a legality disagreement disappear: "no language result" was read as "no
    observation", so a side that had just certified a program as legal contributed nothing to
    the comparison.
    """
    return {"compileStatus": "COMPILE_ACCEPTED", "executeStatus": None,
            "stdoutBase64": "", "stderrBase64": "", "languageExit": None,
            "runtimeCategory": None, "status": "NOT_RUN",
            "phases": {"compile": "COMPILE_ACCEPTED"},
            "diagnostics": [], "infrastructureEvents": []}


def crashed(code=None, family=None):
    """A side whose process reported a compile status and then died.

    The recorded compile status is well-formed, which is the hazard: if a crash counted as a
    position on legality, a dead adapter would be reported as having *judged* the program and
    would disagree with every implementation that answered normally.
    """
    obs = accepted_only()
    obs["infrastructureEvents"] = ["ADAPTER_PROCESS_CRASH"]
    return obs


def refused():
    """An honest incomplete adapter: it reports its own inability, not a verdict.

    The judged `status` is FAIL, matching what a genuine non-conformance produces. The
    comparator must classify on the raw protocol status instead, or every refused test
    becomes a fabricated disagreement.
    """
    return {"compileStatus": "IMPLEMENTATION_FAILURE", "executeStatus": None,
            "stdoutBase64": "", "stderrBase64": "", "languageExit": None,
            "runtimeCategory": None, "status": "FAIL",
            "phases": {"compile": "IMPLEMENTATION_FAILURE"},
            "diagnostics": [], "infrastructureEvents": []}


SUCCESS = {"outcome": "SUCCESS",
           "expectation": {"languageExit": 0, "stdoutBase64": B64("x")}}
RUNTIME = {"outcome": "RUNTIME_ERROR", "expectation": {"runtimeCategory": "NULL_DEREFERENCE"}}
COMPILE_ERR = {"outcome": "COMPILE_ERROR",
               "expectation": {"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-001"}}}


def cmp(left, right, manifest=SUCCESS):
    return D.compare_observations("T", "L", left, "R", right, manifest)


def axes_of(left, right, manifest=SUCCESS):
    return cmp(left, right, manifest)["axes"]


# --------------------------------------------------------------------------
# 1. Refusal is an absence of observation, not a disagreement.
# --------------------------------------------------------------------------
r = cmp(accepted(b"x"), refused(), SUCCESS)
check("refusal vs verdict is inconclusive", r["inconclusive"])
check("refusal vs verdict reports no disagreement", r["axes"] == [])

r2 = cmp(refused(), accepted(b"x"), SUCCESS)
check("refusal is inconclusive on either side", r2["inconclusive"] and r2["axes"] == [])

r3 = cmp(refused(), refused(), SUCCESS)
check("mutual refusal is inconclusive, not agreement", r3["inconclusive"] and r3["axes"] == [])

# A *rejected program* is a complete verdict, not a refusal: two different rejections must
# still be comparable, or the guard above would hide real divergence.
r4 = cmp(rejected("TYPE", "SOLV-TYPE-001"), rejected("TYPE", "SOLV-TYPE-003"), COMPILE_ERR)
check("two rejections are comparable, not inconclusive", not r4["inconclusive"])
check("two rejections with different codes disagree", r4["axes"] == ["diagnostics"])


# 1b. A compile-only *acceptance* is a position on legality, and opposing positions on it
#     are the most fundamental disagreement available. This is the hole that made a legality
#     disagreement read as a clean run: the compile-only side was classified as having
#     produced nothing, so the pair was `inconclusive` with no axes and exit 0.
r5 = cmp(accepted_only(), rejected("TYPE", "SOLV-TYPE-001"), SUCCESS)
check("compile-only acceptance vs rejection is a disagreement",
      r5["axes"] == ["compileAcceptance"])
check("compile-only acceptance vs rejection is not inconclusive",
      not r5["inconclusive"])

r6 = cmp(rejected("TYPE", "SOLV-TYPE-001"), accepted_only(), SUCCESS)
check("the same disagreement is found on either side",
      r6["axes"] == ["compileAcceptance"] and not r6["inconclusive"])

# Falsifier for the fix: a compile-only partner that *agrees* the program is legal must not
# manufacture a divergence merely because it never ran the program.
r7 = cmp(accepted_only(), accepted(b"x"), SUCCESS)
check("compile-only agreement is comparable, not inconclusive", not r7["inconclusive"])
check("compile-only agreement reports no axes", r7["axes"] == [])
check("compile-only agreement suppresses post-compile axes", r7["unconstrained"] == [])
# Marked, so a count of `compared` cannot silently mean "behavior was checked".
check("one compile-only side is reported as legality-only", r7["legalityOnly"])

# Two compile-only acceptances: both hold the same position, neither ran anything.
r8 = cmp(accepted_only(), accepted_only(), SUCCESS)
check("two compile-only acceptances agree", r8["axes"] == [] and not r8["inconclusive"])
check("two compile-only sides are reported as legality-only", r8["legalityOnly"])

# Falsifier: when both sides actually executed, the same comparison must NOT be marked
# legality-only, or the marker would say nothing about whether behavior was compared.
r8b = cmp(accepted(b"x"), accepted(b"x"), SUCCESS)
check("a fully-executed comparison is not legality-only", not r8b["legalityOnly"])
# Neither does a pair of rejections, where the rejection itself is the complete result.
r8c = cmp(rejected("TYPE", "SOLV-TYPE-001"), rejected("TYPE", "SOLV-TYPE-001"), COMPILE_ERR)
check("two rejections are not legality-only", not r8c["legalityOnly"])
# And an incomparable test carries the marker false: it was not compared at all.
check("an inconclusive test is not legality-only", not cmp(refused(), accepted(b"x"))["legalityOnly"])

# A crash is not a position on legality even though its recorded compile status is well-formed.
# Without this, the rule above would turn every dead adapter into an objector.
r9 = cmp(crashed(), accepted(b"x"), SUCCESS)
check("a crash holds no position on legality", r9["inconclusive"] and r9["axes"] == [])
r10 = cmp(crashed(), rejected("TYPE", "SOLV-TYPE-001"), COMPILE_ERR)
check("a crash vs a rejection is still inconclusive", r10["inconclusive"] and r10["axes"] == [])

# A refusal likewise holds no position, in either direction against a rejection.
r11 = cmp(refused(), rejected("TYPE", "SOLV-TYPE-001"), COMPILE_ERR)
check("a refusal vs a rejection is inconclusive", r11["inconclusive"] and r11["axes"] == [])

# --------------------------------------------------------------------------
# 2. Undeclared observables are reported separately, never as disagreements.
# --------------------------------------------------------------------------
no_stderr = {"outcome": "SUCCESS", "expectation": {"languageExit": 0}}
same_out = cmp(accepted(b"x", b""), accepted(b"x", b"GraalVM warning"), no_stderr)
check("undeclared stderr difference is not a disagreement", same_out["axes"] == [])
check("undeclared stderr difference is still reported",
      same_out["unconstrained"] == ["stderr"])
check("undeclared stderr difference is not inconclusive", not same_out["inconclusive"])

# Falsifier: the moment stderr is declared, the identical bytes must be a disagreement.
declares_stderr = {"outcome": "SUCCESS",
                   "expectation": {"languageExit": 0, "stderrBase64": B64("")}}
diff_out = cmp(accepted(b"x", b""), accepted(b"x", b"note"), declares_stderr)
check("declared stderr difference IS a disagreement", diff_out["axes"] == ["stderr"])

# stdout IS declared by SUCCESS, so a difference there must fire even when nothing else is.
check("declared stdout difference is a disagreement",
      axes_of(accepted(b"x"), accepted(b"y"), SUCCESS) == ["stdout"])

# languageExit: declared vs not.
check("declared exit difference is a disagreement",
      axes_of(accepted(b"x", b"", 0), accepted(b"x", b"", 1),
              {"outcome": "SUCCESS", "expectation": {"languageExit": 0}}) == ["languageExit"])
check("undeclared exit difference is unconstrained only",
      cmp(accepted(b"x", b"", 0), accepted(b"x", b"", 1),
          {"outcome": "SUCCESS", "expectation": {"stdoutBase64": B64("x")}})["unconstrained"]
      == ["languageExit"])


# --------------------------------------------------------------------------
# 3. executeStatus is normative through `outcome`, not through an expectation key.
# --------------------------------------------------------------------------
ok = accepted(b"x")
boom = {"compileStatus": "COMPILE_ACCEPTED", "executeStatus": "RUNTIME_FAILURE",
        "stdoutBase64": B64("x"), "stderrBase64": "", "languageExit": None,
        "runtimeCategory": "NULL_DEREFERENCE", "status": "PASS", "diagnostics": [],
        "infrastructureEvents": []}
ax = axes_of(ok, boom, SUCCESS)
check("normal-exit vs runtime-failure is a disagreement", "executeStatus" in ax)

# Falsifier: under a compile-only outcome, execution differences are how both sides were
# already wrong, so they must not be counted as a spec-level divergence.
ax2 = axes_of(ok, boom, COMPILE_ERR)
check("executeStatus is unconstrained under a compile-only outcome",
      "executeStatus" not in ax2)


# --------------------------------------------------------------------------
# 4. Compilation acceptance dominates; later axes are suppressed beneath it.
# --------------------------------------------------------------------------
ax = axes_of(accepted(b"x"), rejected("TYPE", "SOLV-TYPE-001"), SUCCESS)
check("acceptance disagreement is reported", ax == ["compileAcceptance"])

# Two identical programs -> no axes at all.
check("identical observations report no axes", axes_of(accepted(b"x"), accepted(b"x")) == [])


# --------------------------------------------------------------------------
# 5. Diagnostics compare only the structured projection, never wording.
# --------------------------------------------------------------------------
same_structured = rejected("TYPE", "SOLV-TYPE-001", 10, 20)
same_structured2 = rejected("TYPE", "SOLV-TYPE-001", 10, 20)
same_structured2["diagnostics"][0]["message"] = "phrased differently"
check("diagnostic wording is never normative",
      axes_of(same_structured, same_structured2, COMPILE_ERR) == [])

span = rejected("TYPE", "SOLV-TYPE-001", 10, 20)
nolocations = rejected("TYPE", "SOLV-TYPE-001")
# A byte span is only normative when the oracle declares one. COMPILE_ERR names family
# and code only, so a side that reports a span and a side that reports none agree on
# everything the oracle constrains; counting this as a disagreement would report two
# conforming implementations as diverging over a host-chosen detail. This is the same
# false-divergence class as unguarded stderr comparison, on the diagnostic axis, and it
# fired for real against the reference adapter before the projection existed.
check("an undeclared span difference is reported but not counted",
      axes_of(span, nolocations, COMPILE_ERR) == [] and
      cmp(span, nolocations, COMPILE_ERR)["unconstrained"] == ["diagnostics"])

# Falsifier: when the oracle *does* declare a location, the same difference is normative.
SPAN_ORACLE = {"outcome": "COMPILE_ERROR",
               "expectation": {"diagnostic": {"code": "SOLV-TYPE-001",
                                              "location": {"startByteOffset": 10,
                                                           "endByteOffset": 20}}}}
check("a declared span difference is a disagreement",
      axes_of(span, nolocations, SPAN_ORACLE) == ["diagnostics"])
check("matching declared spans agree", axes_of(span, span, SPAN_ORACLE) == [])

# family is a declared field of COMPILE_ERR, so a family-only difference is counted.
family_diff = rejected("SEM", "SOLV-TYPE-001", 10, 20)
check("a family difference is a disagreement",
      axes_of(span, family_diff, COMPILE_ERR) == ["diagnostics"])

# Under a non-COMPILE_ERROR oracle, both rejections are already non-conformances, so the
# difference in *how* they were wrong is reported but not counted.
r5 = cmp(span, nolocations, SUCCESS)
check("diagnostic difference under a success oracle is unconstrained",
      r5["axes"] == [] and r5["unconstrained"] == ["diagnostics"])

# Duplicate diagnostics in different order must compare equal (set semantics).
a = rejected(); a["diagnostics"] = [{"family": "TYPE", "code": "B", "startByteOffset": 1,
                                     "endByteOffset": 2},
                                    {"family": "RESOL", "code": "A", "startByteOffset": 3,
                                     "endByteOffset": 4}]
b = rejected(); b["diagnostics"] = list(reversed([dict(d) for d in a["diagnostics"]]))
check("diagnostic ordering is not observable", axes_of(a, b, COMPILE_ERR) == [])


# --------------------------------------------------------------------------
# 6. Normalization is applied before comparing, so it can create agreement.
# --------------------------------------------------------------------------
norm = {"outcome": "SUCCESS", "expectation": {"languageExit": 0},
        "normalization": [{"field": "stdout", "transform": "platform-line-separator"}]}
crlf, lf = accepted(b"a\r\nb"), accepted(b"a\nb")
if os.linesep == "\r\n":
    check("declared normalization can turn a difference into agreement",
          axes_of(crlf, lf, norm) == [])
else:
    check("declared normalization can turn a difference into agreement",
          axes_of(accepted(b"a\nb"), accepted(b"a\nb"), norm) == [])
# Falsifier: with stdout normative but no normalization declared, the same bytes differ.
# (An oracle declaring nothing at all would classify the difference as unconstrained, which
# is a different guard -- tested in section 2, not here.)
check("without the declaration the same bytes disagree",
      axes_of(crlf, lf, {"outcome": "SUCCESS",
                        "expectation": {"languageExit": 0,
                                        "stdoutBase64": B64("a\r\nb")}}) == ["stdout"])


# --------------------------------------------------------------------------
# 7. Missing manifest fails safe: nothing may be silently treated as unconstrained.
# --------------------------------------------------------------------------
r6 = cmp(accepted(b"x", b"", 0), accepted(b"y", b"", 1), {})
check("absent manifest declares everything (no silent agreement)",
      set(r6["axes"]) >= {"stdout", "languageExit"})


# --------------------------------------------------------------------------
# 7b. Evidence: the report must let a reader distinguish "differed" from "equal".
# --------------------------------------------------------------------------
import hashlib as _hl

ev = cmp(accepted(b"x", b""), accepted(b"x", b"note"), no_stderr)
for side in ("left", "right"):
    check("%s carries a stdout digest" % side, ev[side]["stdoutSha256"] == _hl.sha256(b"x").hexdigest())
check("differing stderr yields differing digests",
      ev["left"]["stderrSha256"] != ev["right"]["stderrSha256"])
eq = cmp(accepted(b"x"), accepted(b"x"), SUCCESS)
check("equal stdout yields equal digests",
      eq["left"]["stdoutSha256"] == eq["right"]["stdoutSha256"])
# Digests are of the *normalized* bytes, matching exactly what the comparator compares.
# On a CRLF host the declared transform maps CRLF to LF, so raw-differing bytes must yield
# equal digests. On a LF host the transform is a no-op, so raw-differing bytes must still
# yield differing digests -- asserting the CRLF expectation unconditionally would bake the
# test host's line separator into a portability guarantee.
nd = cmp(crlf, lf, norm)
same_digest = nd["left"]["stdoutSha256"] == nd["right"]["stdoutSha256"]
if os.linesep == "\r\n":
    check("digest reflects declared normalization", same_digest)
else:
    check("no false agreement from an inapplicable transform", not same_digest)
# Platform-independent invariant: digests agree exactly when normalized bytes agree.
nd2 = cmp(accepted(b"a\nb"), accepted(b"a\nb"), norm)
check("identical normalized bytes give identical digests",
      nd2["left"]["stdoutSha256"] == nd2["right"]["stdoutSha256"])
# A phase that never ran has no output to digest: None, not the digest of b"".
rej = cmp(rejected("TYPE", "SOLV-TYPE-001"), rejected("TYPE", "SOLV-TYPE-001"), COMPILE_ERR)
check("absent execute phase yields no digest, not a digest of empty",
      rej["left"]["stdoutSha256"] is None and rej["right"]["stdoutSha256"] is None)
check("no raw guest bytes are copied into the record",
      "stdoutBase64" not in ev["left"] and "stderrBase64" not in ev["right"])


# --------------------------------------------------------------------------
# 7c. The CRLF host cannot be created here, but the transform must be correct for it.
# --------------------------------------------------------------------------
# `platform-line-separator` only does anything where os.linesep != "\n", so on this host
# the branch is a no-op and the assertions above cannot see it. Exercising `normalize_output`
# directly with the separator the transform is defined against is what makes the guarantee
# about CRLF hosts real instead of a comment. Both directions are checked: mapping must
# create equality, and it must not map two genuinely different values onto each other.
import importlib as _il
_real_linesep = os.linesep
try:
    os.linesep = "\r\n"                     # simulate a Windows host for the transform
    _il.reload(D)
    check("CRLF separator maps to LF",
          D.normalize_output(b"a\r\nb", "stdout", norm) == b"a\nb")
    check("mapping preserves the absence of a separator",
          D.normalize_output(b"a\nb", "stdout", norm) == b"a\nb")
    check("mapping is field-scoped (stderr unaffected)",
          D.normalize_output(b"a\r\nb", "stderr", norm) == b"a\r\nb")
finally:
    os.linesep = _real_linesep
    _il.reload(D)
# On the *real* host the transform must be a no-op for a foreign separator: only the
# platform's own separator is normalized, so \r\n passes through untouched on a LF host.
check("a foreign separator is not normalized on this host",
      D.normalize_output(b"a\r\nb", "stdout", norm) == (b"a\nb" if os.linesep == "\r\n"
                                                         else b"a\r\nb"))
check("undeclared field is never normalized",
      D.normalize_output(b"a\r\nb", "stdout", {"outcome": "SUCCESS",
                                               "expectation": {}}) == b"a\r\nb")
check("an unrepresentable transform cannot be silently ignored",
      _raises(lambda: D.normalize_output(
          b"x", "stdout", {"normalization": [{"field": "stdout", "transform": "trim"}]})))


# --------------------------------------------------------------------------
# 8. Suite assembly: one-sided absence, and counts.
# --------------------------------------------------------------------------
left = {"A": accepted(b"x"), "B": accepted(b"y"), "C": accepted(b"z")}
right = {"A": accepted(b"x"), "B": accepted(b"Z"), "D": accepted(b"w")}
suites = D.compare_suites("L", left, "R", right, {"A": SUCCESS, "B": SUCCESS})
by_id = {r["testId"]: r for r in suites["results"]}
check("both-present agreeing test has no axes", by_id["A"]["axes"] == [])
check("both-present differing test reports stdout", by_id["B"]["axes"] == ["stdout"])
check("one-sided test is inconclusive, not a disagreement",
      by_id["C"]["inconclusive"] and by_id["C"]["axes"] == [])
check("missing manifest still compares, does not crash",
      by_id["D"]["inconclusive"] and by_id["D"]["left"]["present"] is False)
c = suites["counts"]
check("counts: tests counted once each", c["tests"] == 4)
check("counts: compared excludes one-sided", c["compared"] == 2)
check("counts: disagreements counted from axes", c["disagreements"] == 1)
check("counts: inconclusive counted", c["inconclusive"] == 2)


# --------------------------------------------------------------------------
# 9. Exit code: disagreement -> 1, nothing comparable -> 2, clean -> 0.
# --------------------------------------------------------------------------
check("exit 1 on disagreement", D.exit_code({"disagreements": 1, "compared": 5}) == 1)
check("exit 0 on a clean comparison", D.exit_code({"disagreements": 0, "compared": 5}) == 0)
check("exit 2 when nothing could be compared (vacuous)",
      D.exit_code({"disagreements": 0, "compared": 0}) == 2)
# Falsifier for the vacuous guard: disagreement outranks vacuity.
check("disagreement outranks vacuity", D.exit_code({"disagreements": 3, "compared": 0}) == 1)


# --------------------------------------------------------------------------
# 10. The comparator must never be able to emit a conformance verdict.
# --------------------------------------------------------------------------
# Checked over the *code*, not the prose: the module docstring names PASS precisely to
# say it is never produced, so a plain text search would fail on correct code.
import ast  # noqa: E402

_src = open(os.path.join(HERE, "..", "runner", "tck_runner", "differential.py"),
            encoding="utf-8").read()
_strings = {n.value for n in ast.walk(ast.parse(_src))
            if isinstance(n, ast.Constant) and isinstance(n.value, str)}
for verdict in ("PASS", "FAIL", "CONFORMS", "FULL_CONFORMANCE", "NOT_RUN",
                "INFRASTRUCTURE_ERROR"):
    check("comparator never emits the verdict %s" % verdict, verdict not in _strings)
# Protocol status names (COMPILE_ACCEPTED, NORMAL_EXIT, ...) are deliberately present: they
# describe what an adapter said, which is not the same as judging it conformant.


# --------------------------------------------------------------------------
# 11. End to end through the CLI, using behavior-scripted fake adapters.
# --------------------------------------------------------------------------
# Everything above calls the comparator directly. This drives `tck_cli differential`
# itself -- argument parsing, suite loading, one subprocess per adapter, observation
# recording, the written report, and the exit code -- because a command that is only
# unit-tested can be wired wrongly and still show green tests. The fakes make it fast
# enough to run on every build, and they are the same fakes the runner self-tests use to
# prove the runner rejects defective implementations.
import json as _json
import subprocess as _sp
import tempfile as _tf

CLI = os.path.join(HERE, "..", "runner", "tck_cli.py")
FAKE = os.path.join(HERE, "fake_adapters", "fake_adapter.py")
FP = "a" * 64


def _fake_config(dirpath, name, stdout):
    """Write a config + behavior script for a fake adapter that prints `stdout`."""
    behavior = {"describe": {"name": name, "version": "1.0", "fingerprint": FP},
                "compile": {"result": "accepted"},
                "execute": {"result": "normal", "stdout": stdout,
                            "stderr": "", "languageExit": 0}}
    beh = os.path.join(dirpath, name + ".behavior.json")
    with open(beh, "w", encoding="utf-8") as fh:
        fh.write(_json.dumps(behavior))
    cfg = {"name": name, "argv": [sys.executable, FAKE], "fingerprint": FP,
           "env": {"SOLVIK_TCK_FAKE_BEHAVIOR": beh}}
    path = os.path.join(dirpath, name + ".config.json")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(_json.dumps(cfg))
    return path


with _tf.TemporaryDirectory() as td:
    sel = ["--test", "SOL-TCK-0001"]
    rep_path = os.path.join(td, "diff.json")

    def _run(left_cfg, right_cfg):
        return _sp.run([sys.executable, CLI, "differential",
                        "--left-config", left_cfg, "--right-config", right_cfg,
                        "--report", rep_path] + sel,
                       capture_output=True, text=True)

    agree_l = _fake_config(td, "fake-a", "hello")
    agree_r = _fake_config(td, "fake-b", "hello")
    p = _run(agree_l, agree_r)
    check("CLI: agreeing fakes exit 0", p.returncode == 0)
    doc = _json.load(open(rep_path, encoding="utf-8"))
    check("CLI: agreeing fakes report no disagreement", doc["counts"]["disagreements"] == 0)
    check("CLI: agreeing fakes were actually compared", doc["counts"]["compared"] == 1)
    check("CLI: report carries no conformance claim", doc["conformanceClaimed"] is False)
    check("CLI: report is not a conformance report",
          doc["schemaId"].endswith("differential-1.json"))

    differ_r = _fake_config(td, "fake-c", "other")
    p2 = _run(agree_l, differ_r)
    check("CLI: differing fakes exit 1", p2.returncode == 1)
    doc2 = _json.load(open(rep_path, encoding="utf-8"))
    check("CLI: stdout difference is reported as a disagreement",
          doc2["counts"]["disagreements"] == 1)
    check("CLI: the disagreement names the axis",
          doc2["results"][0]["axes"] == ["stdout"])
    check("CLI: sides are distinguishable by adapter name",
          doc2["results"][0]["left"]["adapter"] != doc2["results"][0]["right"]["adapter"])
    check("CLI: per-side output digests are recorded",
          doc2["results"][0]["left"]["stdoutSha256"] !=
          doc2["results"][0]["right"]["stdoutSha256"])
    check("CLI: no raw guest bytes in the report",
          "stdoutBase64" not in doc2["results"][0]["left"])

    # Two adapters with one identity: a configuration error, and it must be refused
    # before either suite is run rather than after two full runs.
    same = _fake_config(td, "fake-a", "hello")
    p3 = _run(same, same)
    check("CLI: identical adapter names are refused", p3.returncode == 2)
    check("CLI: the refusal explains itself", "distinct names" in p3.stderr)

    # A crash on one side is an infrastructure condition, not a language verdict: the
    # comparison must be inconclusive rather than reporting a disagreement.
    crash_behavior = {"describe": {"name": "fake-d", "version": "1.0", "fingerprint": FP},
                      "compile": {"result": "accepted"},
                      "execute": {"result": "normal", "stdout": "hello",
                                  "stderr": "", "languageExit": 0},
                      "faults": {"crash": True}}
    cb = os.path.join(td, "crash.behavior.json")
    with open(cb, "w", encoding="utf-8") as fh:
        fh.write(_json.dumps(crash_behavior))
    crash_cfg_path = os.path.join(td, "fake-d.config.json")
    with open(crash_cfg_path, "w", encoding="utf-8") as fh:
        fh.write(_json.dumps({"name": "fake-d", "argv": [sys.executable, FAKE],
                              "fingerprint": FP,
                              "env": {"SOLVIK_TCK_FAKE_BEHAVIOR": cb}}))
    p4 = _run(agree_l, crash_cfg_path)
    doc4 = _json.load(open(rep_path, encoding="utf-8"))
    check("CLI: a crash is not counted as a disagreement",
          doc4["counts"]["disagreements"] == 0)
    check("CLI: a crash makes the comparison inconclusive",
          doc4["counts"]["inconclusive"] == 1)
    # Nothing comparable at all must not look like a clean run.
    check("CLI: nothing comparable is not reported as success", p4.returncode != 0)


print("\n**%d self-test assertions**" % len(RESULTS))
failed = [n for n, ok_ in RESULTS if not ok_]
print("%d/%d passed" % (len(RESULTS) - len(failed), len(RESULTS)))
if failed:
    for n in failed:
        print("  FAILED:", n)
    sys.exit(1)
print("test_differential: OK")
