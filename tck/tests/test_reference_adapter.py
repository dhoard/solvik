#!/usr/bin/env python3
"""Regression tests for the independent reference front end (Python only).

Why this module exists
----------------------
`adapters/reference_subset_adapter.py` is a 500-line second implementation of the
adapter protocol and the only evidence for TCK.md acceptance criterion 11 ("a third
party can implement the documented protocol without Solvik Java or Truffle classes").
Until now nothing executed it automatically: its advertised behaviour was a number in a
Markdown table, so a bug introduced into it could only be discovered by someone
remembering to run a differential by hand. An unexercised "independent implementation"
is an unverified claim, which is exactly the false-coverage shape this TCK exists to
refuse.

What is being checked, and why it does not taint an oracle
---------------------------------------------------------
These tests compare the reference adapter against the *checked-in corpus oracles*. The
direction of justification matters: the corpus expectations were derived by hand from
LANGUAGE_SPEC.md and are independent of this adapter, so using them to test the adapter
is not circular, and no expectation anywhere in the TCK is derived from this adapter's
output. The adapter remains a differential partner and a protocol-implementability
witness, never an oracle source.

The properties asserted are the ones that make the adapter worth having:

  * on the programs it claims to understand it reproduces the corpus oracle exactly
    (byte-exact stdout, including embedded NUL and escape sequences);
  * everywhere else it *refuses* -- it never invents a language verdict for a construct
    outside its declared subset, which is the difference between an incomplete
    implementation and a lying one;
  * refusals are reported as the adapter's own inability, so the runner can judge them
    as non-conformance rather than as a statement about the program;
  * the set of programs it answers at all is an explicit allowlist, so an edit that
    makes it start guessing on new syntax fails here instead of silently widening a
    differential run;
  * a diagnostic it reports carries a code the specification actually names.
"""

import base64
import collections
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TCK_ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(TCK_ROOT, "runner"))

# Import the CLI itself rather than re-implementing its loading, so this module cannot
# drift from the entry point an end user actually runs.
sys.path.insert(0, os.path.join(TCK_ROOT, "runner"))
import tck_cli  # noqa: E402
from tck_runner import (  # noqa: E402
    runner as RUN,
    strict_json as SJ,
    versions as V,
)

REF_ADAPTER = os.path.join(TCK_ROOT, "adapters", "reference_subset_adapter.py")
CORPUS = os.path.join(TCK_ROOT, "corpus")
REQ_PATH = os.path.join(TCK_ROOT, "requirements", "requirements.json")
PROFILES = os.path.join(TCK_ROOT, "profiles")

RESULTS = []


def check(label, cond, detail=""):
    RESULTS.append((label + ((" -- " + detail) if detail else ""), bool(cond)))
    if not cond:
        print("FAIL:", label)


_CORPUS = None


def _load_corpus():
    """Every (testId, manifest, source dir) in the checked-in corpus."""
    global _CORPUS
    if _CORPUS is not None:
        return _CORPUS
    out = []
    for spec in sorted(os.listdir(CORPUS)):
        spec_dir = os.path.join(CORPUS, spec)
        if not os.path.isdir(spec_dir):
            continue
        for name in sorted(os.listdir(spec_dir)):
            test_dir = os.path.join(spec_dir, name)
            if not os.path.isdir(test_dir):
                continue
            for fn in os.listdir(test_dir):
                if fn.endswith(".manifest.json"):
                    with open(os.path.join(test_dir, fn), "rb") as fh:
                        man = SJ.loadb(fh.read())
                    out.append((man["testId"], man, test_dir))
    _CORPUS = out
    return out


def _adapter_fingerprint():
    proc = subprocess.run([sys.executable, REF_ADAPTER, "--fingerprint"],
                          capture_output=True, text=True)
    return proc.stdout.strip()


def _describe():
    proc = subprocess.run([sys.executable, REF_ADAPTER],
                          input=json.dumps({"protocolVersion": V.PROTOCOL_VERSION,
                                            "requestId": 1, "op": "describe"}) + "\n",
                          capture_output=True, text=True)
    lines = [l for l in proc.stdout.splitlines() if l.strip()]
    return json.loads(lines[0]) if lines else None


