#!/usr/bin/env python3
"""Generate the include-placement, built-in shadowing, finality, switch-default,
and remaining `Result` batch.

Closes further normative rules surfaced by a sentence-level audit: include
placement inside a compilation unit, built-in redeclaration, final-by-default
classes, static-initializer typing, the single trailing switch default, the
successful `unwrap` path, built-in call arity, and nested relative include
resolution against the including file.
"""
import base64
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.10-draft")
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.10-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

RESULT = ('enum Result<T, E> {\n'
          '    Ok(T)\n'
          '    Err(E)\n'
          '}\n')

REQS_SPEC = {
    "REQ-3100": dict(
        section="20. File Inclusion",
        summary="An include may appear only as an item of a compilation unit, so an include inside "
                "a function, block, loop, or other statement position is rejected",
        kind="syntax",
        quotes=["An include may appear only as an item of a compilation unit: it is not a statement "
                "and cannot appear in a function, method, constructor, block, loop, switch, or "
                "match branch."],
        note="The include is written inside a function body, the first forbidden position. The "
             "section names no code, so the rejection is bare; the sentinel proves non-execution."),
    "REQ-3101": dict(
        section="20. File Inclusion",
        summary="Redeclaring a built-in function or type is rejected, and built-in names cannot be "
                "shadowed by a declaration",
        kind="compile-time",
        quotes=["Redeclaring a built-in function or type is rejected by the existing declaration "
                "checks.",
                "Built-in types and functions are always visible unqualified and cannot be shadowed "
                "by a module or alias name."],
        note="Two arms: a function named `print` and a class named `Byte`, each a built-in name. "
             "The section names no code for the redeclaration, so both rejections are bare."),
    "REQ-3102": dict(
        section="7. Classes",
        summary="Classes are final by default, so extending a class that was not declared `open` is "
                "a compile-time error",
        kind="compile-time",
        quotes=["Classes are final by default.",
                "A class must explicitly opt into inheritance:"],
        note="A class without `open` is extended. The section names no code for the finality "
             "restriction, so the rejection is bare."),
    "REQ-3103": dict(
        section="7. Classes",
        summary="A static declaration initializer that is not assignable to the declared type is "
                "`SOLV-TYPE-001`",
        kind="compile-time",
        quotes=["A static declaration initializer that is not assignable to the declared type is "
                "`SOLV-TYPE-001`."],
        note="A static `Integer` property is initialized with a `String`. The section names the "
             "code verbatim, so the oracle pins `SOLV-TYPE-001`.",
        diagnosticCode="SOLV-TYPE-001",
        diagnosticNormative=True,
    ),
    "REQ-3104": dict(
        section="13. switch",
        summary="A switch contains at most one `default`, and it must be last, whether the switch "
                "is a statement or an expression",
        kind="compile-time",
        quotes=["A switch contains at most one `default`, and it must be last."],
        note="Two arms: a second `default` and a `default` followed by a later case. The section "
             "names no code, so both rejections are bare."),
    "REQ-3105": dict(
        section="23. Result Operations",
        summary="`unwrap` returns the success payload of an `Ok`",
        kind="runtime",
        quotes=["`unwrap` returns the success payload of an `Ok`."],
        note="The receiver is an `Ok` and `unwrap` returns its payload, which is printed; the "
             "`Err`-fault half is covered by REQ-0206."),
    "REQ-3106": dict(
        section="6. Functions",
        summary="The predeclared `print`, `println`, and `exit` functions each declare exactly one "
                "parameter, so a call with a different argument count is a compile-time error",
        kind="compile-time",
        quotes=["The predeclared `print`, `println`, and `exit` functions each declare exactly one "
                "parameter, so a call that supplies a different number of arguments is a "
                "compile-time error; built-ins participate in the ordinary resolved-callable model "
                "rather than receiving separate arity rules."],
        note="`print()` supplies zero arguments to a one-parameter built-in. The section names no "
             "code, so the rejection is bare."),
    "REQ-3107": dict(
        section="20. File Inclusion",
        summary="Every nested relative include is resolved against its including file, never the "
                "root directory",
        kind="module",
        quotes=["Every nested relative include is resolved against its including file, never the "
                "root directory."],
        note="The root includes `lib/m.sol`, which includes `n.sol`; `n.sol` exists only beside "
             "`m.sol` in `lib/`. The run succeeds only if the nested include resolved against the "
             "including file, not the root, so the printed value is the acceptance evidence.",
    ),
}

NEG = '\nprint("EXECUTED-INVALID")\n'

TESTS = []
EXTRA = {}


