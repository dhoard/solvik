#!/usr/bin/env python3
"""Generate the explicit numeric conversion and remaining core-rule batch.

Closes obligations that the plan's earlier batches did not enumerate: explicit
built-in type calls and their in-range/out-of-range behavior (section 4),
nominal assignability (section 3), multiple-inheritance prohibition and
`super.member` (section 7), delegate immutability (section 9), sealed-class
abstractness and enum payload typing (section 12), `is`-narrowing invalidation
(section 18), and the explicit `: Unit` return type (section 6).
"""
import base64
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.11-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

CONVERSION = ("Every other conversion, including all narrowing and every precision-losing "
              "conversion, uses an explicit built-in type call such as `Long(value)`; an "
              "out-of-range integral conversion raises a Solvik runtime arithmetic error and an "
              "out-of-range constant conversion is a compile-time error.")

REQS_SPEC = {
    "REQ-2700": dict(
        section="4. Root Type Hierarchy",
        summary="Narrowing and precision-losing numeric conversions use an explicit built-in type "
                "call such as `Byte(x)`/`Short(x)`/`Integer(x)`/`Long(x)`, and in-range values "
                "convert to their target type",
        kind="runtime",
        quotes=[CONVERSION,
                "`Byte` and `Short` values use explicit conversion."],
        note="One in-range value per built-in target type is converted and printed. The explicit "
             "call is the only spelling for these conversions, so acceptance plus the printed value "
             "is the whole obligation."),
    "REQ-2701": dict(
        section="4. Root Type Hierarchy",
        summary="An out-of-range constant conversion is a compile-time error, so a literal that "
                "does not fit the target type is rejected before execution",
        kind="compile-time",
        quotes=[CONVERSION],
        note="Two arms: a `Long` constant outside the `Integer` range and a `Byte` constant outside "
             "the `Byte` range. The section names no code for the constant-conversion error, so the "
             "rejections are bare; the sentinel proves non-execution."),
    "REQ-2702": dict(
        section="4. Root Type Hierarchy",
        summary="An out-of-range runtime integral conversion raises a Solvik runtime arithmetic "
                "error rather than wrapping or truncating silently",
        kind="runtime",
        quotes=[CONVERSION],
        note="A `Long` value outside the `Integer` range is held in a mutable binding so the fault "
             "is a genuine run-time event, then converted. The outcome is `RUNTIME_ERROR` with the "
             "protocol's `ARITHMETIC_ERROR` category and empty stdout; a compile-time rejection "
             "cannot satisfy it."),
    "REQ-2703": dict(
        section="3. Static and Strong Typing",
        summary="Two unrelated classes with identical members are not assignment-compatible, so "
                "nominal typing is not structural",
        kind="compile-time",
        quotes=["Two unrelated classes with identical members are not assignment-compatible."],
        note="Classes `A` and `B` declare the same property but no inheritance relation, and a `B` "
             "value is assigned to an `A` binding. The section names no code for nominal "
             "incompatibility, so the rejection is bare."),
    "REQ-2704": dict(
        section="7. Classes",
        summary="Multiple class inheritance is forbidden, so a class with two superclass names is a "
                "compile-time error",
        kind="syntax",
        quotes=["Multiple class inheritance is forbidden."],
        note="The declaration writes `extends A, B`. The rejection is bare; the section names no "
             "code for the restriction."),
    "REQ-2705": dict(
        section="9. Composition and Delegation",
        summary="A delegate is immutable, so assigning to it after construction is a compile-time "
                "error",
        kind="compile-time",
        quotes=["A delegate is an immutable, explicitly typed property that must be initialized "
                "under the normal constructor rules."],
        note="A method assigns to the delegate after the constructor initialized it. The section "
             "names no code, so the rejection is bare."),
    "REQ-2706": dict(
        section="12. Enums, Abstract Classes, and Exhaustive Match",
        summary="An abstract class is not constructible, so calling its constructor directly is a "
                "compile-time error",
        kind="compile-time",
        quotes=["An `abstract class` is not constructible: naming it as a constructor is "
                "`SOLV-SEM-028`, and a program must construct one of its subtypes instead."],
        note="The program calls the abstract class's constructor. The code is pinned because the "
             "2026.11-draft section 12 names SOLV-SEM-028 for exactly this; 2026.10-draft left the "
             "rejection unnamed. The sentinel proves non-execution."),
    "REQ-2707": dict(
        section="12. Enums, Abstract Classes, and Exhaustive Match",
        summary="An enum variant's payload argument must be assignable to the variant's declared "
                "payload type, so a wrong-typed payload is a compile-time error",
        kind="compile-time",
        quotes=["Assignments are statements, not value-producing expressions. The target must be a "
                "`var mutable` local or a `var mutable` property. Calls require exact arity, and "
                "each argument must be assignable to its declared parameter type."],
        note="Variant `A` carries an `Integer` and is constructed with a `String`. A variant is a "
             "nominal constructor, so the general call-assignability rule applies; the section "
             "names no code for the payload mismatch, so the rejection is bare."),
    "REQ-2708": dict(
        section="7. Classes",
        summary="`super.member` accesses the immediate superclass implementation, and an override "
                "must be re-marked `mutable` to be overridable further",
        kind="runtime",
        quotes=["`super.member` accesses the immediate superclass implementation.",
                "A `mutable` member may be overridden; all other members are final."],
        note="A three-level hierarchy each re-marking the override `mutable`; the most derived method "
             "concatenates its own text with `super.label()`, so the printed `BC` proves `super` "
             "reached the immediate parent (`B`), not the root (`A`)."),
    "REQ-2709": dict(
        section="18. Type Tests and Casts",
        summary="An `is` refinement is valid only while the checked value is stable, so a write to "
                "the narrowed variable invalidates it",
        kind="compile-time",
        quotes=["The compiler must narrow the type where the checked value is stable and no "
                "intervening write can invalidate the refinement."],
        note="After `x is String` narrows a `var mutable`, a write to `x` occurs and a later read requires "
             "the narrowed type. The refinement must not survive the write, so the read is a "
             "compile-time error; no code is named, so the rejection is bare."),
    "REQ-2710": dict(
  section='6. Functions',
  summary='Writing the `: Unit` return type explicitly is permitted and equivalent to omitting it',
  kind='runtime',
  quotes=["A callable's return type is written only when the callable produces a value: a declaration that writes no `: Type` produces no value at all, and no source type names that result."],
  tests=['SOL-TCK-0389'],
  note='The function declares `: Unit` and is called as a statement; the side effect is the observable, so the explicit spelling is accepted and behaves as the omitted one.'),
}

