#!/usr/bin/env python3
"""End-to-end runner self-tests against a behavioral fake adapter.

Drives the real :class:`runner.Runner` against the fake subprocess adapter for
every outcome and the adversarial protocol/adapter conditions from TCK.md
sections 8, 8.1 and 14. Uses only Python and the fake adapter -- no Solvik,
Java, GraalVM, or Maven. Run via ``test_selftests.py`` or directly.
"""

import base64
import json
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "runner"))

from tck_runner import (  # noqa: E402
    isolation,
    report as RP,
    runner as RUN,
    schema as S,
    strict_json as SJ,
    versions as V,
)

FAKE = os.path.join(HERE, "fake_adapters", "fake_adapter.py")

RESULTS = []


def check(label, cond):
    RESULTS.append((label, bool(cond)))
    if not cond:
        print("FAIL:", label)


def b64(s):
    return base64.b64encode(s.encode()).decode()


def make_manifest(tmpdir, outcome="SUCCESS", expectation=None,
                  test_id="SOL-TCK-0001", category="numerics", profile="full-language",
                  status="required", requirements=("REQ-0001",), extra=None):
    d = os.path.join(tmpdir, test_id)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "main.sol"), "w") as fh:
        fh.write("println(1)\n")
    man = {
        "manifestSchemaVersion": 1,
        "specVersion": "2026.10-draft",
        "testId": test_id,
        "category": category,
        "profile": profile,
        "status": status,
        "requirements": list(requirements),
        "entryPoint": "main.sol",
        "outcome": outcome,
        "expectation": expectation if expectation is not None else {},
    }
    if extra:
        man.update(extra)
    path = os.path.join(d, "%s.manifest.json" % test_id)
    with open(path, "w") as fh:
        fh.write(SJ.dumps_canonical(man))
    return man, path


def make_model(reqs=None, closures=None):
    """A minimal inventory model sufficient for manifest cross-validation."""
    by_id = {
        "REQ-0001": {"id": "REQ-0001", "lifecycle": "active", "profile": "full-language",
                     "tests": ["SOL-TCK-0001"]},
    }
    for r in (reqs or []):
        by_id[r[0]] = {"id": r[0], "lifecycle": "active", "profile": r[1], "tests": ["SOL-TCK-0001"]}
    return {
        "by_id": by_id,
        "closures": closures or {"full-language": set(by_id)},
        "full_profile": "full-language",
        "spec_version": "2026.10-draft",
        "inventoryDigest": "a" * 64,
        "requiredCapabilities": ["compile-only"],
        "requirementGaps": [],
        "coverage": {},
        "ambiguities": [],
    }


def run(man, path, behavior, *, spec_version="2026.10-draft", profile="full-language",
        model=None, compile_to=2000, execute_to=2000):
    """Write a corpus containing one manifest, run the suite, return the report."""
    pschema = SJ.loads(open(os.path.join(HERE, "..", "schemas", "protocol-1.schema.json")).read())
    mschema = SJ.loads(open(os.path.join(HERE, "..", "schemas", "manifest-1.schema.json")).read())
    corpus = tempfile.mkdtemp(prefix="tck-corpus-")
    # Relocate the manifest's test dir into the corpus so discovery finds it.
    dest = os.path.join(corpus, man["testId"])
    os.makedirs(dest, exist_ok=True)
    import shutil

    src = os.path.dirname(path)
    for f in os.listdir(src):
        shutil.copy(os.path.join(src, f), os.path.join(dest, f))
    # Rewrite manifest to the new corpus location (digest recomputed on load).
    mpath = os.path.join(dest, os.path.basename(path))
    with open(mpath) as fh:
        man_doc = SJ.loadb(fh.read().encode())
    with open(mpath, "w") as fh:
        fh.write(SJ.dumps_canonical(man_doc))

    beh_path = os.path.join(corpus, "behavior.json")
    with open(beh_path, "w") as fh:
        fh.write(json.dumps(behavior))

    counter_path = os.path.join(corpus, "counter.txt")
    cfg = RUN.AdapterConfig(
        name="fake-jvm",
        argv=[sys.executable, FAKE],
        config_digest="c" * 64,
        fingerprint="f" * 64,
        env_overrides={"SOLVIK_TCK_FAKE_BEHAVIOR": beh_path,
                       "SOLVIK_TCK_FAKE_COUNTER": counter_path},
    )
    r = RUN.Runner(pschema, mschema, cfg, compile_to, execute_to)
    inv_model = model or make_model()
    return RUN.run_suite(r, corpus, inv_model, profile, spec_version,
                         requested_full_profile=True, adapter_config=cfg)


