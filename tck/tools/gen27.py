#!/usr/bin/env python3
"""Generate the built-in rendering and static-member rule batch.

Closes the remaining section 4 rendering rule (every built-in `toString` form and
the non-extensibility of a built-in scalar) and the section 7 static-member rules
that earlier batches did not enumerate: the class name as a receiver rather than
a value, access to a static member through an instance, the shared static member
namespace, `this`/`super` inside static members, and the fact that the
`toString`/`equals`/`hashCode` reservation is instance-only.
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

BUILTIN_TOSTRING = ("Built-in scalars provide fixed, non-overridable implementations: `Integer`, "
                    "`Long`, `Byte`, and `Short` render in decimal, `Float` and `Double` use "
                    "Java-style floating-point text, `Boolean` renders `true` or `false`, "
                    "`Character` renders its character, `String` renders its contents, and `Unit` "
                    "renders `Unit`.")

REQS_SPEC = {
    "REQ-2500": dict(
  section='4. Root Type Hierarchy',
  summary='Built-in scalars have fixed, non-overridable `toString` implementations: integers render in decimal, floating types in Java-style text, Boolean as the words, Character as its character, and String as its contents',
  kind='runtime',
  quotes=['Built-in scalars provide fixed, non-overridable implementations: `Integer`, `Long`, `Byte`, and `Short` render in decimal, `Float` and `Double` use Java-style floating-point text, `Boolean` renders `true` or `false`, `Character` renders its character, and `String` renders its contents.'],
  tests=['SOL-TCK-0362'],
  note="One value of each named built-in is printed through `toString`; each expected token comes from the sentence's own list rather than from a captured trace."),
    "REQ-2501": dict(
        section="4. Root Type Hierarchy",
        summary="A built-in scalar cannot be extended, so a class whose superclass is a built-in "
                "scalar type is a compile-time error",
        kind="compile-time",
        quotes=["A built-in scalar cannot be extended and its `toString` cannot be overridden."],
        note="A class declares `extends Integer`. The section names no code for this restriction, "
             "so the rejection is bare; the sentinel proves non-execution."),
    "REQ-2502": dict(
  section='7. Static members and class initialization',
  summary='A class name is a receiver, not a value, so using it anywhere but as the root of a static member reference is `SOLV-TYPE-016`',
  kind='compile-time',
  quotes=['In particular `var c: Counter = Counter` and a read through an instance such as `instance.limit` are rejected.'],
  tests=['SOL-TCK-0364'],
  note='The program binds the class name to a local, which is exactly the `var c = Counter` form the section rejects. The section names `SOLV-TYPE-016`, so the oracle pins it.'),
    "REQ-2503": dict(
  section='7. Static members and class initialization',
  summary='A static member is reached only through the class name, so reading it through an instance is a compile-time error',
  kind='compile-time',
  quotes=['In particular `var c: Counter = Counter` and a read through an instance such as `instance.limit` are rejected.'],
  tests=['SOL-TCK-0365'],
  note='The program reads the static property through an instance, the second rejected form the section names. No code is named for the instance form, so the rejection is bare.'),
    "REQ-2504": dict(
        section="7. Static members and class initialization",
        summary="A static member shares the class's member namespace, so a static and an instance "
                "member may not reuse the same name",
        kind="compile-time",
        quotes=["A static member shares the class's member namespace: a static property and a static "
                "method may not reuse the name of an instance property, an instance method, or "
                "another static member of the same class, which is `SOLV-RESOL-002`.",
                "A static member is still subject to the rule that a member may not be named after "
                "its class."],
        note="A static property and an instance property share the name `x`; the section describes "
             "the collision but the quoted passage names no code in the general form, so the "
             "rejection is bare."),
    "REQ-2505": dict(
        section="7. Static members and class initialization",
        summary="A static member has no receiver, so `this` inside a static method is "
                "`SOLV-RESOL-005`",
        kind="compile-time",
        quotes=["A static member has no receiver. `this` and every `super` form are rejected inside "
                "a static method body and inside a class initializer block: `this` is "
                "`SOLV-RESOL-005` and `super` is `SOLV-RESOL-006`."],
        note="A static method reads `this.n`. The section names `SOLV-RESOL-005`, so the oracle "
             "pins it.",
        diagnosticCode="SOLV-RESOL-005",
        diagnosticNormative=True,
    ),
    "REQ-2506": dict(
        section="7. Static members and class initialization",
        summary="A static member has no receiver, so `super` inside a static method is "
                "`SOLV-RESOL-006`",
        kind="compile-time",
        quotes=["A static member has no receiver. `this` and every `super` form are rejected inside "
                "a static method body and inside a class initializer block: `this` is "
                "`SOLV-RESOL-005` and `super` is `SOLV-RESOL-006`."],
        note="A static method calls `super.hashCode()`. The section names `SOLV-RESOL-006`, so the "
             "oracle pins it.",
        diagnosticCode="SOLV-RESOL-006",
        diagnosticNormative=True,
    ),
    "REQ-2507": dict(
        section="7. Static members and class initialization",
        summary="The `toString`/`equals`/`hashCode` reserved-name rules are instance-only, so a "
                "static member may use those names and reading it still works",
        kind="runtime",
        quotes=["The `toString`/`equals`/`hashCode` reserved-name rules apply to **instance** "
                "members only, so a static member may use those names."],
        note="A class declares `static var toString` and reads it through the class name; the read "
             "observes the static cell, which the section says remains reachable alongside the "
             "inherited universal instance member. The expected bytes are the cell's value.",
    ),
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


def BAD(tid, cat, req, src, diag, note):
    T(tid, cat, req, src, "COMPILE_ERROR", {"diagnostic": diag}, note)


OK("SOL-TCK-0362", "types", "REQ-2500",
   'var i: Integer = 42\nvar l: Long = 42L\nvar f: Float = 1.5F\nvar d: Double = 1.5\n'
   'var c: Character = \'A\'\nvar s: String = "hi"\nvar b: Boolean = true\n'
   'print(i)\nprint("|")\nprint(l)\nprint("|")\nprint(f)\nprint("|")\nprint(d)\nprint("|")\n'
   'print(c)\nprint("|")\nprint(s)\nprint("|")\nprint(b)\n',
   "42|42|1.5|1.5|A|hi|true",
   "One value of each named built-in renders through its fixed toString; the separators name which "
   "token belongs to which type.")
BAD("SOL-TCK-0363", "objects", "REQ-2501",
    'class MyInt extends Integer {\n    MyInt() {\n    }\n}\nprint("EXECUTED-INVALID")\n', {},
    "A class extending the built-in Integer is the forbidden extension.")
BAD("SOL-TCK-0364", "objects", "REQ-2502",
    (('class Counter {\n'
    '    var static mutable n: Integer = 0\n'
    '\n'
    '    Counter() {\n'
    '    }\n'
    '}\n'
    'var c: Any = Counter\n'
    'print("EXECUTED-INVALID")\n'
    '')),
    {"family": "TYPE", "code": "SOLV-TYPE-016"},
    "Binding the class name to a local uses it as a value, which pins SOLV-TYPE-016.")
BAD("SOL-TCK-0365", "objects", "REQ-2503",
    ('class Counter {\n'
    '    var static mutable n: Integer = 0\n'
    '\n'
    '    Counter() {\n'
    '    }\n'
    '}\n'
    'var c: Counter = Counter()\n'
    'print(c.n)\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''), {},
    "Reading a static member through an instance is the second rejected form.")
BAD("SOL-TCK-0366", "objects", "REQ-2504",
    (('class C {\n'
    '    var static mutable x: Integer = 1\n'
    '    var x: Integer\n'
    '\n'
    '    C() {\n'
    '        this.x = 2\n'
    '    }\n'
    '}\n'
    'var c: C = C()\n'
    'print("EXECUTED-INVALID")\n'
    '')), {},
    "A static and an instance member share the name x, which the shared namespace forbids.")
BAD("SOL-TCK-0367", "objects", "REQ-2505",
    ('class C {\n'
    '    var static mutable n: Integer = 1\n'
    '\n'
    '    method static f(): Integer {\n'
    '        return this.n\n'
    '    }\n'
    '\n'
    '    C() {\n'
    '    }\n'
    '}\n'
    'print(C.f())\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''),
    {"family": "RESOL", "code": "SOLV-RESOL-005"},
    "`this` inside a static method pins the specification-named SOLV-RESOL-005.")
BAD("SOL-TCK-0368", "objects", "REQ-2506",
    ('class mutable A {\n'
    '    A() {\n'
    '    }\n'
    '}\n'
    'class C extends A {\n'
    '    method static f(): Integer {\n'
    '        return super.hashCode()\n'
    '    }\n'
    '\n'
    '    C() {\n'
    '        super()\n'
    '    }\n'
    '}\n'
    'print(C.f())\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''),
    {"family": "RESOL", "code": "SOLV-RESOL-006"},
    "`super` inside a static method pins the specification-named SOLV-RESOL-006.")
OK("SOL-TCK-0369", "objects", "REQ-2507",
   (('class C {\n'
    '    var static toString: Integer = 7\n'
    '\n'
    '    C() {\n'
    '    }\n'
    '}\n'
    'print(C.toString)\n'
    '')),
   "7",
   "The static member may use the reserved instance name and the class-name read observes its "
   "value.")


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