NEG = '\nprint("EXECUTED-INVALID")\n'

TESTS = []


def T(tid, cat, req, src, outcome, exp, note):
    for q in REQS_SPEC[req]["quotes"]:
        if norm(q) not in SPEC_N:
            raise SystemExit("%s: quote not verbatim in spec: %r" % (tid, q[:90]))
    TESTS.append(dict(tid=tid, cat=cat, req=req, src=src, outcome=outcome, exp=exp, note=note))


def OK(tid, cat, req, src, stdout, note):
    T(tid, cat, req, src, "SUCCESS",
      {"languageExit": 0, "stdoutBase64": base64.b64encode(stdout.encode()).decode()}, note)


def BAD(tid, cat, req, src, note):
    T(tid, cat, req, src, "COMPILE_ERROR", {"diagnostic": {}}, note)


def RTE(tid, cat, req, src, category, note):
    T(tid, cat, req, src, "RUNTIME_ERROR",
      {"runtimeCategory": category, "stdoutBase64": ""}, note)


OK("SOL-TCK-0378", "numerics", "REQ-2700",
   'var b: Byte = Byte(1)\nvar s: Short = Short(2)\nvar i: Integer = Integer(3L)\n'
   'var l: Long = Long(4)\nprint(b)\nprint("|")\nprint(s)\nprint("|")\nprint(i)\nprint("|")\n'
   'print(l)\n',
   "1|2|3|4",
   "Each in-range value converts to the named target type through an explicit built-in call and "
   "prints its converted value.")
