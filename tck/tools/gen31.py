#!/usr/bin/env python3
"""Generate the variables, scope-block, switch-default, range, and include-identity batch.

Closes the remaining testable obligations in sections 2, 6, 13, 17, and 20 that
earlier batches did not enumerate: `mutable val` mutability, top-level bindings being
locals of the implicit main, scope-block independence and abrupt exits, the
optional statement `switch` default, the range loop variable's immutability and
scope, literal (unexpanded) include paths, and canonical include identity through
`..` path normalization.
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

REQS_SPEC = {
    "REQ-2900": dict(
        section="2. Variables and Mutability",
        summary="`mutable val` declares a mutable binding or property, so a local declared `mutable val` may "
                "be reassigned and the new value is observed",
        kind="runtime",
        quotes=["`mutable val` declares a mutable binding/property."],
        note="A `mutable val` local is reassigned and printed, so the observed bytes are the assigned "
             "value; this is the positive counterpart to the `val` immutability covered by "
             "REQ-0300/REQ-0301."),
    "REQ-2901": dict(
        section="6. Functions",
        summary="A top-level `val`, mutable or not, is a local of the implicit main, not a global, so it is "
                "not visible to a user-declared function",
        kind="compile-time",
        quotes=["a top-level `val`, whether or not it is `mutable`, is therefore a local of the implicit main, not a global."],
        note="A top-level binding is referenced from a declared function, which is not the implicit "
             "main. The reference is resolved against function locals and parameters only, so it is "
             "an unresolvable-name error. The section names no code, so the rejection is bare."),
    "REQ-2902": dict(
        section="6. Functions",
        summary="A scope block introduces a new lexical scope, and sibling blocks are independent, "
                "so the same local name may be declared in each without shadowing",
        kind="runtime",
        quotes=["A scope block introduces a new lexical scope for the statements it contains; "
                "sibling blocks are independent scopes, so the same local name may be declared in "
                "each without any shadowing between them."],
        note="Two sibling blocks each declare and print a local named `result`. The program is "
             "accepted and prints both values, so the declarations do not collide."),
    "REQ-2903": dict(
        section="6. Functions",
        summary="A scope block is neither a loop nor a function boundary, so `break` inside one "
                "applies to the enclosing loop",
        kind="runtime",
        quotes=["A scope block is neither a loop nor a function boundary: `break`, `continue`, and "
                "`return` inside it apply to the enclosing loop or function."],
        note="A scope block nested inside a range loop executes `break`; the loop terminates before "
             "its later iterations, so only the post-loop print appears."),
    "REQ-2904": dict(
        section="13. switch",
        summary="A statement `switch` may omit `default` and do nothing when no case label matches",
        kind="runtime",
        quotes=["Requiring `default` makes value production explicit for `Integer`, `String`, and "
                "regex dispatch, while a statement `switch` may still omit `default` and do nothing "
                "when no label matches."],
        note="A statement `switch` with one non-matching case and no `default` runs nothing and the "
             "program continues after it, which is the permitted statement form."),
    "REQ-2905": dict(
        section="17. Control Flow",
        summary="The range `for`-in loop variable is an implicitly declared immutable `Integer`, so "
                "assigning to it is a compile-time error",
        kind="compile-time",
        quotes=["The loop variable is an implicitly declared immutable `Integer` binding scoped to "
                "the loop body."],
        note="The loop body assigns to the loop variable. The section names no code, so the "
             "rejection is bare; the sentinel proves non-execution."),
    "REQ-2906": dict(
        section="17. Control Flow",
        summary="The range `for`-in loop variable is scoped to the loop body, so it is not visible "
                "after the loop",
        kind="compile-time",
        quotes=["The loop variable is an implicitly declared immutable `Integer` binding scoped to "
                "the loop body."],
        note="The program reads the loop variable after the loop, outside its scope. The section "
             "names no code, so the rejection is bare."),
    "REQ-2907": dict(
        section="20. File Inclusion",
        summary="Include paths are literal text: environment-variable expansion is not part of the "
                "language, so a `$` in a path is an ordinary character",
        kind="compile-time",
        quotes=["Shell interpolation, environment-variable expansion, URLs, classpath resources, "
                "package lookup, and non-file URI schemes are not part of the language.",
                "| `RESOL_INCLUDE_NOT_FOUND` | `SOLV-RESOL-008` | include directive |"],
        note="The path `$HOME/x.sol` is taken literally and no such file exists, so the include is "
             "not found. If the implementation expanded the environment variable the (existing) "
             "home file could be found, so the RESOL-008 rejection is the observable difference. "
             "The section names `SOLV-RESOL-008` for a missing include.",
        diagnosticCode="SOLV-RESOL-008",
        diagnosticNormative=True,
    ),
    "REQ-2908": dict(
        section="20. File Inclusion",
        summary="Two paths that resolve to the same canonical file are the same include, so a "
                "second include through a `..` spelling is a no-op and the file's top-level "
                "statement runs once",
        kind="module",
        quotes=["Two paths or symlinks that resolve to the same file are the same include.",
                "A canonical physical file is expanded at most once per evaluated root. A later "
                "include of the same canonical file is a no-op, so a diamond is deterministic and "
                "an included top-level statement never runs twice."],
        note="The root includes `a.sol` and then `sub/../a.sol`, which canonicalizes to the same "
             "file. The included file's print runs exactly once, so a second `a` in the output "
             "would falsify the canonical-identity rule.",
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


OK("SOL-TCK-0390", "types", "REQ-2900",
   'mutable val x: Integer = 1\nx = 2\nprint("var" .. x)\n',
   "var2",
   "The `mutable val` local is reassigned and the assigned value is observed.")
BAD("SOL-TCK-0391", "names", "REQ-2901",
    'val x = 1\nfunc f(): Integer {\n    return x\n}\nprint("EXECUTED-INVALID")\n', {},
    "A declared function cannot see the implicit main's top-level local.")
OK("SOL-TCK-0392", "control", "REQ-2902",
   '{\n    val result = 1\n    print("s" .. result)\n}\n{\n    val result = 2\n'
   '    print("s" .. result)\n}\n',
   "s1s2",
   "Sibling blocks each declare the same local name and both print their own value.")
OK("SOL-TCK-0393", "control", "REQ-2903",
   'for (i in 1...5) {\n    {\n        break\n    }\n}\nprint("sbdone")\n',
   "sbdone",
   "The break inside the scope block exits the enclosing range loop, so the loop body does not run "
   "to completion and only the post-loop text appears.")
OK("SOL-TCK-0394", "control", "REQ-2904",
   'val x = 9\nswitch (x) {\n    case 1:\n        print("one")\n}\nprint("swafter")\n',
   "swafter",
   "With no matching case and no default, the statement switch does nothing and the program "
   "continues.")
BAD("SOL-TCK-0395", "control", "REQ-2905",
    'for (i in 1...3) {\n    i = 0\n}\nprint("EXECUTED-INVALID")\n', {},
    "Assigning to the implicitly immutable loop variable is rejected.")
BAD("SOL-TCK-0396", "names", "REQ-2906",
    'for (i in 1...3) {\n    print(i)\n}\nprint(i)\n' + NEG, {},
    "The loop variable is not visible after the loop.")
BAD("SOL-TCK-0397", "files", "REQ-2907",
    'include "$HOME/x.sol"\nprint("EXECUTED-INVALID")\n',
    {"family": "RESOL", "code": "SOLV-RESOL-008"},
    "The $-containing path is literal and names no file, so the include is not found.")
OK("SOL-TCK-0398", "modules", "REQ-2908",
   'include "a.sol"\ninclude "sub/../a.sol"\nprint("cz")\n',
   "cacz",
   "The two includes canonicalize to the same file, so the included print runs once.",
   libs={"a.sol": 'print("ca")\n', "sub/.keep": "placeholder\n"})


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