def by_id(report):
    return report["results"][0]["status"] if report["results"] else None


def test_certification_policy():
    """The aggregate conformance decision is a real branch, not a facade.

    Driven at the pure build_report layer so the PASS path is exercised live with a
    frozen, exhaustive baseline (baselineCertifiable=True), while a draft baseline --
    the shipped 2026.10-draft -- withholds certification (TCK.md sections 5 / 5.1).
    """
    ctx = {"specVersion": "2026.10-draft", "profile": "full-language", "platform": {},
           "timestamp": "2026-01-01T00:00:00Z", "filters": {}, "inputDigests": {}}
    passing = [{"testId": "SOL-TCK-0001", "requirements": ["REQ-0001"],
                "phases": {"compile": "COMPILE_ACCEPTED", "execute": "NORMAL_EXIT"},
                "status": "PASS", "reason": "ok"}]

    # Certifiable baseline + all required tests passed -> genuine PASS.
    c1 = dict(ctx, baselineCertifiable=True, unsupportedRequiredCapabilities=[],
              requirementGaps=[], ambiguities=[])
    r1 = RP.build_report(c1, passing, requested_full_profile=True)
    check("certifiable baseline -> conf PASS", r1["fullProfileConformance"] == "PASS")
    check("certifiable baseline -> exit0", r1["exitCode"] == 0)

    # Identical results but a draft/non-certifiable baseline -> WITHHELD, and the
    # exit code still reflects the individual PASSes (withholding is not a failure).
    c2 = dict(c1, baselineCertifiable=False)
    r2 = RP.build_report(c2, passing, requested_full_profile=True)
    check("draft baseline -> conf WITHHELD", r2["fullProfileConformance"] == "NOT_EVALUATED")
    check("draft baseline -> exit0", r2["exitCode"] == 0)
    check("draft baseline -> test still PASS", r2["results"][0]["status"] == "PASS")

    # A genuine conformance failure is still reported as FAIL, not hidden by the
    # withholding of certification.
    failing = [dict(passing[0], status="FAIL", reason="mismatch")]
    r3 = RP.build_report(c1, failing, requested_full_profile=True)
    check("certifiable + failure -> conf FAIL", r3["fullProfileConformance"] == "FAIL")
    check("certifiable + failure -> exit1", r3["exitCode"] == 1)

    # A required test that did not run prevents certification in both cases.
    notrun = [dict(passing[0], status="NOT_RUN")]
    r4 = RP.build_report(c1, notrun, requested_full_profile=True)
    check("NOT_RUN blocks certification", r4["fullProfileConformance"] == "NOT_EVALUATED")
    check("NOT_RUN -> exit2", r4["exitCode"] == 2)

    # The shipped spec revision really is the non-certifiable draft this policy assumes.
    check("shipped spec is not certifiable", V.SPEC_VERSION not in V.CERTIFIABLE_SPEC_VERSIONS)


