#!/usr/bin/env python3
"""Generate the include-diagnostic, static-return, delegate, and Any batch.

Closes the remaining specification-named diagnostics that no earlier batch
addressed (`SOLV-RESOL-007`, `SOLV-RESOL-009`, `SOLV-RESOL-015`,
`SOLV-TYPE-011`) along with the section 9 delegate declaration rules and the
section 4 `Any` top-type clause.

`SOLV-RESOL-009` is exercised with a directory fixture whose final name ends in
`.sol`, so the canonical target is not a regular file. `SOLV-RESOL-010` (I/O
failure) is deliberately NOT tested here: producing it portably requires denying
file access, which is a platform facility, and the inventory records it as an
untestable-platform requirement rather than inventing a host-dependent test.
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
    "REQ-2600": dict(
        section="20. File Inclusion",
        summary="An include path must be non-empty and its final file name must end in `.sol`; "
                "otherwise the include is `RESOL_INCLUDE_INVALID_PATH` (`SOLV-RESOL-007`) at the "
                "path literal",
        kind="compile-time",
        quotes=["Require a non-empty path whose final file name ends in `.sol`; otherwise report "
                "`SOLV-RESOL-007` at the path literal.",
                "| `RESOL_INCLUDE_INVALID_PATH` | `SOLV-RESOL-007` | path literal |"],
        note="Two arms: a path whose final name ends in `.txt` and the empty path. Both are the "
             "negated path requirement, and the section names the code verbatim, so the oracle pins "
             "`SOLV-RESOL-007`.",
        diagnosticCode="SOLV-RESOL-007",
        diagnosticNormative=True,
    ),
    "REQ-2601": dict(
        section="20. File Inclusion",
        summary="An include whose canonical target is not a regular file is "
                "`RESOL_INCLUDE_NOT_FILE` (`SOLV-RESOL-009`) at the include directive",
        kind="compile-time",
        quotes=["| `RESOL_INCLUDE_NOT_FILE` | `SOLV-RESOL-009` | include directive |",
                "The file is normalized and canonicalized before it is used as an identity."],
        note="The fixture `lib/x.sol` is a directory (kept in the repository via a placeholder "
             "file), so the path has the required `.sol` spelling but the canonical target is not a "
             "regular file. The section names the code verbatim.",
        diagnosticCode="SOLV-RESOL-009",
        diagnosticNormative=True,
    ),
    "REQ-2602": dict(
        section="20. File Inclusion",
        summary="A qualified reference to a module that is not visible is "
                "`RESOL_UNKNOWN_MODULE` (`SOLV-RESOL-015`) at the reference",
        kind="compile-time",
        quotes=["| `RESOL_UNKNOWN_MODULE` | `SOLV-RESOL-015` | qualified reference |"],
        note="The program references `nope::thing()` although no included file declares the `nope` "
             "module. The section names the code verbatim.",
        diagnosticCode="SOLV-RESOL-015",
        diagnosticNormative=True,
    ),
    "REQ-2603": dict(
        section="7. Static members and class initialization",
        summary="A class initializer block holds statements and returns nothing, so a `return` with "
                "a value in the block is `SOLV-TYPE-011`",
        kind="compile-time",
        quotes=["A class declares **at most one** class initializer block. A second block is "
                "`SOLV-SEM-046`, reported on the later block. The block holds statements, not "
                "declarations; a bare `return` exits it early, and a `return` with a value is "
                "`SOLV-TYPE-011` because the block returns nothing."],
        note="The block contains `return 1`. The section names `SOLV-TYPE-011` for exactly this "
             "shape; the test asserts the code, not merely that the program is rejected.",
        diagnosticCode="SOLV-TYPE-011",
        diagnosticNormative=True,
    ),
    "REQ-2604": dict(
        section="9. Composition and Delegation",
        summary="A delegate is an explicitly typed property, so a `delegate var` declaration "
                "without a type is a compile-time error",
        kind="syntax",
        quotes=["A delegate is an immutable, explicitly typed property that must be initialized "
                "under the normal constructor rules."],
        note="The declaration omits the `: Type` annotation. The section names no code for the "
             "rule, so the rejection is bare; the sentinel proves non-execution."),
    "REQ-2605": dict(
        section="9. Composition and Delegation",
        summary="A delegate must be initialized under the normal constructor rules, so a "
                "constructor path that leaves it unassigned is a compile-time error",
        kind="compile-time",
        quotes=["A delegate is an immutable, explicitly typed property that must be initialized "
                "under the normal constructor rules."],
        note="The constructor is empty, so the delegate is never assigned on the successful path. "
             "The alias would otherwise forward calls through an uninitialized property. Bare "
             "rejection: no code is named."),
    "REQ-2606": dict(
        section="4. Root Type Hierarchy",
        summary="`Any` is the sole top type for every non-null value, so scalar, String, and class "
                "values are all assignable to `Any` without disabling their dynamic typing",
        kind="runtime",
        quotes=["`Any` is the sole top type for every non-null Solvik value, including every class, "
                "interface, and enum value."],
        note="A scalar and a String are bound to `Any` and printed, and one is type-tested with "
             "`is`; the expected bytes show the stored values, so `Any` accepted them without "
             "erasing the ability to inspect the dynamic type.",
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


BAD("SOL-TCK-0370", "files", "REQ-2600",
    'include "foo.txt"\nprint("EXECUTED-INVALID")\n',
    {"family": "RESOL", "code": "SOLV-RESOL-007"},
    "A path whose final name does not end in .sol pins SOLV-RESOL-007.")
BAD("SOL-TCK-0371", "files", "REQ-2600",
    'include ""\nprint("EXECUTED-INVALID")\n',
    {"family": "RESOL", "code": "SOLV-RESOL-007"},
    "The empty path pins the non-empty half of the same rule.")
BAD("SOL-TCK-0372", "files", "REQ-2601",
    'include "lib/x.sol"\nprint("EXECUTED-INVALID")\n',
    {"family": "RESOL", "code": "SOLV-RESOL-009"},
    "The canonical target is a directory, so it is not a regular file and pins SOLV-RESOL-009.",
    libs={"lib/x.sol/placeholder.txt": "placeholder\n"})
BAD("SOL-TCK-0373", "modules", "REQ-2602",
    (('var x: Any = nope::thing()\n'
    'print("EXECUTED-INVALID")\n'
    '')),
    {"family": "RESOL", "code": "SOLV-RESOL-015"},
    "A qualified reference to a module no file declares pins SOLV-RESOL-015.")
BAD("SOL-TCK-0374", "objects", "REQ-2603",
    'class C {\n    static {\n        return 1\n    }\n\n    C() {\n    }\n}\n'
    'print("EXECUTED-INVALID")\n',
    {"family": "TYPE", "code": "SOLV-TYPE-011"},
    "A value returned from a class initializer block pins SOLV-TYPE-011.")
BAD("SOL-TCK-0375", "objects", "REQ-2604",
    (('interface P {\n'
    '    method go(): Integer\n'
    '}\n'
    'class X implements P {\n'
    '    delegate a\n'
    '\n'
    '    X() {\n'
    '    }\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    '')), {},
    "A delegate declaration without an explicit type is rejected.")
BAD("SOL-TCK-0376", "objects", "REQ-2605",
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
    '    X() {\n'
    '    }\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    '')), {},
    "An unassigned delegate violates the normal constructor initialization rule.")
OK("SOL-TCK-0377", "types", "REQ-2606",
   'var a: Any = 42\nvar s: Any = "hi"\nprint(a)\nprint("|")\nprint(s)\nprint("|")\n'
   'print(a is Integer)\n',
   "42|hi|true",
   "Both a scalar and a String are assignable to Any and print their stored values; the is test "
   "shows the dynamic type remains inspectable.")


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
