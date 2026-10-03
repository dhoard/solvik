#!/usr/bin/env python3
"""Manifest rejection and inventory/traceability self-tests (TCK.md 6, 7).

Covers the corpus-level rejections a per-document schema cannot express:
duplicate test/requirement identifiers, missing sources/fixtures, incompatible
spec versions, profile/status relabeling, impossible expectation combinations,
unknown outcomes, closed-object violations, path escapes, cyclic/empty/missing
profiles, and coverage gaps. Only Python is required.
"""

import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "runner"))

from tck_runner import (  # noqa: E402
    inventory as INV,
    manifests as MF,
    schema as S,
    strict_json as SJ,
)

RESULTS = []
MANIFEST_SCHEMA = SJ.loads(open(os.path.join(HERE, "..", "schemas", "manifest-1.schema.json")).read())
REQ_SCHEMA = SJ.loads(open(os.path.join(HERE, "..", "schemas", "requirements-1.schema.json")).read())
PROF_SCHEMA = SJ.loads(open(os.path.join(HERE, "..", "schemas", "profile-1.schema.json")).read())


def check(label, cond):
    RESULTS.append((label, bool(cond)))
    if not cond:
        print("FAIL:", label)


def raises(fn, exc):
    try:
        fn()
    except exc:
        return True
    except Exception as e:  # noqa: BLE001
        print("  (wrong exc %s)" % type(e).__name__)
        return False
    return False


def base_manifest(outcome="SUCCESS", exp=None, **over):
    m = {
        "manifestSchemaVersion": 1, "specVersion": "2026.11-draft", "testId": "SOL-TCK-0001",
        "category": "numerics", "profile": "full-language", "status": "required",
        "requirements": ["REQ-0001"], "entryPoint": "main.sol",
        "outcome": outcome, "expectation": exp if exp is not None else {"languageExit": 0},
    }
    m.update(over)
    return m


def valid_model(req_ids=("REQ-0001",)):
    by_id = {r: {"id": r, "lifecycle": "active", "profile": "full-language",
                 "tests": ["SOL-TCK-0001"]} for r in req_ids}
    return {"by_id": by_id, "closures": {"full-language": set(req_ids)},
            "full_profile": "full-language", "spec_version": "2026.11-draft",
            "inventoryDigest": "a" * 64, "requiredCapabilities": ["compile-only"],
            "requirementGaps": [], "coverage": {}, "ambiguities": []}


def corpus_with(manifests):
    """materialize {testId/manifest.json + main.sol} into a temp corpus dir."""
    root = tempfile.mkdtemp(prefix="tck-mc-")
    for i, m in enumerate(manifests):
        # Distinct directory per manifest so two manifests may legitimately share
        # a testId in the file system, letting duplicate-identifier detection run.
        d = os.path.join(root, "case-%02d" % i)
        os.makedirs(d, exist_ok=True)
        open(os.path.join(d, "main.sol"), "w").write("println(1)\n")
        open(os.path.join(d, "m.manifest.json"), "w").write(SJ.dumps_canonical(m))
    return root


def manifest_schema_tests():
    vok = lambda m: not raises(lambda m=m: S.validate(MANIFEST_SCHEMA, m), S.ValidationError)
    viok = lambda m: raises(lambda m=m: S.validate(MANIFEST_SCHEMA, m), S.ValidationError)
    check("schema accepts SUCCESS", vok(base_manifest("SUCCESS", {"languageExit": 0, "stdoutBase64": "aGkK"})))
    check("schema success needs exit", viok(base_manifest("SUCCESS", {"stdoutBase64": "aGkK"})))
    check("schema success forbids runtime", viok(base_manifest("SUCCESS", {"languageExit": 0, "runtimeCategory": "ARITHMETIC_ERROR"})))
    check("schema cerror needs diag", viok(base_manifest("COMPILE_ERROR", {})))
    check("schema cerror forbids exit", viok(base_manifest("COMPILE_ERROR", {"diagnostic": {"code": "SOLV-TYPE-009"}, "languageExit": 1})))
    check("schema rterr needs cat", viok(base_manifest("RUNTIME_ERROR", {})))
    check("schema rterr forbids exit", viok(base_manifest("RUNTIME_ERROR", {"runtimeCategory": "ARITHMETIC_ERROR", "languageExit": 1})))
    check("schema closed object", viok({**base_manifest(), "extra": 1}))
    check("schema closed expectation", viok(base_manifest("SUCCESS", {"languageExit": 0, "nope": 1})))
    check("schema bad outcome", viok({**base_manifest(), "outcome": "BOGUS"}))
    check("schema bad testId", viok({**base_manifest(), "testId": "bad-id"}))
    check("schema bad reqId", viok(base_manifest(requirements=["NOPE"])))
    check("schema abs entry", viok({**base_manifest(), "entryPoint": "/etc/passwd"}))
    check("schema dotdot entry", viok({**base_manifest(), "entryPoint": "../x.sol"}))
    check("schema bad transform", viok({**base_manifest("COMPILE_SUCCESS", {}), "normalization": [{"field": "stdout", "transform": "regex"}]}))
    check("schema good transform", vok({**base_manifest("COMPILE_SUCCESS", {}), "normalization": [{"field": "stdout", "transform": "platform-line-separator"}]}))


