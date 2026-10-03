#!/usr/bin/env python3
"""Record the function-type obligations the revised section 6 makes testable today.

`2026.09-draft` deferred function values wholesale, so a function type could be written but nothing
about it was testable except the deferral. The revised `docs/LANGUAGE_SPEC.md` section 6 defines
function types as a real structural type, and the compiler already implements that written-type
surface: a function type may be written wherever another non-deferred type may be written, an
omitted return type names `Unit`, nullability of the function value itself requires parentheses, a
function type participates in signature matching like any other type, and a function type is not a
legal `is`/`as` target.

Every oracle here was captured by running the program and then confirmed by reading the rule it
tests, and none of them needs a function *value*: each program only writes function types in type
positions, so none depends on the phase that produces values and none contradicts the narrow rule
still rejecting bare reads of a declaration.

Most oracles are built around interface implementation matching, because that is the one legal
construct in the current language whose acceptance is decided by function-type *identity* rather than
by assignability:

  * REQ-3300 a function type is legal in parameter and nested result position of a declaration's
    signature, and the spellings of a function type -- omitted versus written `Unit` return, and
    parameter names -- do not change which type it is;
  * REQ-3301 a function type is a legal generic type argument, grouped and ungrouped;
  * REQ-3302 a function type is a legal property and static property type, and a static
    function-typed property needs no initializer because it starts at the reference zero value;
  * REQ-3303 `null` is not assignable to a non-null function type;
  * REQ-3304 the types *inside* a function type participate in implementation matching, so an
    implementation differing only inside a nested function type is refused -- a compiler that
    compared function types by outer shape alone would accept that program;
  * REQ-3305 parentheses are required when nullability applies to the function value and name a
    different type from nullability of its result, so `(func(Integer): String)?` and
    `func(Integer): String?` do not match;
  * REQ-3306 a function type is not reifiable, so it may not be the target of `is` or `as` --
    `SOLV-TYPE-025`, the code the section names verbatim.

REQ-3300 through REQ-3305 assert the rejection cases at the diagnostic *family* rather than the exact
code. The section states those rules without naming a code, and the project rule against adopting a
diagnostic from the implementation without normative authority is older and stronger than the
convenience of a tighter pin; the family is what the section's own wording supports. REQ-3306 pins
the code precisely because section 6 supplies one.

`tck/tools/gen36.py` records the same revision's obligations that no test can exercise yet, because
their subject is a function value and no program can produce one.
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

SEC = "6. Functions (function types)"

PLACES = ("A function type may appear wherever another non-deferred type may appear, including as "
          "the type of a local, parameter, return, property, or static property, as a generic type "
          "argument, as the inner type of a nullable type, and in the parameter or return position "
          "of another function type:")
PAREN = "Parentheses are required when nullability applies to the function value itself:"
GROUP_NULLABLE = "(func(Integer): String)?  // nullable function value"
GROUP_RESULT = "func(Integer): String?    // non-null function returning String?"
GROUP_LINE = ("The grouping is part of the written type, not a property a compiler may recover from "
              "source text.")
UNIT_LINE = ("Omitting the return type means `Unit`, exactly as it does for a function declaration, "
             "so `func()` and `func(): Unit` name the same type.")
NAMES_LINE = ("Parameter names do not appear in a function type: parameter names belong to "
              "declarations and have no role in function-type identity or assignability.")
IDENTITY_LINE = ("Two function types are identical when they have the same number of parameters, "
                 "corresponding parameter types are identical, and their return types are identical.")
NULL_LINE = ("`null` is assignable only to nullable types. If `S` is a subtype of `T`, then `S` is "
             "assignable to `T?` and `S?` is assignable to `T?`; `S?` is not assignable to non-null "
             "`T`.")
STATIC_ZERO_LINE = ("Each cell begins at its declared type's zero value \u2014 `0` for every integer "
                    "type, `0.0` for `Float`/`Double`, `false` for `Boolean`, the NUL character "
                    "`'\\0'` for `Character`, and `null` for every reference type \u2014 whether or "
                    "not the declaration supplies an initializer, so a static property needs no "
                    "initializer")
STATIC_INIT_LINE = "A static declaration initializer that is not assignable to the\ndeclared type is " \
                   "`SOLV-TYPE-001`."
IMPL_MATCH_LINE = ("An implementing method must use the same parameter types and a covariant return "
                   "type.")
REIFIABLE = ("Function types are not reifiable. A function type used as the target of `is` or `as` "
             "is `SOLV-TYPE-025`. A null check may still refine a nullable function type.")

REQS_SPEC = {
    "REQ-3300": dict(
        section=SEC,
        summary="A function type is legal in parameter and nested result position of a declaration "
                "signature, and the spellings of a function type -- omitted versus written `Unit` "
                "return, and parameter names -- do not change which type it is",
        kind="compile-time",
        quotes=[PLACES, UNIT_LINE, NAMES_LINE, IDENTITY_LINE],
        note="Two halves, one per test. SOL-TCK-0417 declares static functions whose signatures "
             "carry function types in parameter position in each written spelling and in a nested "
             "result position, and forwards through them, so the written types resolve and the "
             "declarations type-check. SOL-TCK-0422 writes an interface signature with "
             "`func(): Unit` and parameter name `basic` and an implementation with `func()` and a "
             "different parameter name; the implementation is accepted, so the two spellings name one "
             "function type and parameter names played no part in matching."),
    "REQ-3301": dict(
        section=SEC,
        summary="A function type is a legal generic type argument, ungrouped and grouped, "
                "non-nullable and nullable",
        kind="compile-time",
        quotes=[PLACES, PAREN, GROUP_NULLABLE],
        note="Three collections are declared with function-typed elements -- a `List` of function "
             "values, a `List` of nullable function values written in the parenthesized spelling, "
             "and a `Map` whose value type is a function type. Each is empty, so each size prints 0."),
    "REQ-3302": dict(
        section=SEC,
        summary="A function type is a legal declared type for a static property, in the "
                "parenthesized nullable spelling and the nullable-result spelling, and a static "
                "function-typed property needs no initializer because it starts at `null`",
        kind="compile-time",
        quotes=[PLACES, PAREN, GROUP_NULLABLE, GROUP_RESULT, STATIC_ZERO_LINE],
        note="A class declares static properties of parenthesized nullable function type -- one "
             "explicitly initialized to `null` and one with no initializer, which the zero-value "
             "rule makes `null` as well -- and one static property of nullable-result function type "
             "with no initializer. All three declarations are accepted. The program never binds a "
             "declaration as a value, so it exercises no rule about producing one."),
    "REQ-3303": dict(
        section=SEC,
        summary="`null` is not assignable to a non-null function type, so a static function-typed "
                "property initialized to `null` is the ordinary assignment failure",
        kind="compile-time",
        quotes=[NULL_LINE, STATIC_INIT_LINE],
        note="A non-null function-typed static property is initialized to `null`. A function type "
             "written without `?` is non-null like every other written type, so this is the "
             "non-assignable static initializer, and section 7's sentence names the code verbatim so "
             "it is pinned; the sentinel proves non-execution."),
    "REQ-3304": dict(
        section=SEC,
        summary="The parameter and result types inside a function type participate in interface "
                "implementation matching, so an implementation differing only inside a nested "
                "function type is refused",
        kind="compile-time",
        quotes=[IDENTITY_LINE, IMPL_MATCH_LINE, PLACES],
        note="The interface's parameter is `func(): func(Integer): String` and the implementation "
             "declares `func(): func(): String` -- the same outer arity, the same outer result, the "
             "same name, differing only in the arity of the function type written inside the "
             "parameter. Section 12 requires the same parameter types and section 6 defines identity "
             "structurally through the nested constructors, so the implementation does not match. The "
             "section names no code for that failure, so the rejection is asserted as the semantic "
             "family and the sentinel proves non-execution."),
    "REQ-3305": dict(
        section=SEC,
        summary="Nullability of the function value requires parentheses and names a different type "
                "from nullability of its result, so `(func(Integer): String)?` and "
                "`func(Integer): String?` do not match",
        kind="compile-time",
        quotes=[PAREN, GROUP_NULLABLE, GROUP_RESULT, GROUP_LINE, IDENTITY_LINE, IMPL_MATCH_LINE],
        note="The interface's parameter is a nullable function value and the implementation declares "
             "a function returning a nullable `String` -- the two spellings the section contrasts, "
             "placed in one pair. The implementation does not match, so the grouping is part of the "
             "type and not recoverable from text, and the failure is asserted as the semantic family "
             "because no code is named for it; the sentinel proves non-execution."),
    "REQ-3306": dict(
        section="6. Functions (type tests, casts, and other constructs)",
        summary="A function type is not reifiable, so writing one as the target of `is` or of `as` is "
                "`TYPE_INVALID_TYPE_OPERAND` (`SOLV-TYPE-025`)",
        kind="compile-time",
        quotes=[REIFIABLE, PLACES],
        note="Two tests, one per construct, each written against an operand that is not a function "
             "value so the program needs none: `is` on an `Integer` and `as` on an `Any` holding an "
             "`Integer`. The section states the prohibition as applying to the written target, so an "
             "implementation that rejected only after checking operand compatibility would still "
             "satisfy it, and one that allowed either construct would not. Section 6 names the code "
             "verbatim, so both tests pin it; the sentinel proves non-execution.",
        diagnosticCode="SOLV-TYPE-025", diagnosticNormative=True,
    ),
}

TESTS = [
    dict(
        tid="SOL-TCK-0417", req="REQ-3300", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0, "stdoutBase64": base64.b64encode(b"0417").decode("ascii")},
        note="Static functions carry function types in parameter and nested result position in each "
             "written spelling and forward through them, so every position resolves.",
        src="""class Holder {
    static func forward(callback: func(Integer): String): func(Integer): String {
        return Holder.pass(callback)
    }

    static func pass(callback: func(Integer): String): func(Integer): String {
        return callback
    }

    static func forwardUnit(basic: func()): Unit {
        Holder.run(basic)
    }

    static func run(basic: func()) {
    }

    static func forwardNullable(nullableResult: func(Integer): String?): func(Integer): String? {
        return nullableResult
    }
}
print("0417")
""",
    ),
    dict(
        tid="SOL-TCK-0418", req="REQ-3301", cat="collections", outcome="SUCCESS",
        exp={"languageExit": 0, "stdoutBase64": base64.b64encode(b"000").decode("ascii")},
        note="A `List`, a `List` of parenthesized nullable function values, and a `Map` with "
             "function-typed values are declared; each is empty, so each size prints 0.",
        src="""val callbacks: List<func(Integer): String> = List()