def T(tid, cat, req, src, outcome, exp, note, libs=None):
    for q in REQS_SPEC[req]["quotes"]:
        if norm(q) not in SPEC_N:
            raise SystemExit("%s: quote not verbatim in spec: %r" % (tid, q[:90]))
    TESTS.append(dict(tid=tid, cat=cat, req=req, src=src, outcome=outcome, exp=exp, note=note))
    if libs:
        EXTRA[tid] = libs


def OK(tid, cat, req, src, stdout, note, libs=None):
    T(tid, cat, req, src, "SUCCESS",
      {"languageExit": 0, "stdoutBase64": base64.b64encode(stdout.encode()).decode()}, note, libs)


def BAD(tid, cat, req, src, diag, note, libs=None):
    T(tid, cat, req, src, "COMPILE_ERROR", {"diagnostic": diag}, note, libs)


BAD("SOL-TCK-0405", "files", "REQ-3100",
    'func f() {\n    include "x.sol"\n}\nf()\nprint("EXECUTED-INVALID")\n', {},
    "An include inside a function body is not a compilation-unit item.")
BAD("SOL-TCK-0406", "files", "REQ-3101",
    'func print(x: Integer) {\n}\nprint(1)\n' + NEG, {},
    "A user function may not redeclare the built-in print.")
BAD("SOL-TCK-0407", "files", "REQ-3101",
    'class Byte {\n    Byte() {\n    }\n}\nprint("EXECUTED-INVALID")\n', {},
    "A user class may not redeclare the built-in Byte type.")
BAD("SOL-TCK-0408", "objects", "REQ-3102",
    'class A {\n    A() {\n    }\n}\nclass B extends A {\n    B() {\n    }\n}\n'
    'print("EXECUTED-INVALID")\n', {},
    "Extending a class that was not declared open is rejected.")
BAD("SOL-TCK-0409", "objects", "REQ-3103",
    'class C {\n    static var n: Integer = "wrong"\n\n    C() {\n    }\n}\n'
    'print("EXECUTED-INVALID")\n',
    {"family": "TYPE", "code": "SOLV-TYPE-001"},
    "A String static initializer for an Integer property pins SOLV-TYPE-001.")
BAD("SOL-TCK-0410", "control", "REQ-3104",
    'val x = 1\nswitch (x) {\n    case 1:\n        print("one")\n    default:\n        print("d")\n'
    '    default:\n        print("d2")\n}\nprint("EXECUTED-INVALID")\n', {},
    "A second default is rejected.")
BAD("SOL-TCK-0411", "control", "REQ-3104",
    'val x = 1\nswitch (x) {\n    default:\n        print("d")\n    case 2:\n        print("two")\n}\n'
    'print("EXECUTED-INVALID")\n', {},
    "A default that is not the last clause is rejected.")
OK("SOL-TCK-0412", "result", "REQ-3105",
   RESULT + 'func get(): Result<Integer, String> {\n    return Result.Ok(9)\n}\n'
   'print("uw" .. get().unwrap())\n',
   "uw9",
   "unwrap on an Ok returns the success payload.")
BAD("SOL-TCK-0413", "control", "REQ-3106",
    'print()\nprint("EXECUTED-INVALID")\n', {},
    "print declares exactly one parameter, so a zero-argument call is rejected.")
OK("SOL-TCK-0414", "modules", "REQ-3107",
   'include "lib/m.sol"\nprint("nm" .. m())\n',
   "nm7",
   "The nested include in lib/m.sol resolves against lib/, so the helper in lib/n.sol is found.",
   libs={"lib/m.sol": 'include "n.sol"\nfunc m(): Integer {\n    return n()\n}\n',
         "lib/n.sol": 'func n(): Integer {\n    return 7\n}\n'})


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
        for rel, text in EXTRA.get(t["tid"], {}).items():
            full = os.path.join(d, rel)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, "w", encoding="utf-8") as handle:
                handle.write(text)
        man = {"manifestSchemaVersion": 1, "specVersion": SPEC_VERSION, "testId": t["tid"],
               "category": t["cat"], "profile": "full-language", "status": "required",
               "requirements": [t["req"]], "entryPoint": "main.sol",
               **({"fixtureRoot": "."} if EXTRA.get(t["tid"]) else {}),
               "outcome": t["outcome"], "expectation": t["exp"]}
        with open(os.path.join(d, "%s.manifest.json" % t["tid"]), "w", encoding="utf-8") as handle:
            json.dump(man, handle, indent=2)
            handle.write("\n")
        print(t["tid"], t["outcome"], t["req"])
    print("wrote %d test directories, %d requirements" % (len(TESTS), len(REQS_SPEC)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
