#!/usr/bin/env python3
"""Convert the function-value obligations that phase 2 made observable into tested requirements.

`tck/tools/gen36.py` recorded five obligations of the `2026.10-draft` function-value revision as
`untested-portable`, because their subject is a function *value* and the repository could not produce
one. It also recorded the conversion obligation those five carry: the phase that adds function values
must, in the same change that makes each behaviour observable, give the ids a real `tests` list and
status `tested`, with oracles derived from the same quoted passages. This tool discharges that
obligation for four of the five and leaves the fifth where it belongs.

The conversion is a handoff and not an edit. This tool writes these four requirement records, so
`tck/tools/gen36.py` stops claiming them; running gen36.py first and gen37.py second reproduces the
committed inventory either way, because each tool verifies the records it does not own rather than
rewriting them. `tck/tools/verify_regen.py` keeps that ordering honest.

What each converted requirement now asserts:

  * REQ-3307 -- a function type is identity-bearing, so a concrete function type and its nullable form
    are valid `===` operands, `Any` stays rejected without refinement, semantic equality and
    `hashCode()` are reference identity and its hash, and every rendering is `func` (sections 3 and 6);
  * REQ-3309 -- function-type assignability is contravariant in parameters and covariant in the result,
    and numeric widening is never applied inside it while ordinary call-site widening still applies to
    arguments supplied at an indirect call (section 6);
  * REQ-3310 -- every non-null function type has `Any` as its top supertype, a join of same-shape
    function types is callable and a join of unrelated ones manufactures nothing, and generic type
    arguments containing function types stay invariant in both directions (section 6);
  * REQ-3311 -- structural identity is confined to function types: a value of one function type fills a
    binding typed by another written from an unrelated declaration and keeps reference identity, while
    two member-identical classes remain assignment-incompatible (sections 3 and 6).

REQ-3308 stays `untested-portable`, deliberately and permanently so far as this phase is concerned. Its
observable is an *embedding host* calling a guest value through the interoperability protocol and
checking that a nullable function value does not report itself executable. No guest program can print
or assert that: the assertion lives in the host, not in the guest's stdout, so its witness belongs to an
embedded-API suite rather than to this corpus. `tck/tools/gen36.py` states the same reason in its
rationale; converting it here would mean writing a guest program that merely mentions a function value
and calling that a host-interop test, which is exactly the substitution the `untested-portable` state
exists to make impossible.

On diagnostic codes. Every rejection here is pinned to an exact code, and only because section 6 names
each one verbatim: `SOLV-TYPE-001` for a non-assignable *static declaration initializer*,
`SOLV-TYPE-002` for "an invocation whose callee is not a function type", and `SOLV-TYPE-039` for "a
compatible pair with no identity-bearing operand". That naming is also why four of the five rejection
programs are written as static-property initializers rather than the `val` locals a hand-written test
would use: the section pins the code for the static placement, and the project rule forbids adopting a
code from the implementation for a placement the section leaves unnamed. The accepted halves of those
same requirements are ordinary executable programs, so their oracles are real stdout.

Where a program carries a rejection, it also carries a positive half in the same file, and the sentinel
`EXECUTED-INVALID` proves the rejection was not merely reported after execution.
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

BEARING = "The identity-bearing static types are exactly:"
BEARING_ITEM = "- function types (section 6);"
VALID_OPERANDS = ("A function value is identity-bearing, so a concrete function type and its nullable "
                  "form are valid operands of `===` and `!==` when the ordinary compatibility rule also "
                  "holds.")
ANY_STAYS = ("`Any` remains invalid for identity operations without refinement, as it is for every "
             "other identity-bearing runtime value.")
FIXED_EQ = ("Semantic equality for function values is reference identity, and `hashCode()` is the "
            "matching reference-identity hash. These operations are fixed and cannot be overridden.")
DISPLAY = ("`toString()` for every function value returns the exact string `func`. It must not expose a "
           "Java class name, memory address, node name, module path, captured values, or implementation "
           "details, so `print`, `println`, and `..` render every function value as `func`.")
CANONICAL = "Every reference evaluation to the same declared top-level function produces the same canonical"
TYPE039 = ("A failure of assignability uses the ordinary invalid-operand diagnostic; a compatible pair "
           "with no identity-bearing operand uses `SOLV-TYPE-039`.")

CONTRAVARIANT = ("Function-type assignability is contravariant in parameters and covariant in the "
                 "result.")
ANIMAL_DOG = ("Given `open class Animal` and `class Dog extends Animal`, a value of type func(Animal): "
              "Dog is assignable to func(Dog): Animal, and a value of type func(Dog): Animal is not "
              "assignable to func(Animal): Dog.")
NO_WIDEN = ("Numeric widening is not a subtype relation (section 4) and is never applied inside "
            "function-type assignability: a function accepting `Long` is not assignable to a function "
            "type accepting `Integer` merely because an `Integer` argument may widen at an ordinary "
            "conversion site.")
CALLSITE_WIDEN = ("Arguments supplied when a function value is invoked still receive the ordinary "
                  "call-site widening rules.")
STATIC_INIT_CODE = "A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`."

ANY_TOP = ("Every non-null function type has `Any` as its top supertype, and a nullable function type "
           "relates to another under those same rules.")
JOIN_RULE = ("The shared type join understands function types: for two same-arity function types each "
             "joined parameter takes the more specific of the two when one is assignable to the other, "
             "and the joined result is their nearest common result type. That joined function type is "
             "the least common function supertype allowed by contravariant parameters and covariant "
             "results.")
NO_MANUFACTURE = ("When a parameter pair is unrelated or the results have no unique join, no "
                  "function-type join exists and the ordinary join may still select a shared nominal "
                  "supertype such as `Any`; a join never introduces `Nothing`, a union, or an "
                  "intersection in order to manufacture a function supertype.")
INVARIANT = ("Generic type arguments remain invariant, so `List<func(Dog): Animal>` and "
             "`List<func(Animal): Dog>` are unrelated applications even though the function types "
             "inside them are comparable.")
TYPE002 = ("An invocation whose callee is not a function type is `SOLV-TYPE-002`, and a call with the "
           "wrong number of arguments is `SOLV-TYPE-003`.")

STRUCTURAL = ("Two function types are identical when they have the same number of parameters, "
              "corresponding parameter types are identical, and their return types are identical. The "
              "declarations that produced values of those types do not affect type identity.")
CONFINED = ("Structural comparison is confined to function types: two unrelated classes with identical "
            "members remain assignment-incompatible (section 3).")

REQS_SPEC = {
    "REQ-3307": dict(
        section="3. Equality and reference identity (function values)",
        kind="compile-time",
        summary="A function type is identity-bearing, so `===` accepts a concrete function type against "
                "its nullable form and keeps rejecting an unrefined `Any` pair, and semantic equality, "
                "`hashCode()`, and every rendering are the fixed reference-identity and `func` "
                "operations",
        quotes=[BEARING, BEARING_ITEM, VALID_OPERANDS, ANY_STAYS, FIXED_EQ, DISPLAY, TYPE039, CANONICAL],
        note="SOL-TCK-0426 is the accepted half. `optional === format` compares a nullable function "
             "type against the concrete type the same declaration yields, which the identity-bearing "
             "list now permits, and is true because every reference to one declared function produces "
             "the same canonical value. `optional === null` is false, so the permitted null literal is "
             "permitted and not forced. `format === describe` is false across two declarations while "
             "`hashCode()` agrees for one value, which is the fixed reference-identity equality and its "
             "matching hash -- the specification fixes only that equal values share a hash, so no test "
             "claims two distinct declarations hash differently. A function value is also passed where "
             "`Any` is expected and concatenated, and prints `func` through `println`, `..`, and "
             "`toString()`, so no display path leaks a node name or Java class. SOL-TCK-0427 is the "
             "retained rejection: two `Any` bindings, one holding a function value, still have no "
             "identity-bearing operand, and section 6 names `SOLV-TYPE-039` verbatim for exactly a "
             "compatible pair with none, so the code is pinned; the sentinel proves non-execution.",
    ),
    "REQ-3309": dict(
        section="6. Functions (structural identity and assignability)",
        kind="compile-time",
        summary="Function-type assignability is contravariant in parameters and covariant in the "
                "result, numeric widening is never applied inside it, and ordinary call-site widening "
                "still applies to arguments supplied at an indirect call",
        quotes=[CONTRAVARIANT, ANIMAL_DOG, NO_WIDEN, CALLSITE_WIDEN, STATIC_INIT_CODE],
        note="Three tests, one clause each. SOL-TCK-0428 binds the section's accepted Animal/Dog "
             "direction -- a `func(Animal): Dog` value into a `func(Dog): Animal` binding -- and "
             "invokes it, so the accepted direction is not merely typed but callable; it then calls a "
             "`func(Long): Long` value with an `Integer` literal, which is the call-site widening the "
             "same section permits, and prints both results. SOL-TCK-0429 writes the rejected "
             "direction, a `func(Dog): Animal` value against a `func(Animal): Dog` binding, as a static "
             "declaration initializer -- the placement whose diagnostic the section names, so "
             "`SOLV-TYPE-001` is pinned rather than merely observed; the sentinel proves non-execution. "
             "SOL-TCK-0430 isolates the widening clause: a `func(Long): Long` value against a "
             "`func(Integer): Integer` binding is refused with the same pinned code even though an "
             "`Integer` argument widens at an ordinary conversion site, and the pair is written with a "
             "`Long` result so no other clause could explain the rejection.",
    ),
    "REQ-3310": dict(
        section="6. Functions (structural identity and assignability)",
        kind="compile-time",
        summary="Every non-null function type has `Any` as its top supertype, the shared type join "
                "produces a callable function type for two same-shape function types and manufactures "
                "no function supertype for an unrelated pair, and generic type arguments containing "
                "function types remain invariant in both directions",
        quotes=[ANY_TOP, JOIN_RULE, NO_MANUFACTURE, INVARIANT, TYPE002, STATIC_INIT_CODE],
        note="Three tests. SOL-TCK-0431 assigns a function value to `Any`, passes it to an `Any` "
             "parameter, and joins two branches of identical parameter and result types -- the joined "
             "value stays callable and both renderings print, so the `Any` top and the understood join "
             "are both observed by execution. SOL-TCK-0432 joins a `func(Integer): String` against a "
             "`func(Unrelated): String`, whose parameter pair is unrelated: no function-type join "
             "exists, the ordinary join selects `Any`, and because a join may not manufacture a "
             "function supertype the value cannot be invoked. That invocation is the defect, and "
             "section 6 names `SOLV-TYPE-002` verbatim for an invocation whose callee is not a function "
             "type, so it is pinned; the sentinel proves non-execution. SOL-TCK-0433 carries the "
             "invariance clause in both directions as two static declaration initializers, since that "
             "is the placement whose diagnostic the section names, so `SOLV-TYPE-001` is pinned for "
             "each; the two declarations that supply the sources are written with explicit type "
             "arguments so the rejection can only come from invariance and not from inference.",
    ),
    "REQ-3311": dict(
        section="3. Static and Strong Typing / 6. Functions (structural identity)",
        kind="compile-time",
        summary="Structural identity is confined to function types: a value of one written function "
                "type fills a binding typed from an unrelated declaration and keeps reference identity, "
                "while two classes with identical members remain assignment-incompatible",
        quotes=[STRUCTURAL, CONFINED, FIXED_EQ, STATIC_INIT_CODE],
        note="Two tests, one per half, so each has an oracle of its own rather than one program whose "
             "rejection hides its acceptance. SOL-TCK-0434 is the structural half: `format` and "
             "`render` are unrelated declarations, and `render` fills a `func(Integer): String` binding "
             "while a second binding of the same written type is `===` to it, which is the clause that "
             "the declarations that produced values of those types do not affect type identity, read "
             "together with reference-identity equality. SOL-TCK-0435 is the boundary: `Left` and "
             "`Right` declare the same property name and type and the same method, and assigning one to "
             "the other as a static declaration initializer is refused with `SOLV-TYPE-001`, the code "
             "the section names for that placement; the sentinel proves non-execution. Together they "
             "are the assertion that the revision's new structural rule did not leak out of function "
             "types, which no single rejected program could show on its own.",
    ),
}

TESTS = [
    dict(
        tid="SOL-TCK-0426", req="REQ-3307", cat="equality", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(
                 b"v1d1funcfunc\ntrue\nfalse\nfalse\ntrue\nfunc\nfunc|func\n").decode("ascii")},
        note="A nullable function value is `===` to the concrete value of the same declaration and not "
             "to `null`; two declarations are not `===` to each other while one value's hash matches "
             "itself; and `println`, `..`, and `toString()` all render `func`.",
        src="""func format(value: Integer): String {
    return "v" .. value.toString()
}

