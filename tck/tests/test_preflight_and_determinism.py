#!/usr/bin/env python3
"""Preflight, capability, determinism, and report-redaction self-tests (13, 14).

* A describe-only preflight that cannot target the requested spec version or
  profile must fail and mark all tests NOT_RUN with exit 2 (a required
  capability/profile cannot be waived).
* A false/insufficient capability declaration (no ``compile-only``) leaves the
  tests executed but blocks full-profile certification: exit 2, NOT_EVALUATED.
* Report semantic content is independent of completion/insertion order (results
  are sorted by test id), so parallel scheduling cannot change the verdicts.
* Filtering yields a per-test exit-0 but NOT_EVALUATED full conformance.
* Recorded reproduction env redacts non-public values.

Only Python + the fake adapter are used.
"""

import base64
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "runner"))

from tck_runner import isolation, report as RP, runner as RUN, strict_json as SJ  # noqa: E402

RESULTS = []
FAKE = os.path.join(HERE, "fake_adapters", "fake_adapter.py")
PS = SJ.loads(open(os.path.join(HERE, "..", "schemas", "protocol-1.schema.json")).read())
MS = SJ.loads(open(os.path.join(HERE, "..", "schemas", "manifest-1.schema.json")).read())


def check(label, cond):
    RESULTS.append((label, bool(cond)))
    if not cond:
        print("FAIL:", label)


def b64(s):
    return base64.b64encode(s.encode()).decode()


def make_corpus(manifests):
    root = tempfile.mkdtemp(prefix="tck-det-")
    for i, m in enumerate(manifests):
        d = os.path.join(root, "c%02d" % i)
        os.makedirs(d)
        open(os.path.join(d, "main.sol"), "w").write("println(1)\n")
        open(os.path.join(d, "m.manifest.json"), "w").write(SJ.dumps_canonical(m))
    return root


def manifest(tid, outcome="SUCCESS", exp=None):
    return {"manifestSchemaVersion": 1, "specVersion": "2026.10-draft", "testId": tid,
            "category": "numerics", "profile": "full-language", "status": "required",
            "requirements": ["REQ-0001"], "entryPoint": "main.sol", "outcome": outcome,
            "expectation": exp if exp is not None else {"languageExit": 0, "stdoutBase64": b64("hi\n")}}


def model():
    return {"by_id": {"REQ-0001": {"id": "REQ-0001", "lifecycle": "active",
                                   "profile": "full-language", "tests": ["SOL-TCK-0001"]}},
            "closures": {"full-language": {"REQ-0001"}}, "full_profile": "full-language",
            "spec_version": "2026.10-draft", "inventoryDigest": "a" * 64,
            "requiredCapabilities": ["compile-only"], "requirementGaps": [], "coverage": {},
            "ambiguities": []}


def run_suite(behavior, manifests, profile="full-language", spec="2026.10-draft", full=True):
    corpus = make_corpus(manifests)
    beh = os.path.join(corpus, "behavior.json")
    open(beh, "w").write(json.dumps(behavior))
    cfg = RUN.AdapterConfig("fake", [sys.executable, FAKE], "c" * 64, "f" * 64,
                            {"SOLVIK_TCK_FAKE_BEHAVIOR": beh,
                             "SOLVIK_TCK_FAKE_COUNTER": os.path.join(corpus, "c.txt")})
    r = RUN.Runner(PS, MS, cfg, 2000, 2000)
    return RUN.run_suite(r, corpus, model(), profile, spec, full, adapter_config=cfg)