def corpus_level_tests():
    model = valid_model()
    # duplicate test ids
    dup = corpus_with([base_manifest(), dict(base_manifest())])
    check("dup test ids rejected", raises(lambda: MF.load_and_validate(dup, MANIFEST_SCHEMA, model, "2026.11-draft"), MF.ManifestError))
    # missing entry point
    root = tempfile.mkdtemp()
    d = os.path.join(root, "SOL-TCK-0001"); os.makedirs(d)
    open(os.path.join(d, "SOL-TCK-0001.manifest.json"), "w").write(SJ.dumps_canonical(base_manifest()))
    check("missing source rejected", raises(lambda: MF.load_and_validate(root, MANIFEST_SCHEMA, model, "2026.11-draft"), MF.ManifestError))
    # bad spec version
    check("incompatible spec version rejected",
          raises(lambda: MF.load_and_validate(corpus_with([base_manifest()]), MANIFEST_SCHEMA, model, "9999"),
                 MF.ManifestError))
    # unknown requirement in manifest (schema allows REQ pattern; inventory rejects)
    check("unknown requirement rejected",
          raises(lambda: MF.load_and_validate(
              corpus_with([base_manifest(requirements=["REQ-9999"])]), MANIFEST_SCHEMA, model, "2026.11-draft"),
              MF.ManifestError))
    # relabel required as optional
    check("relabel optional rejected",
          raises(lambda: MF.load_and_validate(
              corpus_with([base_manifest(status="optional")]), MANIFEST_SCHEMA, model, "2026.11-draft"),
              MF.ManifestError))
    # good corpus validates and digests deterministically
    good = corpus_with([base_manifest(),
                        dict(base_manifest(), testId="SOL-TCK-0002")])
    ms = MF.load_and_validate(good, MANIFEST_SCHEMA, valid_model(("REQ-0001",)), "2026.11-draft")
    check("good corpus loads 2", len(ms) == 2)
    # duplicate detection needs two distinct ids to load first; then a same-id set:
    dup2 = corpus_with([base_manifest(), dict(base_manifest(), testId="SOL-TCK-0001")])
    check("same-id two manifests rejected", raises(lambda: MF.load_and_validate(dup2, MANIFEST_SCHEMA, model, "2026.11-draft"), MF.ManifestError))


def linkage_tests():
    """`validate_test_linkage` must reject a requirement citing an absent test.

    The shipped corpus always links cleanly, so without these cases the guard's error
    path would never be executed by the build and a regression that made it a no-op
    would go unnoticed. A requirement whose declared test was renamed or deleted is a
    false-coverage defect: `validate` would still report the requirement as tested.
    """
    ok = valid_model()                                   # lists SOL-TCK-0001
    check("linkage accepts an existing test",
          not raises(lambda: INV.validate_test_linkage(ok, {"SOL-TCK-0001"}),
                     INV.InventoryError))
    stale = valid_model()
    stale["by_id"]["REQ-0001"]["tests"] = ["SOL-TCK-9999"]
    check("linkage rejects a requirement citing a missing test",
          raises(lambda: INV.validate_test_linkage(stale, {"SOL-TCK-0001"}),
                 INV.InventoryError))
    # A retired requirement must not block validation over tests that were removed
    # with it; only active coverage claims are audited.
    retired = valid_model()
    retired["by_id"]["REQ-0001"]["lifecycle"] = "retired"
    retired["by_id"]["REQ-0001"]["tests"] = ["SOL-TCK-9999"]
    check("linkage ignores non-active requirements",
          not raises(lambda: INV.validate_test_linkage(retired, set()),
                     INV.InventoryError))
    # An empty test list is a coverage *gap*, reported by untested_requirements; it is
    # not a linkage error, otherwise every not-yet-tested requirement would abort
    # validation instead of appearing in the gap list.
    empty = valid_model()
    empty["by_id"]["REQ-0001"]["tests"] = []
    check("linkage tolerates an empty test list (a gap, not a broken link)",
          not raises(lambda: INV.validate_test_linkage(empty, set()),
                     INV.InventoryError))
    check("an empty test list is reported as a gap",
          INV.untested_requirements(empty) == ["REQ-0001"])