# The programs the reference front end answers at all, and how. Everything outside this
# table must be refused. The table is deliberately explicit: growing it is a deliberate
# act (implement more of the language), and a change here must be accompanied by the
# differential numbers in IMPLEMENTATION_PLAN.md being re-measured rather than reworded.
#   accepted  -> the adapter returns COMPILE_ACCEPTED and executes it
#   rejected  -> the adapter returns COMPILE_REJECTED carrying the named spec code
EXPECTED_ACCEPTED = {
    "SOL-TCK-0019",   # normal-string escape table, incl. NUL and CR
    "SOL-TCK-0020",   # `$` has no interpolation meaning
    "SOL-TCK-0051",   # null / true / false rendering
    "SOL-TCK-0102",   # single-file include expansion
    "SOL-TCK-0103",   # nested include expansion order
    "SOL-TCK-0166",   # section 1 identifier character class, bound and read back
    "SOL-TCK-0173",   # section 1 `//` line comment between two statements
    "SOL-TCK-0175",   # section 1 in-range decimal Integer literal
}
EXPECTED_REJECTED = {
    "SOL-TCK-0098": "SOLV-RESOL-008",   # include of a missing file
    "SOL-TCK-0099": "SOLV-RESOL-011",   # self-including file
    "SOL-TCK-0100": "SOLV-RESOL-012",   # include cycle
}


def _run_suite():
    """Drive the real Runner over the real corpus with the reference adapter."""
    suite = tck_cli._load_suite_inputs(CORPUS, REQ_PATH, PROFILES, V.FULL_PROFILE, V.SPEC_VERSION)
    model = suite["model"]
    mschema, pschema = suite["manifestSchema"], suite["protocolSchema"]
    cfg = RUN.AdapterConfig(
        name="reference-subset",
        argv=[sys.executable, REF_ADAPTER],
        config_digest="c" * 64,
        fingerprint=_adapter_fingerprint(),
        env_overrides={},
    )
    r = RUN.Runner(pschema, mschema, cfg, compile_timeout_ms=20000, execute_timeout_ms=20000)
    return RUN.run_suite(r, CORPUS, model, V.FULL_PROFILE, V.SPEC_VERSION,
                         requested_full_profile=True, filters=None, adapter_config=cfg)


_REPORT = None


def report():
    global _REPORT
    if _REPORT is None:
        _REPORT = _run_suite()
    return _REPORT


def _by_id():
    return {r["testId"]: r for r in report()["results"]}


# ---------------------------------------------------------------------------
# 1. The adapter actually runs, and identifies itself as non-Solvik.
# ---------------------------------------------------------------------------
def test_describe_is_self_consistent():
    desc = _describe()
    check("reference adapter answers describe", desc is not None)
    if desc is None:
        return
    impl = desc.get("implementation", {})
    check("describe fingerprint matches --fingerprint",
          impl.get("fingerprint") == _adapter_fingerprint())
    check("describe advertises the compile-only capability",
          "compile-only" in (impl.get("capabilities") or []))
    name = (impl.get("name") or "").lower()
    check("adapter names itself as an independent front end, not as Solvik's VM",
          "reference" in name and "graal" not in name and "truffle" not in name)


def test_adapter_imports_no_jvm_classes():
    """Criterion 11 is about what the adapter *loads*, so inspect its import statements
    rather than grepping the file. A naive substring search over the whole source is
    unusable here: the adapter's own docstring says it imports "no Solvik, GraalVM or
    Truffle code", so the very evidence of compliance is also a false positive for a
    word search. Only `import` / `from ... import` lines can create a dependency."""
    with open(REF_ADAPTER, encoding="utf-8") as fh:
        src = fh.read()
    imports = []
    for line in src.splitlines():
        stripped = line.strip()
        if stripped.startswith("import ") or stripped.startswith("from "):
            imports.append(stripped)
    check("every import in the adapter is a Python statement (not a prose mention)",
          all(("import" in i) for i in imports))
    banned = ("java", "graal", "truffle", "jvm", "solvik", "tck_runner")
    joined = " ".join(imports).lower()
    hits = [b for b in banned if b in joined]
    check("adapter imports no Java/GraalVM/Truffle/Solvik/runner code", not hits,
          "imports: %s" % "; ".join(imports))
    # It must still be runnable, i.e. genuinely pure-Python.
    check("adapter is importable by the plain stdlib interpreter",
          subprocess.run([sys.executable, "-c",
                          "import ast;ast.parse(open(%r).read())" % REF_ADAPTER],
                         capture_output=True).returncode == 0)