def main():
    # --- SUCCESS happy path ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"execute": {"stdout": "hi\n"}})
    check("success PASS", rep["results"][0]["status"] == "PASS")
    # The suite spec version is the draft 2026.10-draft baseline, which is NOT a
    # frozen, exhaustive normative inventory, so aggregate certification is WITHHELD
    # (TCK.md sections 5 / 5.1) even though every seeded test individually passed.
    # The PASS decision itself is proven live at the build_report layer in
    # test_certification_policy() below -- this path is not a facade.
    check("success conf WITHHELD (draft baseline)", rep["fullProfileConformance"] == "NOT_EVALUATED")
    check("success exit0", rep["exitCode"] == 0)

    # --- wrong stdout -> FAIL ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("NO")}),
              {"execute": {"stdout": "hi\n"}})
    check("wrong stdout FAIL", rep["results"][0]["status"] == "FAIL")
    check("wrong stdout exit1", rep["exitCode"] == 1)

    # --- wrong language exit ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"execute": {"stdout": "hi\n", "languageExit": 3}})
    check("wrong exit FAIL", rep["results"][0]["status"] == "FAIL")

    # --- explicit exit(n) is normal SUCCESS ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 7, "stdoutBase64": ""}),
              {"execute": {"stdout": "", "languageExit": 7}})
    check("exit(n) SUCCESS PASS", rep["results"][0]["status"] == "PASS")

    # --- SUCCESS not satisfied by runtime failure ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"execute": {"result": "runtime", "runtimeCategory": "ARITHMETIC_ERROR"}})
    check("SUCCESS vs runtime FAIL", rep["results"][0]["status"] == "FAIL")

    # --- SUCCESS not satisfied by implementation crash ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"execute": {"result": "implementation_failure"}})
    check("SUCCESS vs crash FAIL", rep["results"][0]["status"] == "FAIL")

    # --- COMPILE_ERROR happy ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "COMPILE_ERROR",
                             {"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-009"}}),
              {"compile": {"result": "rejected",
                           "diagnostics": [{"family": "TYPE", "code": "SOLV-TYPE-009"}]}})
    check("compile_error PASS", rep["results"][0]["status"] == "PASS")

    # --- COMPILE_ERROR wrong code -> FAIL ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "COMPILE_ERROR",
                             {"diagnostic": {"code": "SOLV-TYPE-010"}}),
              {"compile": {"result": "rejected",
                           "diagnostics": [{"family": "TYPE", "code": "SOLV-TYPE-009"}]}})
    check("compile_error wrong code FAIL", rep["results"][0]["status"] == "FAIL")

    # --- compile error accepted anyway (accepted instead of rejected) ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "COMPILE_ERROR",
                             {"diagnostic": {"code": "SOLV-TYPE-009"}}),
              {"compile": {"result": "accepted"}})
    check("compile_error accepted FAIL", rep["results"][0]["status"] == "FAIL")

    # --- application output smuggled into a COMPILE_ACCEPTED response is a
    # protocol violation: the protocol schema forbids stdout on compile, so the
    # runner must reject the message rather than accept a compile that ran code. ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "COMPILE_SUCCESS", {}),
              {"compile": {"result": "accepted", "artifactFiles": ["a.bin"]},
               "faults": {"extraCompileOutput": True}})
    check("compile-only stdout rejected", rep["results"][0]["status"] == "INFRASTRUCTURE_ERROR")
    check("compile-only stdout exit2", rep["exitCode"] == 2)

    # --- COMPILE_SUCCESS happy ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "COMPILE_SUCCESS", {}),
              {"compile": {"result": "accepted", "artifactFiles": ["a.bin"]}})
    check("compile_success PASS", rep["results"][0]["status"] == "PASS")

    # --- RUNTIME_ERROR happy ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "RUNTIME_ERROR",
                             {"runtimeCategory": "ARITHMETIC_ERROR"}),
              {"execute": {"result": "runtime", "runtimeCategory": "ARITHMETIC_ERROR"}})
    check("runtime_error PASS", rep["results"][0]["status"] == "PASS")

    # --- RUNTIME_ERROR vs normal exit ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "RUNTIME_ERROR",
                             {"runtimeCategory": "ARITHMETIC_ERROR"}),
              {"execute": {"result": "normal", "stdout": "x"}})
    check("runtime_error vs normal FAIL", rep["results"][0]["status"] == "FAIL")

    # --- RUNTIME_ERROR not satisfied by a compile error ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "RUNTIME_ERROR",
                             {"runtimeCategory": "ARITHMETIC_ERROR"}),
              {"compile": {"result": "rejected",
                           "diagnostics": [{"family": "TYPE", "code": "SOLV-TYPE-009"}]}})
    check("runtime_error vs compile-error FAIL", rep["results"][0]["status"] == "FAIL")

    # --- RUNTIME_ERROR not satisfied by implementation crash ---
    rep = run(*make_manifest(tempfile.mkdtemp(), "RUNTIME_ERROR",
                             {"runtimeCategory": "ARITHMETIC_ERROR"}),
              {"execute": {"result": "implementation_failure"}})
    check("runtime_error vs crash FAIL", rep["results"][0]["status"] == "FAIL")

    test_certification_policy()

    # ================= ADVERSARIAL (all must be INFRASTRUCTURE_ERROR) ===========
    def infra(label, behavior, outcome="SUCCESS", expectation=None):
        rep = run(*make_manifest(tempfile.mkdtemp(), outcome,
                                 expectation or {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
                  behavior)
        check(label, rep["results"][0]["status"] == "INFRASTRUCTURE_ERROR")
        check(label + " exit2", rep["exitCode"] == 2)
        return rep

    base_exec = {"execute": {"stdout": "hi\n"}}
    infra("crash after compile", {**base_exec, "faults": {"crash": True}})
    infra("nonzero adapter exit", {**base_exec, "faults": {"nonzeroExit": True}})
    infra("wrong protocol version", {"execute": {"stdout": "hi\n"},
                                     "faults": {"wrongProtocolVersion": True}})
    infra("wrong request id", {"execute": {"stdout": "hi\n"},
                               "faults": {"wrongRequestId": True}})
    infra("duplicate json keys", {"faults": {"duplicateKeys": True}})
    infra("garbage line", {"faults": {"garbageLine": True}})
    infra("oversized response", {"faults": {"oversizedResponse": True}})
    infra("no artifact handle", {"faults": {"noArtifactHandle": True}})
    # A SUCCESS expectation whose program is rejected at compile must FAIL (a
    # compile error can never satisfy a success/runtime expectation); the runner
    # never sends execute after a rejected compile, so this is a conformance FAIL.
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"compile": {"result": "rejected",
                           "diagnostics": [{"family": "TYPE", "code": "SOLV-TYPE-009"}]}})
    check("success vs rejected compile FAIL", rep["results"][0]["status"] == "FAIL")
    # And a diagnostic that is itself schema-invalid (no family) is an
    # infrastructure error, not a conformance judgment.
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"compile": {"result": "rejected", "diagnostics": [{"code": "SOLV-TYPE-009"}]}})
    check("invalid diagnostic INFRA", rep["results"][0]["status"] == "INFRASTRUCTURE_ERROR")
    infra("changed identity mid-run",
          {"execute": {"stdout": "hi\n"}, "faults": {"changedIdentity": True}})
    infra("compile timeout", {"execute": {"stdout": "hi\n"},
                              "faults": {"sleepCompileMs": 5000}}, )
    infra("execute timeout", {"execute": {"stdout": "hi\n"},
                              "faults": {"sleepExecuteMs": 5000}})

    # --- artifact verification: an accepted compile that fails to materialize a
    # declared artifact, or writes a mismatched one, is an infrastructure error ---
    infra("missing declared artifact",
          {"compile": {"result": "accepted", "artifactFiles": ["a.bin"]},
           "execute": {"stdout": "hi\n"},
           "faults": {"artifactMissing": True}})
    infra("artifact digest mismatch",
          {"compile": {"result": "accepted", "artifactFiles": ["a.bin"]},
           "execute": {"stdout": "hi\n"},
           "faults": {"artifactMismatch": True}})
    # A conforming AOT-style adapter that materializes matching artifacts passes.
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"compile": {"result": "accepted", "artifactFiles": ["a.bin", "dir/b.bin"]},
               "execute": {"stdout": "hi\n"}})
    check("materialized artifacts pass", rep["results"][0]["status"] == "PASS")
    # An adapter that edits its own staged inputs is caught (source mutation).
    infra("source mutation after staging",
          {"compile": {"result": "accepted", "artifactFiles": ["a.bin"]},
           "execute": {"stdout": "hi\n"},
           "faults": {"mutateSource": "main.sol"}})

    # guest/adapter stderr attempting protocol injection must not confuse runner
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"execute": {"stdout": "hi\n"}, "faults": {"injectOnStderr": True}})
    check("stderr injection harmless PASS", rep["results"][0]["status"] == "PASS")

    # --- report validates against the report schema for a PASS run ---
    pschema_ok = SJ.loads(open(os.path.join(HERE, "..", "schemas", "report-1.schema.json")).read())
    rep = run(*make_manifest(tempfile.mkdtemp(), "SUCCESS",
                             {"languageExit": 0, "stdoutBase64": b64("hi\n")}),
              {"execute": {"stdout": "hi\n"}})
    try:
        S.validate(pschema_ok, rep)
        check("integration report schema-valid", True)
    except Exception as exc:  # noqa: BLE001
        check("integration report schema-valid (%s)" % exc, False)

    failed = [l for l, c in RESULTS if not c]
    print("integration fake: %d/%d passed" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