val optionalCallbacks: List<(func(Integer): String)?> = List()
val factories: Map<String, func(Integer): String> = Map()
print(callbacks.size)
print(optionalCallbacks.size)
print(factories.size)
""",
    ),
    dict(
        tid="SOL-TCK-0419", req="REQ-3302", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0, "stdoutBase64": base64.b64encode(b"0419").decode("ascii")},
        note="Static properties of parenthesized nullable function type and of nullable-result "
             "function type are declared, one initialized to `null` and two with no initializer.",
        src="""class Holder {
    static val operation: (func(Integer): String)? = null
    static val sharedOperation: (func(Integer): String)?
    static val nullableResult: func(Integer): String?
}
print("0419")
""",
    ),
    dict(
        tid="SOL-TCK-0420", req="REQ-3303", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-001"}},
        note="A non-null function-typed static property is initialized to `null`, which the non-null "
             "assignment rule refuses and section 7 pins to the assignment diagnostic.",
        src="""class Holder {
    static val operation: func(Integer): String = null
}
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0421", req="REQ-3304", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "SEM"}},
        note="The implementation's parameter differs from the interface's only inside the nested "
             "function type, so the parameter types are not the same and the implementation fails.",
        src="""interface Factory {
    func use(builder: func(): func(Integer): String): String
}

class Simple implements Factory {
    func use(builder: func(): func(): String): String {
        return "s"
    }
}
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0422", req="REQ-3300", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0, "stdoutBase64": base64.b64encode(b"0422").decode("ascii")},
        note="The interface writes `func(): Unit` and parameter name `basic`; the implementation "
             "writes `func()` under a different parameter name and matches, so both spellings name "
             "one function type.",
        src="""interface Scheduler {
    func run(basic: func(): Unit): Unit

    func describe(task: func(Integer): String): func(Integer): String {
        return task
    }
}

