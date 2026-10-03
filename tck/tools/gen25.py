#!/usr/bin/env python3
"""Generate the interface-contract, generic-exception, and Result-diagnostic batch.

Closes three documented coverage gaps from the plan's "Still uncovered" list:

* section 8's interface method contract (methods not properties, exact parameter
  types, covariant return types, and the explicit-override requirement when two
  interfaces supply the same default);
* section 22.1's generic-exception rules (a generic class can be neither thrown
  nor caught, and its surplus constructor arguments stay an ordinary arity error);
* section 23.4's Result required diagnostics (`RESOL_UNKNOWN_MEMBER`,
  `TYPE_ARITY_MISMATCH`, `TYPE_FUNCTION_AS_VALUE`).

Every diagnostic is taken from a code the specification names verbatim; section 8
names none, so its rejections are bare.
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

RESULT = ('enum Result<T, E> {\n'
          '    Ok(T)\n'
          '    Err(E)\n'
          '}\n'
          'func get(): Result<Integer, String> {\n'
          '    return Result.Ok(1)\n'
          '}\n')

REQS_SPEC = {
    "REQ-2300": dict(
        section="8. Interfaces",
        summary="Interfaces contain methods, not stored properties, so a property declaration in "
                "an interface is a compile-time error",
        kind="compile-time",
        quotes=["Interfaces contain methods, not stored properties."],
        note="A `val` property in an interface is the negated sentence. Section 8 names no code, so "
             "the rejection is bare; the program prints a sentinel only if it executes."),
    "REQ-2301": dict(
        section="8. Interfaces",
        summary="An implementing method must use the same parameter types as the interface method; "
                "a different parameter type is a compile-time error rather than an overload",
        kind="compile-time",
        quotes=["An implementing method must use the same parameter types and a covariant return "
                "type."],
        note="The class declares the interface method with an `Integer` parameter where the "
             "interface declares `String`. If the implementation treated it as an overload the "
             "interface contract would be unimplemented, so the rejection is required either way. "
             "No code is named, so the oracle is bare."),
    "REQ-2302": dict(
        section="8. Interfaces",
        summary="An implementing method may narrow the interface method's return type covariantly, "
                "and a return type unrelated to the interface's is a compile-time error",
        kind="compile-time",
        quotes=["An implementing method must use the same parameter types and a covariant return "
                "type."],
        note="The positive arm returns a subtype `Dog` for an interface method declared to return "
             "`Animal` (which must be `mutable` because classes are final by default) and is observed "
             "as stdout; the negative arm returns an unrelated `Rock`, which the specification's "
             "'covariant' requirement rejects. The pair differs only in the return type's relation "
             "to `Animal`."),
    "REQ-2303": dict(
        section="8. Interfaces",
        summary="When multiple interfaces provide an otherwise unresolved default for the same "
                "method, the class must explicitly override it; without an override the class is "
                "rejected, and with one the override runs",
        kind="compile-time",
        quotes=["If multiple interfaces provide an otherwise unresolved default for the same method, "
                "the class must explicitly override it."],
        note="Two interfaces supply the same default method. The negative arm omits the override and "
             "is rejected bare; the positive arm declares it and the observed stdout is the "
             "override's value, proving the explicit declaration is what resolves the conflict."),
    "REQ-2304": dict(
        section="22.1 Exception types",
        summary="A generic class cannot be thrown, because constructing it yields a parameterized "
                "type that a `throw` operand does not accept",
        kind="compile-time",
        quotes=["A generic class cannot be thrown or caught at all today: constructing it yields a "
                "parameterized type, which neither a `throw` operand nor a `catch` handler type "
                "accepts.",
                "| `SEM_THROW_NON_EXCEPTION` | `SOLV-SEM-053` | the `throw` operand expression |"],
        note="A parameterized generic exception instance is thrown. Section 22.6 names "
             "`SOLV-SEM-053` for the throw operand, and a parameterized application is exactly the "
             "non-exception operand shape section 22.1 describes, so the oracle pins the code.",
        diagnosticCode="SOLV-SEM-053",
        diagnosticNormative=True,
    ),
    "REQ-2305": dict(
        section="22.1 Exception types",
        summary="A generic class cannot be caught, because a parameterized type is not an accepted "
                "`catch` handler type",
        kind="compile-time",
        quotes=["A generic class cannot be thrown or caught at all today: constructing it yields a "
                "parameterized type, which neither a `throw` operand nor a `catch` handler type "
                "accepts.",
                "| `SEM_INVALID_CATCH_TYPE` | `SOLV-SEM-054` | the `catch` clause's type reference |"],
        note="The handler names `MyErr<Integer>`. Section 22.6 names `SOLV-SEM-054` for an invalid "
             "catch type, and section 22.1 says the parameterized type is not accepted, so the "
             "oracle pins the code. The thrown value is an ordinary user exception, so only the "
             "handler type is wrong.",
        diagnosticCode="SOLV-SEM-054",
        diagnosticNormative=True,
    ),
    "REQ-2306": dict(
        section="22.1 Exception types",
        summary="For a generic exception class, supplying more arguments than its declared "
                "constructor has remains an ordinary arity error rather than the synthesized "
                "message argument",
        kind="compile-time",
        quotes=["for a generic one, supplying more arguments than its declared constructor has "
                "remains an ordinary arity error rather than a message.",
                "supplying more than one extra trailing argument is an arity error "
                "(`SOLV-TYPE-003`)."],
        note="The generic exception declares a zero-argument constructor and is constructed with "
             "two arguments; the surplus must not be reinterpreted as the single optional message. "
             "Section 22.1 names `SOLV-TYPE-003` as the arity error, so the oracle pins it.",
        diagnosticCode="SOLV-TYPE-003",
        diagnosticNormative=True,
    ),
    "REQ-2307": dict(
        section="23.4 Required diagnostics",
        summary="A `Result` receiver member that is not a `Result` operation is "
                "`RESOL_UNKNOWN_MEMBER` (`SOLV-RESOL-004`)",
        kind="compile-time",
        quotes=["`Result` operations reuse existing diagnostic codes; no new codes are introduced.",
                "| `RESOL_UNKNOWN_MEMBER` | `SOLV-RESOL-004` | a member of a `Result` receiver that "
                "is not a `Result` operation |"],
        note="A `Result` receiver is accessed through a member name that is not one of the six "
             "operations. Section 23.4 names the code verbatim, so the oracle pins "
             "`SOLV-RESOL-004`.",
        diagnosticCode="SOLV-RESOL-004",
        diagnosticNormative=True,
    ),
    "REQ-2308": dict(
        section="23.4 Required diagnostics",
        summary="A `Result` operation call with the wrong argument count is `TYPE_ARITY_MISMATCH` "
                "(`SOLV-TYPE-003`)",
        kind="compile-time",
        quotes=["`Result` operations reuse existing diagnostic codes; no new codes are introduced.",
                "| `TYPE_ARITY_MISMATCH` | `SOLV-TYPE-003` | a `Result` operation call with the "
                "wrong argument count |"],
        note="`isOk` takes no parameters, so supplying one is the wrong argument count. Section "
             "23.4 names `SOLV-TYPE-003` verbatim, so the oracle pins it.",
        diagnosticCode="SOLV-TYPE-003",
        diagnosticNormative=True,
    ),
    "REQ-2309": dict(
        section="23.4 Required diagnostics",
        summary="A bare member read of a `Result` operation, without the call, is "
                "`TYPE_FUNCTION_AS_VALUE` (`SOLV-TYPE-014`)",
        kind="compile-time",
        quotes=["`Result` operations reuse existing diagnostic codes; no new codes are introduced.",
                "| `TYPE_FUNCTION_AS_VALUE` | `SOLV-TYPE-014` | a bare member read of a `Result` "
                "operation (no call) |"],
        note="The program reads `r.isOk` without calling it, the exact shape the table names, so "
             "the oracle pins `SOLV-TYPE-014`.",
        diagnosticCode="SOLV-TYPE-014",
        diagnosticNormative=True,
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


# --- section 8.
BAD("SOL-TCK-0341", "objects", "REQ-2300",
    'interface Named {\n    val name: String\n}\nclass U implements Named {\n'
    '    val name: String\n\n    U(name: String) {\n        this.name = name\n    }\n}\n'
    'val u = U("x")\nprint(u.name)\n' + NEG, {},
    "A stored property in an interface is the negated sentence.")
BAD("SOL-TCK-0342", "objects", "REQ-2301",
    'interface Named {\n    func greet(who: String): String\n}\nclass U implements Named {\n'
    '    U() {\n    }\n\n    func greet(who: Integer): String {\n        return "hi"\n    }\n}\n'
    'print("EXECUTED-INVALID")\n', {},
    "The implementing method changes the interface parameter type to Integer.")
OK("SOL-TCK-0343", "objects", "REQ-2302",
   'mutable class Animal {\n    Animal() {\n    }\n}\nclass Dog extends Animal {\n    Dog() {\n    }\n}\n'
   'interface Maker {\n    func make(): Animal\n}\nclass DogMaker implements Maker {\n'
   '    DogMaker() {\n    }\n\n    func make(): Dog {\n        return Dog()\n    }\n}\n'
   'val m: Maker = DogMaker()\nprint("covok")\n',
   "covok",
   "Returning the subtype Dog for an interface method declared to return Animal is a covariant "
   "return and is accepted.")
BAD("SOL-TCK-0344", "objects", "REQ-2302",
    'mutable class Animal {\n    Animal() {\n    }\n}\nclass Rock {\n    Rock() {\n    }\n}\n'
    'interface Maker {\n    func make(): Animal\n}\nclass RockMaker implements Maker {\n'
    '    RockMaker() {\n    }\n\n    func make(): Rock {\n        return Rock()\n    }\n}\n'
    'val m: Maker = RockMaker()\nprint("EXECUTED-INVALID")\n', {},
    "Rock is unrelated to Animal, so the return type is not covariant.")
BAD("SOL-TCK-0345", "objects", "REQ-2303",
    'interface A {\n    func speak(): String {\n        return "a"\n    }\n}\n'
    'interface B {\n    func speak(): String {\n        return "b"\n    }\n}\n'
    'class C implements A, B {\n    C() {\n    }\n}\n'
    'val c = C()\nprint(c.speak())\n' + NEG, {},
    "Two interfaces supply the same default and the class does not override it.")
OK("SOL-TCK-0346", "objects", "REQ-2303",
   'interface A {\n    func speak(): String {\n        return "a"\n    }\n}\n'
   'interface B {\n    func speak(): String {\n        return "b"\n    }\n}\n'
   'class C implements A, B {\n    C() {\n    }\n\n    func speak(): String {\n        return "c"\n'
   '    }\n}\nval c = C()\nprint("conf" .. c.speak())\n',
   "confc",
   "The explicit override resolves the same default from both interfaces and its value is what "
   "runs.")

# --- section 22.1: generic exceptions.
BAD("SOL-TCK-0347", "exceptions", "REQ-2304",
    'class MyErr<T> extends RuntimeException {\n    val payload: T\n\n    MyErr(payload: T) {\n'
    '        this.payload = payload\n    }\n}\nthrow MyErr<Integer>(1)\n'
    'print("EXECUTED-INVALID")\n',
    {"family": "SEM", "code": "SOLV-SEM-053"},
    "A parameterized generic exception is thrown, which the throw operand does not accept.")
BAD("SOL-TCK-0348", "exceptions", "REQ-2305",
    'class Simple extends RuntimeException {\n    Simple() {\n    }\n}\n'
    'class MyErr<T> extends RuntimeException {\n    val payload: T\n\n    MyErr(payload: T) {\n'
    '        this.payload = payload\n    }\n}\nfunc f() {\n    throw Simple()\n}\ntry {\n    f()\n'
    '} catch (e: MyErr<Integer>) {\n    print("caught")\n}\nprint("EXECUTED-INVALID")\n',
    {"family": "SEM", "code": "SOLV-SEM-054"},
    "The handler names a parameterized generic type, which is not an accepted catch handler type.")
BAD("SOL-TCK-0349", "exceptions", "REQ-2306",
    'class MyErr<T> extends RuntimeException {\n    MyErr() {\n    }\n}\n'
    'throw MyErr<Integer>(1, 2)\n'
    'print("EXECUTED-INVALID")\n',
    {"family": "TYPE", "code": "SOLV-TYPE-003"},
    "Two arguments for a zero-argument generic exception constructor remain an arity error, not a "
    "synthesized message.")

# --- section 23.4: Result operation diagnostics.
BAD("SOL-TCK-0350", "result", "REQ-2307",
    RESULT + 'func use(): Result<Integer, String> {\n    val r = get()\n    val b = r.nope();\n'
    '    return Result.Ok(1)\n}\nprint("EXECUTED-INVALID")\n',
    {"family": "RESOL", "code": "SOLV-RESOL-004"},
    "An unknown member on a Result receiver pins the specification-named SOLV-RESOL-004.")
BAD("SOL-TCK-0351", "result", "REQ-2308",
    RESULT + 'func use(): Result<Integer, String> {\n    val r = get()\n    val b = r.isOk(1);\n'
    '    return Result.Ok(1)\n}\nprint("EXECUTED-INVALID")\n',
    {"family": "TYPE", "code": "SOLV-TYPE-003"},
    "isOk takes no arguments, so the extra argument is the wrong argument count and pins "
    "SOLV-TYPE-003.")
BAD("SOL-TCK-0352", "result", "REQ-2309",
    RESULT + 'func use(): Result<Integer, String> {\n    val r = get()\n    val f = r.isOk;\n'
    '    return Result.Ok(1)\n}\nprint("EXECUTED-INVALID")\n',
    {"family": "TYPE", "code": "SOLV-TYPE-014"},
    "A bare member read of a Result operation pins the specification-named SOLV-TYPE-014.")


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
