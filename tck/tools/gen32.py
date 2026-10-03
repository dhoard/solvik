#!/usr/bin/env python3
"""Generate the remaining section 23 `Result` operation tests.

Earlier batches covered wrong-variant faults for `unwrap`/`unwrapErr`,
must-consume, propagation, and the required diagnostics. This batch covers the
operation table's remaining rows: `expect` on either variant, the exactly-once
evaluation of its message, `ignore`'s single receiver evaluation and `Unit`
result, the complementarity of `isOk`/`isErr`, and the successful `unwrapErr`
path.
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
          '}\n')

REQS_SPEC = {
    "REQ-3000": dict(
        section="23. Result Operations",
        summary="`expect(message)` returns the success payload of an `Ok`, ignoring the message",
        kind="runtime",
        quotes=["`expect(message)` returns the success payload of an `Ok`, ignoring the message."],
        note="The receiver is an `Ok` and `expect` returns its payload, which is printed. The "
             "message is supplied but has no effect on the success path."),
    "REQ-3001": dict(
        section="23. Result Operations",
        summary="`expect(message)` on an `Err` raises a runtime fault reporting the message "
                "together with the carried error",
        kind="runtime",
        quotes=["On an `Err` it raises a runtime fault reporting `message` together with the "
                "carried error (section 23.1)."],
        note="The receiver is an `Err`, so the call faults at run time with the protocol's "
             "`RESULT_WRONG_VARIANT` category. A compile-time rejection cannot satisfy the "
             "RUNTIME_ERROR expectation."),
    "REQ-3002": dict(
        section="23. Result Operations",
        summary="The `expect` message is evaluated exactly once whenever the call runs, on either "
                "variant",
        kind="runtime",
        quotes=["The message must be assignable to `String` and is evaluated exactly once whenever "
                "the call runs, on either variant."],
        note="The message argument is a function that prints a marker, so the expected bytes show "
             "one evaluation before the returned payload; a double evaluation would print the "
             "marker twice."),
    "REQ-3003": dict(
        section="23. Result Operations",
        summary="`ignore` evaluates its receiver exactly once, discards the value, and yields "
                "`Unit`, so it is a well-formed standalone statement",
        kind="runtime",
        quotes=["`ignore` evaluates its receiver exactly once, discards the value, and yields "
                "`Unit`."],
        note="A side-effecting function returns a `Result`; `ignore()` is bound to a `Unit` local, "
             "so the receiver runs once and the trailing print shows execution continued. A "
             "non-`Unit` result would not be assignable to the declared `Unit` local."),
    "REQ-3004": dict(
        section="23. Result Operations",
        summary="`isOk` and `isErr` are complementary variant tests that never fault",
        kind="runtime",
        quotes=["`isOk` and `isErr` are complementary tests over the variant. They never fault."],
        note="Both tests are read on one `Ok` value; the printed `truefalse` shows they report "
             "opposite verdicts without faulting."),
    "REQ-3005": dict(
        section="23. Result Operations",
        summary="`unwrapErr` returns the error payload of an `Err`",
        kind="runtime",
        quotes=["`unwrapErr` returns the error payload of an `Err`. On an `Ok` it raises a runtime "
                "fault (section 23.1)."],
        note="The receiver is an `Err` and `unwrapErr` returns its carried error, which is printed. "
             "The `Ok`-fault half is covered by REQ-0206.",
    ),
}

TESTS = []


def T(tid, cat, req, src, outcome, exp, note):
    for q in REQS_SPEC[req]["quotes"]:
        if norm(q) not in SPEC_N:
            raise SystemExit("%s: quote not verbatim in spec: %r" % (tid, q[:90]))
    TESTS.append(dict(tid=tid, cat=cat, req=req, src=src, outcome=outcome, exp=exp, note=note))


def OK(tid, cat, req, src, stdout, note):
    T(tid, cat, req, src, "SUCCESS",
      {"languageExit": 0, "stdoutBase64": base64.b64encode(stdout.encode()).decode()}, note)


def RTE(tid, cat, req, src, category, note):
    T(tid, cat, req, src, "RUNTIME_ERROR",
      {"runtimeCategory": category, "stdoutBase64": ""}, note)


OK("SOL-TCK-0399", "result", "REQ-3000",
   RESULT + 'func get(): Result<Integer, String> {\n    return Result.Ok(5)\n}\n'
   'val r = get()\nprint("exp" .. r.expect("msg"))\n',
   "exp5",
   "expect on an Ok returns the success payload and ignores the message.")
RTE("SOL-TCK-0400", "result", "REQ-3001",
    RESULT + 'func get(): Result<Integer, String> {\n    return Result.Err("boom")\n}\n'
    'val r = get()\nprint(r.expect("custom"))\n',
    "RESULT_WRONG_VARIANT",
    "expect on an Err faults at run time with the wrong-variant category.")
OK("SOL-TCK-0401", "result", "REQ-3002",
   RESULT + 'func msg(): String {\n    print("m")\n    return "x"\n}\n'
   'func get(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n'
   'val r = get()\nprint(r.expect(msg()))\n',
   "m1",
   "The message expression prints its marker once before the Ok payload, so it was evaluated "
   "exactly once.")
OK("SOL-TCK-0402", "result", "REQ-3003",
   RESULT + 'func probe(): Result<Integer, String> {\n    print("p")\n    return Result.Ok(1)\n}\n'
   'val u: Unit = probe().ignore()\nprint("done")\n',
   "pdone",
   "ignore evaluates the receiver once and yields Unit, which binds to the declared Unit local.")
OK("SOL-TCK-0403", "result", "REQ-3004",
   RESULT + 'func get(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n'
   'val r = get()\nprint(r.isOk())\nprint(r.isErr())\n',
   "truefalse",
   "isOk and isErr report opposite verdicts on the same value without faulting.")
OK("SOL-TCK-0404", "result", "REQ-3005",
   RESULT + 'func get(): Result<Integer, String> {\n    return Result.Err("e")\n}\n'
   'print(get().unwrapErr())\n',
   "e",
   "unwrapErr on an Err returns the carried error payload.")


def verify():
    bad = []
    for t in TESTS:
        if t["req"] not in REQS_SPEC:
            bad.append(("REQ", t["tid"], t["req"]))
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
