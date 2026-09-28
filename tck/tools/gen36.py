#!/usr/bin/env python3
"""Record the two `2026.10-draft` function-value obligations that no test can exercise yet.

The revision's section 3 makes a function type identity-bearing and section 6 fixes what a function
value reports at the interoperability boundary. Neither is testable in the revision that adopts them,
because each one's subject is a *function value* and this repository does not yet produce one: a bare
read of a declaration is still refused.

They are recorded with `status` `untested-portable`, the state `tck/tools/gen30.py` uses for a
portable obligation the current language cannot express, and the inventory reports them as the
coverage gap they are rather than letting them disappear. Each rationale names the witness the next
phase owes, so the gap cannot be closed by assertion.

Recording them now rather than with that phase is deliberate: the inventory is the statement of what
a revision demands, and a revision that changed which types are identity-bearing and what a host sees
at the interop boundary while listing no obligation for either would understate its own blast radius
-- the failure mode `gen30.py` exists to prevent. The rest of the revision's function-type surface is
testable today and is recorded by `tck/tools/gen35.py`, including the non-reifiability rule, whose
`is`/`as` targets can be written against a non-function operand.

  * REQ-3307 -- a function type is identity-bearing, and function-value equality, hash, and display
    are the fixed reference-identity and `func` operations (sections 3 and 6);
  * REQ-3308 -- a non-null function value reports itself executable to a host and a nullable one does
    not, and a host execution reaches the same call target a guest call would (section 6);
  * REQ-3309 -- function-type assignability is contravariant in parameters and covariant in the
    result, and numeric widening is never applied inside it (section 6);
  * REQ-3310 -- every non-null function type has `Any` as its supertype, a join never manufactures a
    function supertype, and generic arguments containing function types stay invariant (section 6);
  * REQ-3311 -- structural comparison is confined to function types, so two unrelated classes with
    identical members stay assignment-incompatible (sections 3 and 6).

REQ-3309 to REQ-3311 are the assignability half of the revision. Each is a relation between two
function *types* whose only guest-visible witness is an assignment or a call that needs a value of
one of them, so all three wait on the same phase. REQ-3311 is the boundary on the new structural
rule and is recorded rather than skipped because an implementation that let structural comparison
leak back out of function types would satisfy every other test in the batch.

CONVERSION OBLIGATION. These five records are the revision's outstanding debt, and the profile
records it: full-language conformance reports `blocked` while any requirement is untested. The
phase that adds function values as runtime entities (FIRST_CLASS_FUNCTIONS.md phase 2) must, in the
same change that makes each behaviour observable, give these ids a real `tests` list and status
`tested`, derive the oracle from the same quoted passages, and re-run this generator -- leaving the
records untested once the feature ships would mean the conformance status no longer describes the
implementation.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.10-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

REQS_SPEC = {
    "REQ-3307": dict(
        section="3. Equality and reference identity (function values)",
        summary="A function type is identity-bearing, so `===` and `!==` accept a concrete function "
                "type and its nullable form, and semantic equality, `hashCode()`, and `toString()` "
                "are the fixed reference-identity and `func` operations that no class may override",
        quotes=[
            "The identity-bearing static types are exactly:",
            "- function types (section 6);",
            "A function value is identity-bearing, so a concrete function type and its nullable form "
            "are valid operands of `===` and `!==` when the ordinary compatibility rule also holds.",
            "`Any` remains invalid for identity operations without refinement, as it is for every "
            "other identity-bearing runtime value.",
            "Semantic equality for function values is reference identity, and `hashCode()` is the "
            "matching reference-identity hash. These operations are fixed and cannot be overridden.",
            "`toString()` for every function value returns the exact string `func`. It must not "
            "expose a Java class name, memory address, node name, module path, captured values, or "
            "implementation details, so `print`, `println`, and `..` render every function value as "
            "`func`.",
        ],
        rationale="Every arm needs a function value as an operand: the accepted pair is a concrete "
                  "function type against its nullable form, and the fixed equality, hash, and display "
                  "are observable only by comparing or rendering values that section 6 declares "
                  "distinct. Section 3 already enumerates the bearing types and the existing suite "
                  "pins the rejection of every non-bearing pair, so what this revision changes is the "
                  "set, and nothing can observe that change while a declaration cannot be read as a "
                  "value. The phase that produces canonical named function values owes: the accepted "
                  "`===` between a function type and its nullable form, the retained `Any` rejection, "
                  "`false` for two distinct values, and the `func` rendering through `print` and `..`.",
    ),
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
                  "phase that produces function values owes an embedded-host test that executes a "
                  "guest function value from the host and checks that a nullable one does not report "
                  "executable, in the embedded-API suite rather than the corpus.",
    ),
    "REQ-3309": dict(
        section="6. Functions (structural identity and assignability)",
        summary="Function-type assignability is contravariant in parameters and covariant in the "
                "result, and numeric widening is never applied inside function-type assignability",
        quotes=[
            "Function-type assignability is contravariant in parameters and covariant in the result.",
            "Given `open class Animal` and `class Dog extends Animal`, a value of type "
            "func(Animal): Dog is assignable to func(Dog): Animal, and a value of type func(Dog): "
            "Animal is not assignable to func(Animal): Dog.",
            "Numeric widening is not a subtype relation (section 4) and is never applied inside "
            "function-type assignability: a function accepting `Long` is not assignable to a "
            "function type accepting `Integer` merely because an `Integer` argument may widen at an "
            "ordinary conversion site.",
            "Arguments supplied when a function value is invoked still receive the ordinary "
            "call-site widening rules.",
        ],
        rationale="Assignability is observed by producing a value of the source type and assigning it "
                  "to a binding of the target type, and both directions of the Animal/Dog pair need "
                  "such a value; the negative direction is the whole content of the rule and cannot "
                  "be stated as a program today. The type model implements and unit-tests the "
                  "relation, but a TCK oracle is a guest program and no guest program can yet write "
                  "one. The phase that produces function values owes the section's own Animal/Dog "
                  "example accepted in one direction and rejected in the other, plus a "
                  "`func(Long): Unit` value rejected against `func(Integer): Unit`, and separately "
                  "the argument-widening permission at an indirect call.",
    ),
    "REQ-3310": dict(
        section="6. Functions (structural identity and assignability)",
        summary="Every non-null function type has `Any` as its supertype, a join never manufactures a "
                "function supertype, and generic type arguments containing function types remain "
                "invariant",
        quotes=[
            "Every non-null function type has `Any` as its top supertype, and a nullable function "
            "type relates to another under those same rules.",
            "a join never introduces `Nothing`, a union, or an intersection in order to manufacture "
            "a function supertype",
            "Generic type arguments remain invariant, so `List<func(Dog): Animal>` and "
            "`List<func(Animal): Dog>` are unrelated applications even though the function types "
            "inside them are comparable.",
        ],
        rationale="Each clause needs a function-typed value to relate: assigning one to `Any` and "
                  "passing it where `Any` is expected, joining two function-typed branches to observe "
                  "that an unrelated pair lands on `Any` rather than a manufactured type, and moving "
                  "a `List` between two comparable-but-invariant element types. The type model's "
                  "`superType()` and join are unit-tested today; the guest-observable form is not. "
                  "The phase that produces function values owes the `Any` assignment, the "
                  "unrelated-branch join rendering through `Any`'s restrictions, and the two "
                  "unrelated `List` applications in both directions.",
    ),
    "REQ-3311": dict(
        section="3. Static and Strong Typing / 6. Functions (structural identity)",
        summary="Structural comparison is confined to function types, so two unrelated classes with "
                "identical members remain assignment-incompatible",
        quotes=[
            "Structural comparison is confined to function types: two unrelated classes with "
            "identical members remain assignment-incompatible (section 3).",
            "Two function types are identical when they have the same number of parameters, "
            "corresponding parameter types are identical, and their return types are identical. The "
            "declarations that produced values of those types do not affect type identity.",
        ],
        rationale="This clause is the boundary on the revision's new structural rule, and its subject "
                  "is a pair of nominal classes rather than a function value. The nominal half is "
                  "already tested in isolation (REQ-1606 rejects equality between unrelated nominal "
                  "types, and the implementation-signature and assignment batches reject assignment), "
                  "so what is missing is the contrast in one program: an accepted assignment between "
                  "two comparable function types beside a rejected assignment between two "
                  "member-identical classes. That contrast is the assertion that structural "
                  "comparison did not leak, and it needs function values to state. Recorded until the "
                  "phase that produces them, at which point one program carries both halves.",
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
        note = "No portable test can exercise this yet. " + spec["rationale"]
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