func describe(value: Integer): String {
    return "d" .. value.toString()
}

func makeFormatter(): func(Integer): String {
    return format
}

func take(operation: func(Integer): String): String {
    return operation(1)
}

func takeAny(value: Any): String {
    return value.toString()
}

val asAny: Any = format
val branch: Any = makeFormatter()
val optional: (func(Integer): String)? = format
print(take(makeFormatter()) .. take(describe) .. takeAny(asAny) .. takeAny(branch) .. "\\n")
println(optional === format)
println(optional === null)
println(format === describe)
println(format.hashCode() == format.hashCode())
println(format)
println("" .. format .. "|" .. format.toString())
""",
    ),
    dict(
        tid="SOL-TCK-0427", req="REQ-3307", cat="equality", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-039"}},
        note="Two `Any` bindings, one holding a function value, remain a compatible pair with no "
             "identity-bearing operand, which the section pins to `SOLV-TYPE-039`.",
        src="""func format(value: Integer): String {
    return "v" .. value.toString()
}

val boxed: Any = format
val other: Any = 1
print(boxed === other)
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0428", req="REQ-3309", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0, "stdoutBase64": base64.b64encode(b"animal-2").decode("ascii")},
        note="The section's accepted Animal/Dog direction is invoked, and a `func(Long): Long` value is "
             "called with an `Integer` literal, which is ordinary call-site widening.",
        src="""open class Animal {
    val name: String = "animal"
}

class Dog extends Animal {
}

func toDog(animal: Animal): Dog {
    return Dog()
}

func nameOf(value: Animal): String {
    return value.name
}

func widenResult(value: Long): Long {
    return value + 1
}

// The section's own accepted direction: source `func(Animal): Dog` against target
// `func(Dog): Animal`. Parameters are contravariant, so the target's `Dog` must be
// assignable to the source's `Animal`, and the result is covariant, so the source's `Dog`
// result is assignable to the target's `Animal`.
val accepted: func(Dog): Animal = toDog

// Call-site widening is a separate rule from assignability and still applies through a
// function value: this passes an `Integer` literal to a `Long` parameter.
val takesLong: func(Long): Long = widenResult

print(nameOf(accepted(Dog())) .. "-" .. takesLong(1))
""",
    ),
    dict(
        tid="SOL-TCK-0429", req="REQ-3309", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-001"}},
        note="The rejected direction of the section's Animal/Dog pair, written as a static declaration "
             "initializer so the pinned code applies to the placement the section names.",
        src="""open class Animal {
    val name: String = "animal"
}

class Dog extends Animal {
}

func toAnimal(dog: Dog): Animal {
    return dog
}

// The section's rejected pair: source `func(Dog): Animal` against target
// `func(Animal): Dog`. Contravariant parameters require the target's `Animal` to be
// assignable to the source's `Dog` and covariant results require `Animal` to be
// assignable to `Dog`; neither holds. Written as a static initializer because that is
// the placement whose diagnostic the section names verbatim.
class Boundary {
    static val reversed: func(Animal): Dog = toAnimal
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0430", req="REQ-3309", cat="numerics", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-001"}},
        note="A `func(Long): Long` value is not assignable to `func(Integer): Integer`: widening an "
             "`Integer` argument at a conversion site does not relate the two function types.",
        src="""func widenResult(value: Long): Long {
    return value + 1
}

// Numeric widening is not a subtype relation and is never applied inside function-type
// assignability: an `Integer` argument may widen at an ordinary conversion site, and that
// does not make this source's `Long` parameter match the target's `Integer` one. Written
// as a static initializer because that is the placement whose diagnostic the section names
// verbatim.
class Boundary {
    static val widened: func(Integer): Integer = widenResult
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0431", req="REQ-3310", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0, "stdoutBase64": base64.b64encode(b"func|func|v2").decode("ascii")},
        note="A function value is assigned to `Any` and passed to an `Any` parameter, and two branches "
             "of identical function type join to a value that is still callable.",
        src="""func format(value: Integer): String {
    return "v" .. value.toString()
}

func describe(value: Integer): String {
    return "d" .. value.toString()
}

func takeAny(value: Any): String {
    return value.toString()
}

// Every non-null function type has `Any` as its top supertype, so a function value is
// assignable to `Any` and may be passed where `Any` is expected.
val asAny: Any = format

// The shared type join understands function types: these two branches have identical
// parameter types and identical result types, so the join is that function type and the
// joined value stays callable.
val flag: Boolean = true
val joined = if (flag) { format } else { describe }

print(takeAny(asAny) .. "|" .. takeAny(joined) .. "|" .. joined(2))
""",
    ),
    dict(
        tid="SOL-TCK-0432", req="REQ-3310", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-002"}},
        note="Two branches with unrelated parameters have no function-type join, so the join selects "
             "`Any` and cannot manufacture a callable function type; invoking it is the pinned code.",
        src="""func format(value: Integer): String {
    return "v" .. value.toString()
}

class Unrelated {
    val tag: String = "u"
}

func fromUnrelated(value: Unrelated): String {
    return value.tag
}

// The two branch types have unrelated parameters, so no function-type join exists and the
// ordinary join selects a shared nominal supertype instead. A join never introduces
// `Nothing`, a union, or an intersection to manufacture a function supertype, so the joined
// value is not of a function type and invoking it is an invocation whose callee is not a
// function type -- the code the section names verbatim for that shape.
val flag: Boolean = true
val joined = if (flag) { format } else { fromUnrelated }
print(joined(1))
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0433", req="REQ-3310", cat="generics", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-001"}},
        note="Both directions between `List<func(Animal): Dog>` and `List<func(Dog): Animal>` are "
             "refused, because generic type arguments stay invariant even where the element types are "
             "comparable. The sources carry explicit type arguments so inference cannot explain it.",
        src="""open class Animal {
    val name: String = "animal"
}

class Dog extends Animal {
}

// Generic type arguments remain invariant, so these two applications are unrelated even
// though `func(Animal): Dog` is assignable to `func(Dog): Animal`. Both directions are
// static initializers, the placement whose non-assignable diagnostic the section names
// verbatim, so each direction is pinned.
class Invariant {
    static val wide: List<func(Animal): Dog> = List<func(Animal): Dog>()
    static val narrow: List<func(Dog): Animal> = List<func(Dog): Animal>()
    static val intoNarrow: List<func(Dog): Animal> = Invariant.wide
    static val intoWide: List<func(Animal): Dog> = Invariant.narrow
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0434", req="REQ-3311", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0, "stdoutBase64": base64.b64encode(b"v1-r1-r1-true").decode("ascii")},
        note="A `render` value fills a binding of the function type written from an unrelated "
             "declaration's shape and stays `===` to a second binding of that written type, so the "
             "producing declaration is not part of function-type identity.",
        src="""func format(value: Integer): String {
    return "v" .. value.toString()
}

func render(value: Integer): String {
    return "r" .. value.toString()
}

func through(callback: func(Integer): String): String {
    return callback(1)
}

// Function types are structural, and the declarations that produced values of those types
// do not affect type identity: `format` and `render` are unrelated declarations, and a
// value of one is assignable to a binding of the other's type.
val substituted: func(Integer): String = render

// Two values of one written function type compare by reference identity, not by which
// declaration produced them, so a structural substitution does not change identity.
val alsoSubstituted: func(Integer): String = render
print(through(format) .. "-" .. through(substituted) .. "-" .. through(alsoSubstituted) .. "-" .. (substituted === alsoSubstituted))
""",
    ),
    dict(
        tid="SOL-TCK-0435", req="REQ-3311", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-001"}},
        note="`Left` and `Right` declare the same property name and type and the same method and remain "
             "assignment-incompatible, which is the boundary on structural comparison as a rule that "
             "belongs to function types alone.",
        src="""func format(value: Integer): String {
    return "v" .. value.toString()
}

class Left {
    val name: String = "left"

    func value(): Integer {
        return 1
    }
}

class Right {
    val name: String = "left"

    func value(): Integer {
        return 1
    }
}

// Structural comparison is confined to function types. `Left` and `Right` declare the same
// property name and type and the same method, and remain assignment-incompatible; that
// nominal half is the non-assignable static initializer, which the section pins.
class Boundary {
    static val copied: Right = Left()
}

print(format(1))
print("EXECUTED-INVALID")
""",
    ),
]


def verify():
    bad = []
    for rid, spec in sorted(REQS_SPEC.items()):
        if len(spec["quotes"]) < 2:
            bad.append(("THIN", rid, "a requirement must quote at least two sentences"))
        for q in spec["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append(("QUOTE", rid, q[:80]))
    for t in TESTS:
        if t["req"] not in REQS_SPEC:
            bad.append(("REQ", t["tid"], t["req"]))
        if "print(" not in t["src"]:
            bad.append(("NO-OBSERVABLE", t["tid"], ""))
        if t["outcome"] == "COMPILE_ERROR" and "EXECUTED-INVALID" not in t["src"]:
            bad.append(("NO-SENTINEL", t["tid"], ""))
        if t["outcome"] == "COMPILE_ERROR" and "code" not in t["exp"]["diagnostic"]:
            bad.append(("UNPINNED", t["tid"], "every rejection here is named by section 6"))
    seen = {}
    for t in TESTS:
        payload = re.sub(r"\s+", "", t["src"])
        if payload in seen:
            bad.append(("DUP", t["tid"], seen[payload]))
        seen[payload] = t["tid"]
    return bad


def main():
    bad = verify()
    if bad:
        for kind, rid, detail in bad:
            print("%s %s: %s" % (kind, rid, detail))
        return 1
    byreq = {}
    for t in TESTS:
        byreq.setdefault(t["req"], []).append(t["tid"])

    data = json.load(open(REQUIREMENTS, encoding="utf-8"))
    have = {r["id"] for r in data["requirements"]}
    byid = {r["id"]: r for r in data["requirements"]}

    # The four records this tool owns must not also be claimed by gen36.py, whose
    # `untested-portable` form they replace. It writes them only when absent, so with
    # this guard the two tools can run in either order and neither overwrites the other.
    gen36 = open(os.path.join(ROOT, "tck", "tools", "gen36.py"), encoding="utf-8").read()
    for rid in sorted(REQS_SPEC):
        if '"%s"' % rid in gen36:
            print("%s is still claimed by gen36.py" % rid)
            return 1

    for rid, spec in sorted(REQS_SPEC.items()):
        assert byreq.get(rid), "%s has no test" % rid
        record = {
            "id": rid, "specVersion": SPEC_VERSION, "section": spec["section"],
            "summary": spec["summary"], "kind": spec["kind"], "profile": "full-language",
            "portable": True, "tests": sorted(byreq[rid]), "status": "tested",
            "lifecycle": "active", "oracleNotes": spec["note"], "normativeQuotes": spec["quotes"]}
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