def main():
    good = {"execute": {"stdout": "hi\n"}}

    # Preflight cannot target the requested profile -> all NOT_RUN, exit 2.
    rep = run_suite({"describe": {"profiles": ["some-other-profile"]}, **good},
                    [manifest("SOL-TCK-0001")])
    check("profile-mismatch preflight NOT_RUN", rep["results"][0]["status"] == "NOT_RUN")
    check("profile-mismatch exit2", rep["exitCode"] == 2)
    check("profile-mismatch NOT_EVALUATED", rep["fullProfileConformance"] == "NOT_EVALUATED")

    # Preflight cannot target the requested spec version -> exit 2.
    rep = run_suite({"describe": {"specVersions": ["1.0"]}, **good},
                    [manifest("SOL-TCK-0001")], spec="2026.10-draft")
    check("spec-mismatch preflight exit2", rep["exitCode"] == 2)

    # False capability: compiles/executes fine but omits the required
    # 'compile-only' capability -> tests run but certification is blocked.
    rep = run_suite({"describe": {"capabilities": []}, **good}, [manifest("SOL-TCK-0001")])
    check("false-capability test ran", rep["results"][0]["status"] == "PASS")
    check("false-capability unsupported required",
          rep["unsupportedRequiredCapabilities"] == ["compile-only"])
    check("false-capability exit2", rep["exitCode"] == 2)
    check("false-capability NOT_EVALUATED", rep["fullProfileConformance"] == "NOT_EVALUATED")

    # Determinism: report semantic content is insertion-order independent.
    manifests = [manifest("SOL-TCK-0003"), manifest("SOL-TCK-0001"), manifest("SOL-TCK-0002")]
    rep_a = run_suite(good, manifests, full=True)
    rep_b = run_suite(good, list(reversed(manifests)), full=True)
    a = RP.strict_json.dumps_canonical(_semantic(rep_a))
    b = RP.strict_json.dumps_canonical(_semantic(rep_b))
    check("determinism order-independent", a == b)
    check("results sorted by id",
          [r["testId"] for r in rep_a["results"]] == ["SOL-TCK-0001", "SOL-TCK-0002", "SOL-TCK-0003"])

    # Filtered run: exit 0 for requested tests, full conformance NOT_EVALUATED.
    rep = run_suite(good, [manifest("SOL-TCK-0001")], full=False)
    check("filtered exit0", rep["exitCode"] == 0)
    check("filtered NOT_EVALUATED", rep["fullProfileConformance"] == "NOT_EVALUATED")

    # Env redaction: secrets never appear, names are preserved.
    red = isolation.redact_env({"PATH": "/usr/bin", "LANG": "C.UTF-8", "MY_TOKEN": "s3cr3t",
                                "SOLVIK_TCK_FAKE_BEHAVIOR": "/tmp/behavior.json"})
    check("redact keeps LANG value", red["LANG"] == "C.UTF-8")
    check("redact hides token", red["MY_TOKEN"] == "***")
    check("redact hides behavior path", red["SOLVIK_TCK_FAKE_BEHAVIOR"] == "***")
    check("redact hides PATH value", red["PATH"] == "***")
    check("redact preserves name", "MY_TOKEN" in red)
    # A reproduction record in a result contains only redacted env.
    rep = run_suite(good, [manifest("SOL-TCK-0001")])
    repro = rep["results"][0].get("reproduction")
    check("reproduction present", repro is not None)
    check("reproduction env redacted",
          repro and all(v == "***" for k, v in repro["env"].items()
                        if k.startswith("SOLVIK_TCK_FAKE")))

    failed = [l for l, c in RESULTS if not c]
    print("preflight+determinism selftests: %d/%d passed" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


def _semantic(rep):
    """Strip nondeterministic/path-dependent fields so ordering can be compared.

    Two runs use different temporary corpora, so the selected-manifest digest and
    reproduction env embed different absolute paths; those are not ordering
    effects. What must be order-independent is the semantic verdict content:
    counts, per-test status/reason/phases, and conformance decisions.
    """
    r = dict(rep)
    r.pop("timestamp", None)
    dig = dict(r.get("inputDigests", {}))
    dig.pop("manifests", None)  # embeds temp corpus absolute paths
    r["inputDigests"] = dig
    r["results"] = [{k: v for k, v in res.items() if k != "reproduction"} for res in rep["results"]]
    return r


if __name__ == "__main__":
    sys.exit(main())
