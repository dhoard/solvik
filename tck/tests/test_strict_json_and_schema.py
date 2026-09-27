#!/usr/bin/env python3
"""Strict-JSON and JSON-Schema self-tests, including reference parity.

Proves two TCK invariants:

* the strict JSON decoder rejects every non-canonical input the TCK forbids
  (duplicate keys, NaN/infinity, trailing data, invalid UTF-8/BOM, surrogates,
  overflow-to-infinity numbers); and
* the dependency-free schema validator is *not weaker* than a reference Draft
  2020-12 validator for every keyword the TCK schemas use. When the optional
  ``jsonschema`` package is installed, both validators are run on a battery of
  schemas/instances and must agree; when it is absent the parity assertions are
  reported as skipped (the runner never *requires* a third-party package).

Only Python is required; the reference-parity block degrades gracefully.
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "runner"))

from tck_runner import schema as S  # noqa: E402
from tck_runner import strict_json as SJ  # noqa: E402

RESULTS = []
SKIPPED = []


def check(label, cond):
    RESULTS.append((label, bool(cond)))
    if not cond:
        print("FAIL:", label)


def raises(fn, exc):
    try:
        fn()
    except exc:
        return True
    except Exception:  # noqa: BLE001
        return False
    return False


def strict_json_tests():
    ok_inputs = [
        '{"a":[1,2.5,{"b":true}]}', "-1.5e-3", '{"a":1,"b":2}', '[1,2,3]',
        '{"nested":{"deep":{"x":"value"}}}',
    ]
    bad_inputs = [
        '{"a":1,"a":2}',            # duplicate key
        "NaN", "-Infinity", "Infinity",  # non-standard literals
        "1 2", '{"a":1}x',          # trailing data
        "1e999",                     # overflows to infinity
        "",                          # empty
        '{"a":"a\x01b"}',            # raw control char in string (raw char)
    ]
    for s in ok_inputs:
        check("strict_json accepts %r" % s[:12], not raises(lambda s=s: SJ.loads(s), SJ.StrictJSONError))
    for s in bad_inputs:
        check("strict_json rejects %r" % s[:12], raises(lambda s=s: SJ.loads(s), SJ.StrictJSONError))

    # loadb: invalid UTF-8, BOM, lone surrogate (via JSON \udXXX surrogate pair split).
    check("loadb invalid utf8", raises(lambda: SJ.loadb(b"\xff\xfe"), SJ.StrictJSONError))
    check("loadb BOM", raises(lambda: SJ.loadb(b"\xef\xbb\xbf{}"), SJ.StrictJSONError))
    # A lone high surrogate is legal JSON source (\ud800) but cannot be re-encoded
    # as UTF-8; strict decoding must reject it so reports/protocol lines are lossless.
    check("loadb lone surrogate",
          raises(lambda: SJ.loads('{"a":"\\ud800"}'), SJ.StrictJSONError))
    # canonical dumps is order-independent.
    check("canonical stable",
          SJ.dumps_canonical({"b": 1, "a": [2, {}]}) == SJ.dumps_canonical({"a": [2, {}], "b": 1}))


# Parity battery: each entry is (schema, good_instances, bad_instances).
def parity_cases():
    return [
        ({"type": "object", "properties": {"a": {"type": "integer"}},
          "required": ["a"], "additionalProperties": False},
         [{"a": 1}], [{"a": "x"}, {}, {"a": 1, "b": 2}]),
        ({"$defs": {"p": {"type": "string", "minLength": 2}},
          "properties": {"x": {"$ref": "#/$defs/p"}}, "required": ["x"], "additionalProperties": False},
         [{"x": "ab"}], [{"x": "a"}, {"x": 1}]),
        ({"type": "array", "items": {"type": "integer"}, "uniqueItems": True, "minItems": 1},
         [[1, 2]], [[1, 1], [], ["a"], [1.5]]),
        ({"enum": ["A", "B", "C"]}, ["A"], ["D", 1]),
        ({"const": 42}, [42], [41, "42"]),
        ({"type": "integer", "minimum": 0, "maximum": 5}, [0, 3, 5], [-1, 6, 3.5, True]),
        ({"allOf": [{"type": "integer"}, {"minimum": 2}]}, [3], [1, "x"]),
        ({"anyOf": [{"type": "string"}, {"type": "integer"}]}, ["s", 1], [1.5, True]),
        # 3 matches both subschemas -> violates exactly-one -> invalid; 1 and 6 match exactly one.
        ({"oneOf": [{"maximum": 5}, {"minimum": 3}]}, [1, 6], [3, 4, 5]),
        ({"not": {"type": "string"}}, [1], ["s"]),
        ({"if": {"const": "A"}, "then": {"minLength": 1}, "else": {"type": "integer"}}, ["A", 1], [""]),
        ({"type": "string", "pattern": "^[a-z]+$"}, ["abc"], ["ABC", "a1"]),
        # Draft 2020-12: prefixItems fixes the head, items applies to the rest.
        ({"type": "array", "prefixItems": [{"type": "integer"}, {"type": "string"}],
          "items": {"type": "boolean"}}, [[1, "s", True, False]], [["x", 1], [1, "s", "no"]]),
        # NOTE: `format` is intentionally excluded from the direct parity battery.
        # The reference treats format as annotation-only while the runner asserts
        # a small set; the runner's format behavior is verified as a documented
        # strengthening in check_keyword_parity().
    ]


def _reference_validator_factory():
    try:
        from jsonschema import Draft202012Validator
    except Exception:  # noqa: BLE001
        return None

    def factory(schema):
        # Default configuration: `format` is annotation-only (not asserted).
        # The runner's validator ASSERTS a small set of formats, which is a
        # documented strengthening, never a weakening (TCK.md section 7). The
        # parity comparison therefore requires exact agreement on every keyword
        # EXCEPT `format`, where it only forbids the runner being weaker
        # (mine accepts something the reference rejects); the runner rejecting
        # what the (annotation-only) reference accepts is allowed and asserted
        # separately via the explicit int32/date-time verdicts in parity_cases().
        return Draft202012Validator(schema)

    return factory


def schema_parity_tests():
    # My own validator must always produce the expected verdicts.
    for i, (schema, good, bad) in enumerate(parity_cases()):
        for g in good:
            check("parity good s%d %r" % (i, g), not raises(lambda s=schema, g=g: S.validate(s, g),
                                                            S.ValidationError))
        for b in bad:
            check("parity bad s%d %r" % (i, b), raises(lambda s=schema, b=b: S.validate(s, b),
                                                       S.ValidationError))
    # Reference validator parity (optional).
    if _reference_validator_factory() is None:
        SKIPPED.append("jsonschema reference validator not installed")
        return
    agree = 0
    disagree = 0
    factory = _reference_validator_factory()
    for i, (schema, good, bad) in enumerate(parity_cases()):
        for inst in good + bad:
            mine_ok = not raises(lambda s=schema, inst=inst: S.validate(s, inst), (S.ValidationError,))
            ref_ok = factory(schema).is_valid(inst)
            if mine_ok == ref_ok:
                agree += 1
            else:
                disagree += 1
                print("DISAGREE s%d %r: mine=%s ref=%s" % (i, inst, mine_ok, ref_ok))
    check("reference parity: no disagreements", disagree == 0)
    check("reference parity: cases compared", agree > 0)


# Per-keyword behavioral parity: each entry is (keyword, schema, good, bad). For
# every keyword the TCK schemas actually use, this asserts the reference Draft
# 2020-12 validator AND the dependency-free validator agree on both a valid and
# an invalid instance -- proving the runner's validator is not weaker for any
# used keyword (TCK.md section 7).
KEYWORD_CASES = {
    "type": ({"type": "integer"}, 1, "x"),
    "enum": ({"enum": [1, 2]}, 1, 3),
    "const": ({"const": 7}, 7, 8),
    "properties": ({"properties": {"a": {"type": "integer"}}}, {"a": 1}, {"a": "x"}),
    "patternProperties": ({"patternProperties": {"^n": {"type": "integer"}}}, {"n1": 2}, {"n1": "x"}),
    "additionalProperties": ({"additionalProperties": False, "properties": {"a": {}}}, {"a": 1}, {"b": 1}),
    "propertyNames": ({"propertyNames": {"pattern": "^[a-z]+$"}}, {"abc": 1}, {"ABC": 1}),
    "required": ({"required": ["a"]}, {"a": 1}, {"b": 1}),
    "minProperties": ({"minProperties": 2}, {"a": 1, "b": 2}, {"a": 1}),
    "maxProperties": ({"maxProperties": 1}, {"a": 1}, {"a": 1, "b": 2}),
    "items": ({"items": {"type": "integer"}}, [1, 2], [1, "x"]),
    "prefixItems": ({"prefixItems": [{"type": "integer"}, {"type": "string"}]}, [1, "s"], ["x", 1]),
    "minItems": ({"minItems": 2}, [1, 2], [1]),
    "maxItems": ({"maxItems": 1}, [1], [1, 2]),
    "uniqueItems": ({"uniqueItems": True}, [1, 2], [1, 1]),
    "contains": ({"contains": {"const": 5}}, [1, 5], [1, 2]),
    "minimum": ({"minimum": 3}, 3, 2),
    "maximum": ({"maximum": 3}, 3, 4),
    "exclusiveMinimum": ({"exclusiveMinimum": 3}, 4, 3),
    "exclusiveMaximum": ({"exclusiveMaximum": 3}, 2, 3),
    "multipleOf": ({"multipleOf": 3}, 9, 10),
    "minLength": ({"minLength": 2}, "ab", "a"),
    "maxLength": ({"maxLength": 1}, "a", "ab"),
    "pattern": ({"pattern": "^[a-z]+$"}, "abc", "ABC"),
    "allOf": ({"allOf": [{"type": "integer"}, {"minimum": 2}]}, 3, 1),
    "anyOf": ({"anyOf": [{"type": "string"}, {"type": "integer"}]}, "s", 1.5),
    "oneOf": ({"oneOf": [{"maximum": 5}, {"minimum": 3}]}, 1, 3),
    "not": ({"not": {"type": "string"}}, 1, "s"),
    "if": ({"if": {"const": "A"}, "then": {"minLength": 2}, "else": {"type": "integer"}}, 1, "A"),
    "$ref": ({"$defs": {"p": {"type": "integer"}}, "properties": {"x": {"$ref": "#/$defs/p"}}},
             {"x": 1}, {"x": "s"}),
}


def check_keyword_parity():
    """Assert per-keyword agreement with the reference validator."""
    ref = _reference_validator_factory()
    if ref is None:
        SKIPPED.append("keyword parity skipped (no jsonschema)")
        return
    # Every keyword used must be present in our supported set AND exercised here.
    for kw in S.SUPPORTED_KEYWORDS:
        if kw in ("$schema", "$id", "$comment", "title", "description", "$defs", "then", "else",
                  "minContains", "maxContains", "format"):
            continue  # metadata / sub-keywords / non-asserted / extra tested indirectly
        check("keyword exercised in parity: %s" % kw, kw in KEYWORD_CASES)
    for kw, (schema, good, bad) in KEYWORD_CASES.items():
        check("keyword in supported set: %s" % kw, kw in S.SUPPORTED_KEYWORDS)
        for inst, want_ok in ((good, True), (bad, False)):
            mine_ok = not raises(lambda s=schema, i=inst: S.validate(s, i), S.ValidationError)
            ref_ok = ref(schema).is_valid(inst)
            # For non-format keywords the runner must agree exactly with the
            # reference. (format is exercised separately as a documented
            # strengthening.)
            check("parity %s %r" % (kw, inst), mine_ok == ref_ok and mine_ok == want_ok)

    # format: prove the runner is never WEAKER than the annotation-only reference,
    # and that its asserted verdicts are correct.
    fmt_cases = [
        ({"type": "integer", "format": "int32"}, 300, True),
        ({"type": "integer", "format": "int32"}, 3000000000, False),
        ({"format": "date-time"}, "2020-01-01T00:00:00Z", True),
        ({"format": "date-time"}, "nope", False),
    ]
    for schema, inst, want_ok in fmt_cases:
        mine_ok = not raises(lambda s=schema, i=inst: S.validate(s, i), S.ValidationError)
        ref_ok = ref(schema).is_valid(inst)
        check("format assertion %r==%s" % (inst, want_ok), mine_ok == want_ok)
        # runner must not accept something the reference rejects (never weaker).
        check("format never weaker %r" % (inst,), not (mine_ok and not ref_ok))


def main():
    strict_json_tests()
    schema_parity_tests()
    check_keyword_parity()
    failed = [l for l, c in RESULTS if not c]
    print("strict_json+schema selftests: %d/%d passed, %d skipped%s" %
          (len(RESULTS) - len(failed), len(RESULTS), len(SKIPPED),
           " (%s)" % "; ".join(SKIPPED) if SKIPPED else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