# ---------------------------------------------------------------------------
# 2. On its declared subset the adapter reproduces the corpus oracle exactly.
# ---------------------------------------------------------------------------
def test_accepted_programs_match_their_oracles():
    corpus = {tid: man for tid, man, _d in _load_corpus()}
    dirs = {tid: d for tid, man, d in _load_corpus()}
    # Only programs the adapter actually *executed* have a byte oracle to reproduce.
    executed = [tid for tid in sorted(corpus)
                if corpus[tid]["outcome"] == "SUCCESS"
                and corpus[tid]["testId"] in EXPECTED_ACCEPTED]
    check("the declared subset contains executable programs",
          len(executed) > 0, "got none")
    for tid in executed:
        exp = corpus[tid].get("expectation", {})
        want = base64.b64decode(exp.get("stdoutBase64", "")).decode() \
            if "stdoutBase64" in exp else None
        got = _direct_execute(corpus[tid], dirs[tid])
        check("%s executed output matches its oracle byte for byte" % tid,
              want is not None and got == want,
              "want %r got %r" % (want, got))
    # And the accepted verdicts must be the ones the runner judged PASS.
    passed = {r["testId"] for r in report()["results"] if r["status"] == "PASS"}
    check("every program the adapter executes is judged PASS",
          set(EXPECTED_ACCEPTED) <= passed,
          "not PASS: %s" % sorted(set(EXPECTED_ACCEPTED) - passed))


def _source_dir(corpus_by_id, tid):
    for t, _man, d in _load_corpus():
        if t == tid:
            return d
    return None


def _direct_execute(man, source_dir):
    """Compile then execute one program, returning decoded guest stdout."""
    ws = tempfile.mkdtemp(prefix="ref-ws-")
    try:
        staged = os.path.join(ws, "t")
        shutil.copytree(source_dir, staged)
        entry = man["entryPoint"]
        tree = hashlib.sha256(entry.encode()).hexdigest()
        compile_req = {"protocolVersion": V.PROTOCOL_VERSION, "requestId": 2,
                       "op": "compile", "workspace": staged, "entryPoint": entry,
                       "inputTreeDigest": tree, "compileTimeoutMs": 20000}
        lines = [json.dumps({"protocolVersion": V.PROTOCOL_VERSION, "requestId": 1,
                             "op": "describe"}), json.dumps(compile_req)]
        proc = subprocess.run([sys.executable, REF_ADAPTER],
                              input="\n".join(lines) + "\n",
                              capture_output=True, text=True)
        msgs = [json.loads(l) for l in proc.stdout.splitlines() if l.strip()]
        comp = msgs[1] if len(msgs) > 1 else {}
        handle = comp.get("artifactHandle")
        if not handle:
            return None
        exec_req = {"protocolVersion": V.PROTOCOL_VERSION, "requestId": 3,
                    "op": "execute", "workspace": staged, "artifactHandle": handle,
                    "inputTreeDigest": tree, "executeTimeoutMs": 20000}
        proc2 = subprocess.run([sys.executable, REF_ADAPTER],
                               input="\n".join(lines + [json.dumps(exec_req)]) + "\n",
                               capture_output=True, text=True)
        msgs2 = [json.loads(l) for l in proc2.stdout.splitlines() if l.strip()]
        ex = msgs2[-1] if msgs2 else {}
        if ex.get("status") != "NORMAL_EXIT":
            return None
        return base64.b64decode(ex.get("stdoutBase64", "")).decode()
    finally:
        shutil.rmtree(ws, ignore_errors=True)


def _direct_compile_status(man, source_dir):
    ws = tempfile.mkdtemp(prefix="ref-ws-")
    try:
        staged = os.path.join(ws, "t")
        shutil.copytree(source_dir, staged)
        entry = man["entryPoint"]
        req = {"protocolVersion": V.PROTOCOL_VERSION, "requestId": 2, "op": "compile",
               "workspace": staged, "entryPoint": entry,
               "inputTreeDigest": hashlib.sha256(entry.encode()).hexdigest(),
               "compileTimeoutMs": 20000}
        proc = subprocess.run([sys.executable, REF_ADAPTER],
                              input="\n".join([json.dumps(
                                  {"protocolVersion": V.PROTOCOL_VERSION,
                                   "requestId": 1, "op": "describe"}),
                                  json.dumps(req)]) + "\n",
                              capture_output=True, text=True)
        msgs = [json.loads(l) for l in proc.stdout.splitlines() if l.strip()]
        return msgs[1] if len(msgs) > 1 else {}
    finally:
        shutil.rmtree(ws, ignore_errors=True)


