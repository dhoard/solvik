#!/usr/bin/env python3
"""Generate the static-toString dispatch and deferred-safe-cast batch.

Closes the last two guest-observable rules surfaced by the sentence-level audit:
the fact that a static `toString` cannot replace the universal instance member,
and the deferral of safe-cast syntax.
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

REQS_SPEC = {
    "REQ-3200": dict(
        section="7. Static members and class initialization",
        summary="A static `toString` cannot replace the universal `Any.toString()` instance member, "
                "so an instance still formats through the universal member",
        kind="runtime",
        quotes=["Those names are reserved to protect the universal `Any` members, which are instance "
                "members reached through virtual dispatch; a static member never enters the "
                "dispatch table, so a `static func toString()` cannot replace `Any.toString()` any "
                "more than an instance method of another name can, and `instance.toString()` keeps "
                "reaching the universal member.",
                "for a class declaring `static val toString: Integer` and inheriting the default "
                "`Any.toString()`, the expression `C.toString` reads the static cell and `instance` "
                "formatting still calls `Any.toString()`."],
        note="A class declares `static func toString` and an instance calls `toString()`. The "
             "instance reaches the inherited universal member and prints the class name, not the "
             "static method's text; an implementation that resolved the instance call to the static "
             "member would print `static`."),
    "REQ-3201": dict(
        section="18. Type Tests and Casts",
        summary="Safe-cast syntax is deferred, so the `as?` spelling is not part of the language and "
                "is a parse error",
        kind="syntax",
        quotes=["An unsuccessful `as` cast raises a Solvik runtime type error. Safe-cast syntax is "
                "deferred."],
        note="The program writes `a as? A`, a spelling the specification does not define. The "
             "section names no code for the absent syntax, so the rejection is bare; the sentinel "
             "proves non-execution.",
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


def BAD(tid, cat, req, src, note):
    T(tid, cat, req, src, "COMPILE_ERROR", {"diagnostic": {}}, note)


OK("SOL-TCK-0415", "objects", "REQ-3200",
   'class C {\n    static func toString(): String {\n        return "static"\n    }\n\n'
   '    C() {\n    }\n}\nval c = C()\nprint("st" .. c.toString())\n',
   "stC",
   "The instance call reaches the universal member and prints the class name `C`, not the static "
   "method's text.")
BAD("SOL-TCK-0416", "types", "REQ-3201",
    'class A {\n    A() {\n    }\n}\nval a: A = A()\nval b = a as? A\n' + NEG,
    "The deferred `as?` spelling is not defined and is a parse error.")


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
