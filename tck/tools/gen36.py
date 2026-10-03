#!/usr/bin/env python3
"""Record the one `2026.11-draft` function-value obligation no *portable* test can ever exercise.

Section 6 fixes what a function value reports at the interoperability boundary. Unlike the rest of
that revision's function-value surface, this clause's observable lives in the *host*: the assertion is
an embedding host executing a guest value and reading back whether the value reported itself executable,
and a guest program can neither print nor assert either half of it. So this is not a gap a later corpus
batch closes; it waits on an embedded-API suite, and `status` `untested-portable` records exactly that:
portable in the sense that the contract is host-independent, untested in the sense that no portable
program can witness it.

It was recorded alongside four sibling obligations that shared its subject -- a function *value* -- but
not its obstacle. Those four were untestable only because the repository produced no function values at
all; once phase 2 produced them they became ordinary guest programs, and `tck/tools/gen37.py` now owns
their records as `tested`:

  * REQ-3307 identity-bearing equality, hash, and `func` display;
  * REQ-3309 contravariant parameters, covariant results, and no widening inside assignability;
  * REQ-3310 the `Any` top, the function-type join, and invariance of function-typed type arguments;
  * REQ-3311 structural identity confined to function types.

This tool retains the one clause whose obstacle was never the missing value:

  * REQ-3308 -- a non-null function value reports itself executable to a host and a nullable one does
    not, and a host execution reaches the same call target a guest call would (section 6).

Recording REQ-3308 in the revision that adopted the clause rather than with the suite that witnesses it
is deliberate: the inventory is the statement of what a revision demands, and a revision that changed
what a host sees at the interop boundary while listing no obligation for it would understate its own
blast radius -- the failure mode `tck/tools/gen30.py` exists to prevent. The clause's remaining
testable relatives were recorded the same way and are now tested.

STATUS TODAY. The witness this record names exists. `SolvikInteropTest` asserts executability for every
kind of function value, executes a named value from a host and compares both the result and the call
target with the guest call, asserts a nullable function value holding no value reports no executability,
and asserts a wrong host argument count fires the internal arity invariant in both directions. None of
that fills the record's `tests` list, which holds portable manifest ids, so the clause stays
`untested-portable` and keeps appearing in the conformance report's `untestedRequirements`; `report.py`
treats a requirement gap -- like the draft baseline itself -- as withholding certification, so
`fullProfileConformance` reads `NOT_EVALUATED` rather than `PASS`. Retiring the record takes a
requirement kind the corpus profile does not currently have, and the change introducing one must say so
rather than quietly reclassifying the clause.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.11-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

REQS_SPEC = {
    "REQ-3308": dict(
        section="6. Functions (function values)",
        summary="At the interoperability boundary a non-null function value reports itself executable "
                "and a nullable one does not, and a host execution enforces the function's arity and "
                "reaches the same call target a guest call would",
        quotes=[
            "At the interoperation boundary a non-null function value reports itself as executable. "
            "Host execution enforces the function's arity as an internal runtime invariant and invokes "
            "the same call target as guest execution; guest source never relies on that runtime "
            "check, because semantic analysis rejects a bad arity before execution. Function "
            "parameter and return type metadata need not be reflectively exposed to hosts.",
            "A direct call whose target is statically known keeps its existing statically resolved "
            "path.",
        ],
        rationale="This is a host-side contract: the observable is an embedding host calling a guest "
                  "value through the interoperability protocol, which no guest program can print or "
                  "assert. Its witness is therefore not merely a function value but one that reaches "
                  "a host, so it stays `untested-portable` rather than `untested-platform`: the "
                  "contract is host-independent even though the harness is not a guest program. The "
                  "in-process witness exists: `SolvikInteropTest` executes a named function "
                  "value from a host against the declaration's own call target, asserts a nullable "
                  "function value holding no value reports no executability, and asserts a wrong host "
                  "argument count fires the internal arity invariant in both directions. The empty "
                  "`tests` list is permanent, not pending: it holds portable manifest ids, and the "
                  "launcher protocol gives a host no guest value to hold, so no manifest for this "
                  "clause can ever exist.",
    ),
}


def verify():
    bad = []
    for rid, spec in sorted(REQS_SPEC.items()):
        if len(spec["quotes"]) < 2:
            bad.append(("THIN", rid, "a requirement must quote at least two sentences"))
        for q in spec["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append(("QUOTE", rid, q[:80]))
    return bad


def main():
    bad = verify()
    if bad:
        for kind, rid, detail in bad:
            print("%s %s: %s" % (kind, rid, detail))
        return 1

    data = json.load(open(REQUIREMENTS, encoding="utf-8"))
    have = {r["id"] for r in data["requirements"]}
    byid = {r["id"]: r for r in data["requirements"]}
    for rid, spec in sorted(REQS_SPEC.items()):
        # Earlier drafts prefixed this text with "No portable test can exercise this yet." The
        # "yet" promised a manifest that can never exist, so the rationale now states its own
        # permanence and the record carries no prefix.
        note = spec["rationale"]
        assert len(note) <= 2048, "%s rationale exceeds the schema bound" % rid
        record = {
            "id": rid, "specVersion": SPEC_VERSION, "section": spec["section"],
            "summary": spec["summary"], "kind": "compile-time", "profile": "full-language",
            "portable": True, "tests": [], "status": "untested-portable", "lifecycle": "active",
            # The schema requires a `rationale` on an untested record; gen30.py's convention repeats
            # it as `oracleNotes`, so the two fields stay one string and one source of truth.
            "rationale": note, "oracleNotes": note,
            "normativeQuotes": spec["quotes"]}
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
    print("recorded %d deferred requirements" % len(REQS_SPEC))
    return 0


if __name__ == "__main__":
    sys.exit(main())