def inventory_tests():
    def req(rid="REQ-0001", profile="full-language", tests=None, status="tested",
            life="active", portable=True, **over):
        r = {"id": rid, "specVersion": "2026.11-draft", "section": "4",
             "summary": "a requirement that does something meaningful",
             "kind": "compile-time", "profile": profile, "portable": portable,
             "tests": tests if tests is not None else ["SOL-TCK-0001"],
             "status": status, "lifecycle": life,
             "oracleNotes": "derived from LANGUAGE_SPEC section 4",
             "normativeQuotes": [
                 "Concatenation binds looser than arithmetic, so a + b .. c is "
                 "(a + b) .. c, and it is left-associative."]}
        r.update(over)
        return r

    def write(reqs):
        doc = {"schemaVersion": 1, "specVersion": "2026.11-draft", "requirements": reqs}
        p = tempfile.mktemp(suffix=".json")
        open(p, "w").write(SJ.dumps_canonical(doc))
        return p

    prof_full = {"full-language": {"schemaVersion": 1, "specVersion": "2026.11-draft",
                                   "name": "full-language", "kind": "full",
                                   "requirements": ["REQ-0001"], "capabilities": []}}
    inv = INV.load_inventory(write([req()]), REQ_SCHEMA)
    check("inventory loads", inv["by_id"]["REQ-0001"]["id"] == "REQ-0001")
    check("inventory validates", INV.validate(inv, prof_full, "2026.11-draft")["full_profile"] == "full-language")
    check("duplicate req rejected", raises(lambda: INV.load_inventory(write([req(), req()]), REQ_SCHEMA), INV.InventoryError))
    # Oracle-source discipline (TCK.md section 6.1): every requirement must carry the
    # normative passages its oracle was derived from, so a fabricated or missing source
    # is a schema-level error rather than a review-time oversight.
    no_quotes = req()
    del no_quotes["normativeQuotes"]
    check("requirement without normativeQuotes rejected",
          raises(lambda: INV.load_inventory(write([no_quotes]), REQ_SCHEMA), INV.InventoryError))
    check("requirement with empty normativeQuotes rejected",
          raises(lambda: INV.load_inventory(write([req(normativeQuotes=[])]), REQ_SCHEMA), INV.InventoryError))
    check("requirement with non-substantive quote rejected",
          raises(lambda: INV.load_inventory(write([req(normativeQuotes=["short"])]), REQ_SCHEMA), INV.InventoryError))
    check("empty full profile rejected",
          raises(lambda: INV.validate(inv, {"full-language": {**prof_full["full-language"], "requirements": []}}, "2026.11-draft"), INV.InventoryError))
    check("missing full profile rejected", raises(lambda: INV.validate(inv, {}, "2026.11-draft"), INV.InventoryError))
    cyclic = {"full-language": {**prof_full["full-language"], "extends": ["a"]},
              "a": {"schemaVersion": 1, "specVersion": "2026.11-draft", "name": "a", "kind": "sub",
                    "requirements": [], "capabilities": [], "extends": ["full-language"]}}
    check("cyclic profile rejected", raises(lambda: INV.validate(inv, cyclic, "2026.11-draft"), INV.InventoryError))
    check("unknown profile ref rejected",
          raises(lambda: INV.validate(inv, {"full-language": {**prof_full["full-language"], "extends": ["nope"]}}, "2026.11-draft"), INV.InventoryError))
    # requirement in no profile (valid inventory, empty profile closure)
    prof_no = {"full-language": {**prof_full["full-language"], "requirements": []}}
    check("req in no profile rejected",
          raises(lambda: INV.validate(inv, prof_no, "2026.11-draft"), INV.InventoryError))
    # untested gap listing
    inv3 = INV.load_inventory(write([req(tests=[], status="untested-portable",
                                         rationale="portable but deferred pending oracle")]), REQ_SCHEMA)
    m3 = INV.validate(inv3, prof_full, "2026.11-draft")
    check("gap reported", INV.untested_requirements(m3) == ["REQ-0001"])
    # schema: tested status requires tests
    check("schema tested needs tests",
          raises(lambda: S.validate(REQ_SCHEMA, {"schemaVersion": 1, "specVersion": "2026.11-draft",
                                                 "requirements": [req(tests=[])]}), S.ValidationError))


def main():
    manifest_schema_tests()
    corpus_level_tests()
    inventory_tests()
    linkage_tests()
    failed = [l for l, c in RESULTS if not c]
    print("manifest+inventory selftests: %d/%d passed" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
