#!/usr/bin/env python3
"""Generate the control-flow, return, arity, Unit, and switch-label gap batch.

Closes a further set of section 4/6/13/17 obligations not covered by earlier
batches: the pre-test `while` loop, the `return`/return-type rules, the trailing
comma in call argument lists, a program with no entry point, the rendering of
`Unit`, the compile-time-constant requirement for constant switch cases, and the
`String` requirement for regex cases.

Rejections whose rule the specification names no code for are asserted bare.
"""
import base64
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.09-draft")
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.09-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

REQS_SPEC = {
    "REQ-2400": dict(
        section="17. Control Flow",
        summary="`while` is a pre-test loop: the condition is evaluated before each iteration and "
                "the body runs only while it is true",
        kind="runtime",
        quotes=["`while` is a pre-test loop."],
        note="The loop prints the counter at each iteration and increments it in the body, so the "
             "expected bytes `012` are exactly the three iterations the pre-test condition admits; "
             "a post-test loop would run the body one extra time."),
    "REQ-2401": dict(
        section="6. Functions",
        summary="`return;` is valid only in a function declared without a return type, so a bare "
                "return in a value-returning function is a compile-time error",
        kind="compile-time",
        quotes=["`return;` is valid only in a function declared without a return type; `return "
                "value` requires the value to be assignable to the declared return type."],
        note="A function declared to return `Integer` uses a bare `return;`. The section names no "
             "code, so the rejection is bare; the sentinel proves non-execution."),
    "REQ-2402": dict(
        section="6. Functions",
        summary="`return value` requires the value assignable to the declared return type, so a "
                "value returned from a function declared without a return type is a compile-time "
                "error",
        kind="compile-time",
        quotes=["`return;` is valid only in a function declared without a return type; `return "
                "value` requires the value to be assignable to the declared return type."],
        note="A value-less function returns `1`, which is not assignable to its (Unit) result. The "
             "section names no code, so the rejection is bare."),
    "REQ-2403": dict(
        section="6. Functions",
        summary="A call's argument list may end with a trailing comma, which contributes no "
                "argument; the list still requires at least one argument, so `add(,)` is a parse "
                "error while `add()` is the ordinary empty argument list",
        kind="syntax",
        quotes=["A call's argument list may end with a trailing comma (`add(1, 2,)`). The trailing "
                "comma contributes no argument, so it never affects arity. The list still requires "
                "at least one argument, so `add(,)` is a parse error while `add()` is the ordinary "
                "empty argument list."],
        note="The accepted arm calls a two-parameter function with a trailing comma and observes "
             "the sum, so the comma contributed no third argument; the rejected arm supplies a lone "
             "comma, which the section calls a parse error. Bare rejection: no code is named."),
    "REQ-2404": dict(
        section="6. Functions",
        summary="A program with no executable top-level statements has no entry point and does "
                "nothing, completing with status 0",
        kind="process",
        quotes=["A program with no executable top-level statements has no entry point and does "
                "nothing."],
        note="The program declares one function and has no top-level statement, so the expected "
             "stdout is empty and the expected language exit status is 0."),
    "REQ-2405": dict(
        section="4. Root Type Hierarchy",
        summary="`Unit` is a real type with one value and renders as `Unit` through its fixed, "
                "non-overridable `toString`",
        kind="runtime",
        quotes=["Built-in scalars provide fixed, non-overridable implementations: `Integer`, "
                "`Long`, `Byte`, and `Short` render in decimal, `Float` and `Double` use Java-style "
                "floating-point text, `Boolean` renders `true` or `false`, `Character` renders its "
                "character, `String` renders its contents, and `Unit` renders `Unit`.",
                "`Unit` has one value and is the result of a function that returns normally without "
                "a value."],
        note="A value-less function is bound to a `Unit` local and printed, so the expected bytes "
             "`xUnit` show both the function's side effect and the fixed `Unit` rendering."),
    "REQ-2406": dict(
        section="13. switch",
        summary="Constant case expressions must be compile-time constants assignable to the "
                "switched value's type, so a case label naming a runtime binding is a compile-time "
                "error",
        kind="compile-time",
        quotes=["Constant case expressions must be compile-time constants assignable to the switched "
                "value's type."],
        note="The case label is the runtime binding `x` rather than a constant. The section names "
             "no code, so the rejection is bare; the sentinel proves non-execution."),
    "REQ-2407": dict(
        section="13. switch",
        summary="Regex cases require a `String` switch value, so a regex case on a non-String "
                "switch is a compile-time error",
        kind="compile-time",
        quotes=["Regex cases require a `String` switch value."],
        note="The switch value is an `Integer` and one case is a regex pattern. The section names "
             "no code, so the rejection is bare."),
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


OK("SOL-TCK-0353", "control", "REQ-2400",
   'var i: Integer = 0\nwhile (i < 3) {\n    print(i)\n    i = i + 1\n}\n',
   "012",
   "The pre-test loop prints 0, 1, and 2; the condition is checked before each body run.")
BAD("SOL-TCK-0354", "control", "REQ-2401",
    'func f(): Integer {\n    return;\n}\nprint(f())\n' + NEG, {},
    "A bare return in a value-returning function is the forbidden form.")
BAD("SOL-TCK-0355", "control", "REQ-2402",
    'func f() {\n    return 1\n}\nf()\nprint("EXECUTED-INVALID")\n', {},
    "A value returned from a value-less function is not assignable to its Unit result.")
OK("SOL-TCK-0356", "syntax", "REQ-2403",
   'func add(a: Integer, b: Integer): Integer {\n    return a + b\n}\nprint("tc" .. add(1, 2,))\n',
   "tc3",
   "The trailing comma contributes no third argument, so the call has source-level arity 2.")
BAD("SOL-TCK-0357", "syntax", "REQ-2403",
    'func add(a: Integer, b: Integer): Integer {\n    return a + b\n}\nprint(add(,))\n' + NEG, {},
    "A lone comma in the argument list is a parse error, not an empty argument list.")
OK("SOL-TCK-0358", "process", "REQ-2404",
   'func f(): Integer {\n    return 1\n}\n',
   "",
   "With no executable top-level statement the program has no entry point, prints nothing, and "
   "exits 0.")
OK("SOL-TCK-0359", "types", "REQ-2405",
   'func f() {\n    print("x")\n}\nval u: Unit = f()\nprint(u)\n',
   "xUnit",
   "The value-less function's result is bound to a Unit local and its fixed rendering is `Unit`.")
BAD("SOL-TCK-0360", "control", "REQ-2406",
    'val x = 1\nswitch (x) {\n    case x:\n        print("same")\n    default:\n'
    '        print("default")\n}\nprint("EXECUTED-INVALID")\n', {},
    "A case label naming a runtime binding is not a compile-time constant.")
BAD("SOL-TCK-0361", "control", "REQ-2407",
    'val x = 1\nswitch (x) {\n    case regex r"1":\n        print("one")\n    default:\n'
    '        print("d")\n}\nprint("EXECUTED-INVALID")\n', {},
    "A regex case on an Integer switch value is not a String switch value.")


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