class Job implements Scheduler {
    func run(ignored: func()) {
    }

    func describe(callback: func(Integer): String): func(Integer): String {
        return callback
    }
}
print("0422")
""",
    ),
    dict(
        tid="SOL-TCK-0423", req="REQ-3305", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "SEM"}},
        note="The interface's parameter is a nullable function value and the implementation's is a "
             "function returning a nullable `String`; the two groupings name different types, so the "
             "implementation fails.",
        src="""interface Holder {
    func take(operation: (func(Integer): String)?): String
}

class Simple implements Holder {
    func take(operation: func(Integer): String?): String {
        return "s"
    }
}
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0424", req="REQ-3306", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-025"}},
        note="An `Integer` is tested against a function type; the written target is the defect the "
             "section names, so the rejection is the non-reifiable-target diagnostic.",
        src="""val value: Integer = 1
if (value is func(Integer): String) {
    print("matched")
}
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0425", req="REQ-3306", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-025"}},
        note="An `Any` holding an `Integer` is cast to a function type, which the non-reifiability "
             "rule forbids independently of the operand's runtime value.",
        src="""val value: Any = 1
val operation = value as func(Integer): String
print("EXECUTED-INVALID")
""",
    ),
]


def verify():
    bad = []
    for rid, spec in sorted(REQS_SPEC.items()):
        if len(spec["quotes"]) < 2:
            bad.append(("THIN", rid, "a requirement must quote at least two sentences"))
        for q in spec["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append(("QUOTE", rid, q[:80]))
    for t in TESTS:
        if t["req"] not in REQS_SPEC:
            bad.append(("REQ", t["tid"], t["req"]))
        if "print(" not in t["src"]:
            bad.append(("NO-OBSERVABLE", t["tid"], ""))
        if t["outcome"] == "COMPILE_ERROR" and "EXECUTED-INVALID" not in t["src"]:
            bad.append(("NO-SENTINEL", t["tid"], ""))
    seen = {}
    for t in TESTS:
        payload = re.sub(r"\s+", "", t["src"])
        if payload in seen:
            bad.append(("DUP", t["tid"], seen[payload]))
        seen[payload] = t["tid"]
    return bad


def main():
    bad = verify()
    if bad:
        for kind, rid, detail in bad:
            print("%s %s: %s" % (kind, rid, detail))
        return 1
    byreq = {}
    for t in TESTS:
        byreq.setdefault(t["req"], []).append(t["tid"])

    data = json.load(open(REQUIREMENTS, encoding="utf-8"))
    have = {r["id"] for r in data["requirements"]}
    byid = {r["id"]: r for r in data["requirements"]}
    for rid, spec in sorted(REQS_SPEC.items()):
        assert byreq.get(rid), "%s has no test" % rid
        record = {
            "id": rid, "specVersion": SPEC_VERSION, "section": spec["section"],
            "summary": spec["summary"], "kind": spec["kind"], "profile": "full-language",
            "portable": True, "tests": sorted(byreq[rid]), "status": "tested",
            "lifecycle": "active", "oracleNotes": spec["note"], "normativeQuotes": spec["quotes"]}
        if spec.get("diagnosticCode"):
            record["diagnosticCode"] = spec["diagnosticCode"]
            record["diagnosticNormative"] = spec["diagnosticNormative"]
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