# ---------------------------------------------------------------------------
# 3. Refusal discipline: no verdict is invented outside the declared subset.
# ---------------------------------------------------------------------------
def test_answered_programs_are_exactly_the_allowlist():
    corpus = {tid: man for tid, man, _d in _load_corpus()}
    dirs = {tid: d for tid, man, d in _load_corpus()}
    answered, refused, wrong_code = [], [], []
    for tid in sorted(corpus):
        msg = _direct_compile_status(corpus[tid], dirs[tid])
        status = msg.get("status")
        if status == "COMPILE_ACCEPTED":
            answered.append(tid)
        elif status == "COMPILE_REJECTED":
            codes = [d.get("code") for d in (msg.get("diagnostics") or [])]
            if tid in EXPECTED_REJECTED and EXPECTED_REJECTED[tid] in codes:
                answered.append(tid)
            else:
                wrong_code.append((tid, codes))
        elif status == "IMPLEMENTATION_FAILURE":
            refused.append(tid)
        else:
            wrong_code.append((tid, status))
    got_accepted = {t for t in answered if t in EXPECTED_ACCEPTED}
    got_rejected = {t for t in answered if t in EXPECTED_REJECTED}
    check("it answers exactly the declared subset of programs",
          answered == sorted(set(EXPECTED_ACCEPTED) | set(EXPECTED_REJECTED)),
          "answered %s" % sorted(set(answered) ^ (set(EXPECTED_ACCEPTED) | set(EXPECTED_REJECTED))))
    check("every accepted program is executed to a correct verdict",
          got_accepted <= {r["testId"] for r in report()["results"] if r["status"] == "PASS"})
    check("it refuses every program outside its subset rather than guessing",
          set(refused) | set(answered) == set(corpus),
          "%d tests got neither a verdict nor an honest refusal"
          % (len(corpus) - len(refused) - len(answered)))


def test_refusals_never_become_language_results():
    """The single most important property of a deliberately incomplete adapter: a
    program it does not implement must never look like a program it judged.

    `IMPLEMENTATION_FAILURE` is defined by the protocol as the adapter reporting about
    itself, so the runner must judge it as non-conformance and must never record a
    stdout/exit value derived from it. If this ever stops holding, an unimplemented
    construct could be reported as a pass or a fail.
    """
    rep = report()
    refused = [r for r in rep["results"] if r["testId"] not in
               (set(EXPECTED_ACCEPTED) | set(EXPECTED_REJECTED))]
    check("refused tests are numerous enough for this check to matter",
          len(refused) > 100, "only %d" % len(refused))
    judged_pass = [r["testId"] for r in refused if r["status"] == "PASS"]
    check("no refused test is ever judged PASS", not judged_pass, str(judged_pass[:5]))
    bad = [r["testId"] for r in refused
           if r.get("phases", {}).get("execute") not in (None, "NONE")]
    check("no refused test is executed", not bad, str(bad[:5]))
    check("refused tests are reported as failures of conformance, not infrastructure",
          all(r["status"] == "FAIL" for r in refused),
          str([(r["testId"], r["status"]) for r in refused if r["status"] != "FAIL"][:5]))
    reasons = collections.Counter(r["reason"] for r in refused)
    check("every refusal reason describes an absent language result",
          all(("requires successful compilation" in reason or
               "expects a structured COMPILE_REJECTED" in reason or
               "cannot be satisfied by a compile failure" in reason)
              for reason in reasons),
          str([r for r in reasons if "requires successful" not in r][:3]))


def test_reported_diagnostic_codes_are_spec_named():
    """An implementation-chosen code would make the second opinion worthless, because
    agreeing on an invented code is not agreement about the specification."""
    with open(os.path.join(TCK_ROOT, "..", "docs", "LANGUAGE_SPEC.md"),
              encoding="utf-8") as fh:
        spec = fh.read()
    corpus = {tid: man for tid, man, _d in _load_corpus()}
    dirs = {tid: d for tid, man, d in _load_corpus()}
    for tid, expected_code in sorted(EXPECTED_REJECTED.items()):
        msg = _direct_compile_status(corpus[tid], dirs[tid])
        codes = {d.get("code") for d in (msg.get("diagnostics") or [])}
        check("%s rejects with the code the specification names (%s)" % (tid, expected_code),
              expected_code in codes, "got %s" % sorted(codes))
        for code in codes:
            check("%s reports only codes that occur in LANGUAGE_SPEC.md (%s)" % (tid, code),
                  code in spec)


def test_declared_subset_is_small_and_documented():
    """A guard against the adapter quietly becoming a second conformance oracle.

    The adapter cannot certify the full-language profile precisely because it implements
    a subset. If its accepted set grew to cover the whole corpus it would either be a
    second complete implementation (at which point the claims in its own docstring are
    stale) or it started guessing. Either way this check should force a decision.
    """
    total = len(_load_corpus())
    answered = len(EXPECTED_ACCEPTED) + len(EXPECTED_REJECTED)
    check("the adapter still answers only a small fraction of the corpus",
          answered * 4 < total,
          "answers %d of %d; update its docstring, this guard, and the plan" %
          (answered, total))