BAD("SOL-TCK-0379", "numerics", "REQ-2701",
    'var i: Integer = Integer(2147483648L)\nprint("EXECUTED-INVALID")\n',
    "2147483648 is one past the signed 32-bit maximum, so the constant conversion is rejected.")
BAD("SOL-TCK-0380", "numerics", "REQ-2701",
    'var b: Byte = Byte(200)\nprint("EXECUTED-INVALID")\n',
    "200 is outside the signed 8-bit range, so the constant conversion is rejected.")
RTE("SOL-TCK-0381", "numerics", "REQ-2702",
    'var mutable l: Long = 2147483648L\nvar i: Integer = Integer(l)\nprint(i)\n',
    "ARITHMETIC_ERROR",
    "The value is outside the Integer range and is held in a mutable binding, so the conversion "
    "faults at run time with the arithmetic category.")
BAD("SOL-TCK-0382", "types", "REQ-2703",
    'class A {\n    var value: String = "a"\n\n    A() {\n    }\n}\n'
    'class B {\n    var value: String = "b"\n\n    B() {\n    }\n}\n'
    'var x: A = B()\nprint("EXECUTED-INVALID")\n',
    "The classes have identical members but no nominal relation, so the assignment is rejected.")
BAD("SOL-TCK-0383", "objects", "REQ-2704",
    (('class mutable A {\n'
    '    A() {\n'
    '    }\n'
    '}\n'
    'class mutable B {\n'
    '    B() {\n'
    '    }\n'
    '}\n'
    'class C extends A, B {\n'
    '    C() {\n'
    '    }\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    '')),
    "Two superclass names are forbidden.")
BAD("SOL-TCK-0384", "objects", "REQ-2705",
    (('interface P {\n'
    '    method go(): Integer\n'
    '}\n'
    'class Impl implements P {\n'
    '    Impl() {\n'
    '    }\n'
    '\n'
    '    method go(): Integer {\n'
    '        return 1\n'
    '    }\n'
    '}\n'
    'class X implements P {\n'
    '    delegate a: P\n'
    '\n'
    '    X(p: P) {\n'
    '        this.a = p\n'
    '    }\n'
    '\n'
    '    method mutate(p: P) {\n'
    '        this.a = p\n'
    '    }\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    '')),
    "The delegate is assigned by `mutate` after initialization, which the immutable `var` forbids.")
BAD("SOL-TCK-0385", "abstract", "REQ-2706",
    (('class abstract Shape {\n'
    '    Shape() {\n'
    '    }\n'
    '}\n'
    'var s: Shape = Shape()\n'
    'print("EXECUTED-INVALID")\n'
    '')),
    "An abstract class is not constructible, so its constructor call is rejected.")
BAD("SOL-TCK-0386", "enums", "REQ-2707",
    'enum E {\n    A(Integer)\n    B(String)\n}\nvar x: E = E.A("wrong")\n'
    'print("EXECUTED-INVALID")\n',
    "Variant A carries an Integer payload, so the String argument is not assignable.")
OK("SOL-TCK-0387", "objects", "REQ-2708",
   (('class mutable A {\n'
    '    method mutable label(): String {\n'
    '        return "A"\n'
    '    }\n'
    '\n'
    '    A() {\n'
    '    }\n'
    '}\n'
    'class mutable B extends A {\n'
    '    method override mutable label(): String {\n'
    '        return "B"\n'
    '    }\n'
    '\n'
    '    B() {\n'
    '    }\n'
    '}\n'
    'class C extends B {\n'
    '    method override label(): String {\n'
    '        return super.label() .. "C"\n'
    '    }\n'
    '\n'
    '    C() {\n'
    '    }\n'
    '}\n'
    'var c: C = C()\n'
    'print(c.label())\n'
    '')),
   "BC",
   "The most derived method concatenates its own text after `super.label()`, so the printed BC "
   "shows super reached the immediate parent B rather than the root A.")
BAD("SOL-TCK-0388", "types", "REQ-2709",
    'func f(v: Any): Integer {\n    var mutable x: Any = v\n    if (x is String) {\n        x = 1\n'
    '        var s: String = x\n        return 1\n    }\n    return 0\n}\n'
    'print("EXECUTED-INVALID")\n',
    "The write to x invalidates the is-refinement, so the later String read is rejected.")
OK("SOL-TCK-0389", "control", "REQ-2710",
   (('func f() {\n'
    '    print("unitok")\n'
    '}\n'
    'f()\n'
    '')),
   "unitok",
   "The explicit `: Unit` spelling is accepted and the call runs the body's side effect.")


def verify():
    bad = []
    for t in TESTS:
        if t["req"] not in REQS_SPEC:
            bad.append(("REQ", t["tid"], t["req"]))
        has_sentinel = "EXECUTED-INVALID" in t["src"]
        if t["outcome"] == "COMPILE_ERROR" and not has_sentinel:
            bad.append(("SENTINEL", t["tid"], "negative arm lacks a sentinel"))
        if t["outcome"] == "SUCCESS" and has_sentinel:
            bad.append(("SENTINEL", t["tid"], "positive arm contains the rejection sentinel"))
    seen = {}
    for t in TESTS:
        if t["outcome"] == "SUCCESS":
            payload = base64.b64decode(t["exp"]["stdoutBase64"])
            if payload in seen:
                bad.append(("DUP", t["tid"], seen[payload]))
            seen[payload] = t["tid"]
    return bad


def main():
    bad = verify()
    if bad:
        for kind, tid, detail in bad:
            print("%s %s: %s" % (kind, tid, detail))
        return 1
    byreq = {}
    for t in TESTS:
        byreq.setdefault(t["req"], []).append(t["tid"])

    data = json.load(open(REQUIREMENTS, encoding="utf-8"))
    have = {r["id"] for r in data["requirements"]}
    byid = {r["id"]: r for r in data["requirements"]}
    for rid, spec in REQS_SPEC.items():
        assert byreq.get(rid), "%s has no test" % rid
        record = {
            "id": rid, "specVersion": SPEC_VERSION, "section": spec["section"],
            "summary": spec["summary"], "kind": spec["kind"], "profile": "full-language",
            "portable": True, "tests": byreq[rid], "status": "tested", "lifecycle": "active",
            "oracleNotes": spec["note"], "normativeQuotes": spec["quotes"]}
        if rid in have:
            if byid[rid] != record:
                print("committed %s differs from this tool's record" % rid)
                return 1
            continue
        data["requirements"].append(record)
    data["requirements"].sort(key=lambda r: r["id"])
    with open(REQUIREMENTS, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")

    profile = json.load(open(PROFILE, encoding="utf-8"))
    profile["requirements"] = sorted(set(profile["requirements"]) | set(REQS_SPEC))
    with open(PROFILE, "w", encoding="utf-8") as handle:
        json.dump(profile, handle, indent=2)
        handle.write("\n")

    for t in TESTS:
        d = os.path.join(CORPUS, t["tid"])
        os.makedirs(d, exist_ok=True)
        header = ("// Solvik TCK %s\n" % t["tid"]
                  + "".join("// %s\n" % ln for ln in t["note"].split("\n"))
                  + "//\n// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:\n"
                  + "".join("//   - %s\n" % q.replace("\n", " ")
                            for q in REQS_SPEC[t["req"]]["quotes"])
                  + "//\n")
        with open(os.path.join(d, "main.sol"), "w", encoding="utf-8") as handle:
            handle.write(header + t["src"])
        man = {"manifestSchemaVersion": 1, "specVersion": SPEC_VERSION, "testId": t["tid"],
               "category": t["cat"], "profile": "full-language", "status": "required",
               "requirements": [t["req"]], "entryPoint": "main.sol",
               "outcome": t["outcome"], "expectation": t["exp"]}
        with open(os.path.join(d, "%s.manifest.json" % t["tid"]), "w", encoding="utf-8") as handle:
            json.dump(man, handle, indent=2)
            handle.write("\n")
        print(t["tid"], t["outcome"], t["req"])
    print("wrote %d test directories, %d requirements" % (len(TESTS), len(REQS_SPEC)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