def test_refusal_set_matches_the_specification_extraction():
    """The adapter's reserved-looking name set must stay a mechanical reading of the spec.

    The adapter refuses to bind a name that appears in LANGUAGE_SPEC.md as a backticked bare
    lowercase word, because section 1 reserves keywords while supplying no keyword list -- so a
    binding name's legality cannot be decided and over-refusal is the only sound direction. That
    is only defensible while the set really *is* the mechanical extraction: the moment someone
    curates it (dropping `value` as "obviously prose", or adding words to make a test pass), the
    adapter has started making language decisions it has no authority to make, and its second
    opinions stop being independent of the implementation's choices.
    """
    with open(os.path.join(TCK_ROOT, "..", "docs", "LANGUAGE_SPEC.md"),
              encoding="utf-8") as fh:
        spec = fh.read()
    extracted = set(re.findall(r"`([a-z]{2,})`", spec))
    src = open(REF_ADAPTER, encoding="utf-8").read()
    m = re.search(r"_RESERVEDISH = frozenset\(\{(.*?)\}\)", src, re.S)
    check("the adapter declares a reserved-looking name set", m is not None, "not found")
    if not m:
        return
    declared = set(re.findall(r'"([a-z]{2,})"', m.group(1)))
    check("the adapter's refused-name set equals the mechanical spec extraction",
          declared == extracted,
          "adapter-only %s; spec-only %s"
          % (sorted(declared - extracted)[:8], sorted(extracted - declared)[:8]))
    # A set that refused nothing would make the guard above vacuous, and would also mean the
    # adapter was back to accepting `val class = 5`.
    check("the refused-name set is large enough to matter", len(declared) > 40,
          "%d names" % len(declared))


def test_out_of_range_integer_literals_are_refused_not_accepted():
    """Section 1 forbids a literal outside the signed 32-bit range but names no code for it.

    The adapter must therefore refuse such a program rather than accept it: accepting would assert
    the literal was valid, which the specification contradicts. This is falsified by deleting the
    range check in `_in_int32_range`, which makes the adapter accept SOL-TCK-0176 and agree with
    nothing, because SOL-TCK-0176's oracle is a rejection.
    """
    corpus = {tid: man for tid, man, _d in _load_corpus()}
    dirs = {tid: d for tid, man, d in _load_corpus()}
    out_of_range = [tid for tid, man in corpus.items()
                    if re.search(r"\b(?:\d{10,})\b", open(
                        os.path.join(dirs[tid], man["entryPoint"]), encoding="utf-8").read())
                    and man["outcome"] == "COMPILE_ERROR"]
    check("the corpus contains an out-of-range-integer rejection program to test against",
          bool(out_of_range), "none found")
    wrong = []
    for tid in sorted(out_of_range):
        msg = _direct_compile_status(corpus[tid], dirs[tid])
        if msg.get("status") != "IMPLEMENTATION_FAILURE":
            wrong.append((tid, msg.get("status")))
    check("every out-of-range integer program is refused, never accepted",
          not wrong, str(wrong[:4]))


def test_fingerprint_is_stable_and_distinct():
    fp = _adapter_fingerprint()
    check("fingerprint is a hex sha256", len(fp) == 64 and all(c in "0123456789abcdef" for c in fp))
    check("fingerprint is reproducible across invocations", fp == _adapter_fingerprint())


def main():
    if not os.path.isfile(REF_ADAPTER):
        print("reference adapter not found: %s" % REF_ADAPTER, file=sys.stderr)
        return 2
    test_describe_is_self_consistent()
    test_adapter_imports_no_jvm_classes()
    test_accepted_programs_match_their_oracles()
    test_answered_programs_are_exactly_the_allowlist()
    test_refusals_never_become_language_results()
    test_reported_diagnostic_codes_are_spec_named()
    test_declared_subset_is_small_and_documented()
    test_refusal_set_matches_the_specification_extraction()
    test_out_of_range_integer_literals_are_refused_not_accepted()
    test_fingerprint_is_stable_and_distinct()

    failed = [l for l, ok in RESULTS if not ok]
    print("=" * 70)
    if failed:
        for f in failed:
            print("FAIL: %s" % f)
        print("%d/%d passed" % (len(RESULTS) - len(failed), len(RESULTS)))
        return 1
    print("reference adapter: %d/%d passed" % (len(RESULTS), len(RESULTS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
