#!/usr/bin/env python3
"""Generate the section 12 (enums, abstract classes, exhaustive match) TCK batch.

Oracles are DERIVED FROM LANGUAGE_SPEC.md and only then compared against the
implementation. Each expectation is written from the specification's own text before any
probe of the built launcher; a mismatch is investigated as a candidate implementation
defect, never resolved by copying observed output into the expectation.

Section 12 is the largest spec section with zero requirement coverage, and it is almost
entirely without named diagnostics: `SOLV-SEM-029` and `SOLV-SEM-030` occur ZERO times in
the specification, even though the implementation emits them for the non-exhaustive match
and unreachable branch rules that section 12 states. Every one of those rejections
therefore carries a bare `{}` expectation, because section 12's prose forces the rejection
but never names a code for it, and adopting an implementation-chosen code would make the
TCK's oracle a transcription of the very implementation it exists to judge.

Two codes ARE pinnable and are pinned:
  * `SOLV-SEM-028`, which section 12 names for constructing an `abstract` class.
  * nothing else. In particular the match join-rejection rejections are BARE, following the
    precedent already recorded for SOL-TCK-0135: the specification names SOLV-TYPE-001 for a
    *static* declaration initializer that is not assignable to its declared type, and not for
    a local initializer, so no code is mandated at a local binding site.

The 2026.11-draft revision rewrote this section, and three requirements carry that history
in their notes rather than being deleted. No class type has a knowable subtype set, so a
`match` over one is exhaustive only through a wildcard branch, and an `abstract` supertype
may be extended from any file, including one brought in by `include`. `SOLV-SEM-039`, which
2026.10-draft named for a subclass in another physical file, no longer exists and nothing
here asserts it.

Where a rule is stated positively but its rejection has no code, the rejection is still
asserted -- as a bare rejection -- and the pair is what makes the positive test mean
something. Several tests are deliberately constructed as discriminating PAIRS so that a
plausible alternative implementation fails one arm rather than agreeing by accident:

  * first-match-wins is pinned by writing the two branches in BOTH orders. With a transitive
    hierarchy, specific-first compiles and prints the specific marker; base-first must be
    REJECTED because the specific branch is unreachable once the base branch covers it. An
    implementation that picked the LAST matching branch, or sorted branches by specificity,
    would have to accept the base-first program and print the other marker. Reachability is
    decided by type subsumption, which is why it survives the loss of class exhaustiveness;
    the wildcard both arms now carry is what keeps unreachability the only reason to reject
    the base-first one.
  * exhaustiveness is pinned over a plain enum both ways: an omitted variant is rejected and
    a wildcard covering it is accepted, so "covered" cannot mean merely that some branch
    matched at runtime.
  * class-type exhaustiveness is pinned both ways too: a class-typed match with no wildcard
    is rejected, and the same match with `_` added is accepted. Before 2026.11-draft the
    first arm was rejected for omitting a subtype rather than for omitting a wildcard.
  * the file boundary is pinned by an `abstract` supertype in one physical file extended from
    an included one, accepted, alongside a same-file control. Under 2026.10-draft the pair
    ran the other way; what it still pins is that the physical boundary does not decide
    extension, so an implementation that kept the old file rule fails the included arm.
  * branch scope isolation is pinned by a POSITIVE test in which every branch declares a
    binding with the SAME name, which only compiles if each branch body is its own scope; a
    shared scope would make the second declaration a redeclaration error.

The `..` concatenation operator is used instead of `+` throughout. Sections 8 and 12 both
show `+` applied to String, which section 3 normatively forbids; that conflict is recorded
elsewhere and blocks only oracles that copy the `+` style, not the rules tested here.
"""
import base64, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

# ---------------------------------------------------------------- requirements
SECTION = "12. Enums, Abstract Classes, and Exhaustive Match"
REQS = {
 "REQ-1500": dict(
  section=SECTION,
  summary="An enum variant is a nested nominal constructor: outside a context that already establishes the enum type it must be qualified as EnumName.Variant, and an unqualified variant name is not visible",
  kind="compile-time",
  notes="The acceptance prints through the qualified spelling, and the rejection uses the bare name in the same position, so the pair pins qualification rather than merely the existence of the variant. The rejection is asserted bare: section 12 states that variants are nested and must be qualified outside an establishing context but names no diagnostic, and the resolver's own unknown-name code belongs to section 7's name-resolution rule, which is already covered separately.",
  quotes=["Enum variants are nested nominal constructors. Outside a context that already establishes the enum type, qualify them as `Result.Ok(value)`."]),
 "REQ-1501": dict(
  section=SECTION,
  summary="Inside a match over a known enum the unqualified variant pattern is permitted, so a match may bind variant payloads without re-qualifying the enum name",
  kind="compile-time",
  notes="This is the counterpart half of REQ-1500, asserted from the same sentence, and it must be a separate program because the same spelling is legal in one context and illegal in the other -- a single program could not distinguish a lexer from a scoping rule. Both branches bind a payload and produce a distinct rendered string, so the test also shows the binding actually carries the payload value rather than the arm merely being accepted.",
  quotes=["Inside a `match` over a known enum, `Ok(value)` is permitted."]),
 "REQ-1502": dict(
  section=SECTION,
  summary="A match over a closed variant set must cover every variant unless a wildcard pattern is present, and missing a known variant is a compile-time error",
  kind="compile-time",
  notes="Three arms pin one rule so that 'covered' cannot be read as 'some branch happened to match at runtime': an omitted variant must be rejected, the same program with the variant restored must be accepted, and a wildcard standing in for the missing variant must be accepted because the specification explicitly permits it. A closed variant set is known only for `enum` and `error` types, which is why every arm here is over an enum and the class-typed counterpart is REQ-1504. Both named diagnostics for this area are unnamed in the specification, so the rejection is bare.",
  quotes=["`match` is expression-oriented and exhaustive where the compiler knows a closed variant set.",
          "Missing a known enum variant is a compile-time error unless a wildcard pattern handles it.",
          "every known variant must be covered unless `_` is present"]),
 "REQ-1503": dict(
  section=SECTION,
  summary="Branches are checked in source order and duplicate or unreachable branches are errors, so a branch shadowed by an earlier one is rejected while the same branches ordered specific-first are accepted",
  kind="compile-time",
  notes="The pair is the whole point: a transitive hierarchy is matched with the most specific branch first (accepted, and the specific marker is printed, so first-match-wins is observed rather than assumed) and then with the base branch first (rejected, because the specific branch is unreachable once the base branch covers it). An implementation selecting the last or the most specific matching branch rather than the first would accept the second program and print the other marker in the first. Reachability is decided by type subsumption independently of exhaustiveness, which is what keeps this requirement meaningful now that a class-typed match no longer has a closed set to cover; both arms carry the wildcard that a class-typed match requires, so unreachability is the only difference between them. The unreachable-branch diagnostic is not named in the specification, so the rejection is bare.",
  quotes=["Branches are checked in source order, duplicate or unreachable branches are errors",
          "Branch reachability is decided by type subsumption and is independent of exhaustiveness"]),
 "REQ-1504": dict(
  section=SECTION,
  summary="An abstract class is not constructible, and a match whose matched type is a class type is exhaustive only when it has a wildcard branch",
  kind="compile-time",
  notes="Two rules the 2026.11-draft revision separated, pinned together because both are about what an `abstract` class does and does not license. Constructing it is rejected with the code section 12 names, and constructing a subtype instead is the accepted control. The exhaustiveness half is the revision's substantive change: before it, a match over an `abstract` class was exhaustive over the class's closed transitive subtype set and a wildcard was one way to satisfy that; now no class type has a subtype set at all, so the wildcard is mandatory and a class-typed match without one is rejected however many subtypes it names. The accepted arm adds only `_`, which is what makes the rejection mean 'no wildcard' rather than 'matching a class is broken'.",
  quotes=["An `abstract class` is not constructible: naming it as a constructor is `SOLV-SEM-028`, and a program must construct one of its subtypes instead.",
          "A class type — `abstract`, `mutable`, or otherwise — has no knowable subtype set, so a `match` whose matched type is a class type is exhaustive only when it has a wildcard branch.",
          "`match` over an `abstract` class type is therefore legal and useful, and it always requires `_`."]),
 "REQ-1505": dict(
  section=SECTION,
  summary="An abstract class may be extended from any file including one brought in by include, so the physical file boundary does not decide whether a subclass is legal",
  kind="compile-time",
  notes="Asserted from a genuine two-file fixture, because the rule is ABOUT the physical file boundary and a single-file program cannot test it. The revision changed the direction: 2026.10-draft confined a subclass to the supertype's own physical file and named SOLV-SEM-039 for a subclass elsewhere, and that code no longer exists. What the requirement still pins is that the boundary is inert, so an implementation that kept the old file rule rejects the included arm and fails. A same-file control in the following requirement is what makes this mean 'the boundary does not matter' rather than 'subclassing happens to work here'.",
  quotes=["An `abstract` class may be extended from any file, including a file brought in by `include`."]),
 "REQ-1506": dict(
  section=SECTION,
  summary="An abstract subclass declared in the same physical file is legal, so the same-file case is accepted as plainly as the cross-file one",
  kind="compile-time",
  notes="The control arm for REQ-1505 and the reason it is a separate requirement: without it, an implementation that rejected all subclassing of an abstract class would fail only one test rather than being shown inconsistent. The accepted program constructs the subclass and matches on it, so the subclass is shown to be a usable type rather than merely a declaration that parses.",
  quotes=["An `abstract` class may be extended from any file, including a file brought in by `include`."]),
 "REQ-1507": dict(
  section=SECTION,
  summary="Initial match patterns are enum variant patterns, binding patterns of the form name: Type, and wildcard underscore",
  kind="compile-time",
  notes="The three admitted forms are each exercised, with the binding form observed printing a value reached through the binding rather than a constant, so the binding is shown to carry the matched value. Together the forms are the closed set the specification admits, and no fourth form is asserted absent, because the specification's word 'initial' records a scope boundary rather than a prohibition on future forms.",
  quotes=["Initial `match` patterns are enum variant patterns, binding patterns of the form `name: Type`, and wildcard `_`."]),
 "REQ-1508": dict(
  section=SECTION,
  summary="The result type of a match is the nearest common declared supertype to which every branch result is assignable; if none exists the match is ill-typed",
  kind="compile-time",
  notes="Pinned from both sides of the join rule and deliberately NOT pinned to a diagnostic code: the join produces Number for the Integer and Long case, which is accepted and observed through a declared Number binding, while binding the same construct to Integer is rejected. Following the precedent recorded for SOL-TCK-0135, the specification names SOLV-TYPE-001 for a static declaration initializer that is not assignable to its declared type and not for a local initializer, so the rejection here is bare. The accepted arm is what distinguishes a real least-upper-bound computation from an implementation that rejects every heterogeneous match.",
  quotes=["The result type is the nearest common declared supertype to which every branch result is assignable; if none exists, the match is ill-typed."]),
 "REQ-1509": dict(
  section=SECTION,
  summary="A match branch result is an expression, so a branch may use a brace-delimited block for multiple statements followed by a tail result",
  kind="runtime",
  notes="Each branch emits a distinct side effect and a distinct tail value, and the expected stream is the side effect followed by the value, which pins both the tail-result rule and branch-local scope at once: both branches declare a binding with the same name, which only compiles if each branch body is its own scope, since a shared scope would make the second declaration a redeclaration error. The expected bytes are derived from section 5's rule that print appends no separator, so the concatenation is computed rather than observed.",
  quotes=["Each branch result is an expression; because a block is an expression (section 21), a branch may use a brace-delimited block for multiple statements followed by a tail result."]),
}

# ---------------------------------------------------------------- test programs
# Rejections append a sentinel that must never execute, so a false acceptance cannot hide
# behind an otherwise-matching exit status.
NEG = '\nprint("EXECUTED-INVALID")\n'

ENUM = "enum Color {\n    RED\n    GREEN\n}\n"
RES = "enum Result<T, E> {\n    Ok(T)\n    Err(E)\n}\n"
# Two sibling subtypes of an abstract supertype. Neither is extended, so nothing here depends
# on a closed set; the wildcard each match carries is what makes them legal (section 12).
SIB = ("abstract class Shape {\n}\n"
       "class Sq extends Shape {\n}\n"
       "class Ci extends Shape {\n}\n")
# A transitive hierarchy. Classes are final by default (section 7), so the intermediate class
# must be declared `mutable` for the deeper subtype to extend it.
TRANS = ("abstract class A {\n}\n"
         "mutable class B extends A {\n}\n"
         "class C extends B {\n}\n")

S = {}
EXPECT = {}
CATEGORY = {}
REQ_FOR = {}
FIXTURE = {}


def add(tid, req, category, src, outcome, fixture=None, **exp):
    S[tid] = src
    CATEGORY[tid] = category
    REQ_FOR[tid] = req
    EXPECT[tid] = {"outcome": outcome, **exp}
    if fixture:
        FIXTURE[tid] = fixture


# --- REQ-1500: variants are nested nominal constructors, qualified outside an
#     establishing context.
add("SOL-TCK-0185", "REQ-1500", "enums",
    ENUM + 'val c = Color.RED\nprint("qualified")\n', "SUCCESS", stdout="qualified")

add("SOL-TCK-0186", "REQ-1500", "enums",
    ENUM + "val c = RED" + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1501: inside a match over a known enum the unqualified pattern is permitted.
add("SOL-TCK-0187", "REQ-1501", "match",
    RES + 'val r: Result<Integer, String> = Result.Ok(42)\n'
          'val m = match r {\n'
          '    Ok(v) => "ok" .. v\n'
          '    Err(e) => "err" .. e\n'
          '}\n'
          'print(m)\n', "SUCCESS", stdout="ok42")

add("SOL-TCK-0188", "REQ-1501", "match",
    RES + 'val r: Result<Integer, String> = Result.Err("bad")\n'
          'val m = match r {\n'
          '    Ok(v) => "ok" .. v\n'
          '    Err(e) => "err" .. e\n'
          '}\n'
          'print(m)\n', "SUCCESS", stdout="errbad")

# --- REQ-1502: exhaustive over a closed variant set, unless a wildcard covers it.
add("SOL-TCK-0189", "REQ-1502", "match",
    ENUM + "val c = Color.GREEN\n"
           "val n = match c {\n    RED => 1\n}\nprint(n)\n" + NEG,
    "COMPILE_ERROR", diag={})

add("SOL-TCK-0190", "REQ-1502", "match",
    ENUM + "val c = Color.GREEN\n"
           "val n = match c {\n    RED => 1\n    GREEN => 2\n}\nprint(\"all\" .. n)\n",
    "SUCCESS", stdout="all2")

add("SOL-TCK-0191", "REQ-1502", "match",
    ENUM + "val c = Color.GREEN\n"
           "val n = match c {\n    _ => 9\n}\nprint(\"wild\" .. n)\n",
    "SUCCESS", stdout="wild9")

# --- REQ-1503: source order; a branch shadowed by an earlier one is an error. Both arms
#     carry the wildcard a class-typed match requires, so reachability by subsumption is the
#     only thing distinguishing them.
add("SOL-TCK-0192", "REQ-1503", "match",
    TRANS + "val a: A = C()\n"
            "val n = match a {\n    c: C => 10\n    b: B => 20\n    _ => 0\n}\nprint(\"first\" .. n)\n",
    "SUCCESS", stdout="first10")

add("SOL-TCK-0193", "REQ-1503", "match",
    TRANS + "val a: A = C()\n"
            "val n = match a {\n    b: B => 20\n    c: C => 10\n    _ => 0\n}\nprint(n)\n" + NEG,
    "COMPILE_ERROR", diag={})

add("SOL-TCK-0194", "REQ-1503", "match",
    ENUM + "val c = Color.RED\n"
           "val n = match c {\n    RED => 1\n    RED => 2\n    GREEN => 3\n}\nprint(n)\n" + NEG,
    "COMPILE_ERROR", diag={})

# --- REQ-1504: an abstract class is not constructible, and a class-typed match needs `_`.
add("SOL-TCK-0195", "REQ-1504", "abstract",
    SIB + "val s: Shape = Sq()\n"
          "val n = match s {\n    q: Sq => 1\n    c: Ci => 2\n}\nprint(n)\n" + NEG,
    "COMPILE_ERROR", diag={})

add("SOL-TCK-0196", "REQ-1504", "abstract",
    SIB + "val s: Shape = Ci()\n"
          "val n = match s {\n    q: Sq => 1\n    _ => 0\n}\nprint(\"abstract\" .. n)\n",
    "SUCCESS", stdout="abstract0")

add("SOL-TCK-0197", "REQ-1504", "abstract",
    SIB + "val s = Shape()" + NEG, "COMPILE_ERROR",
    diag={"family": "SEM", "code": "SOLV-SEM-028"})

# --- REQ-1505 / REQ-1506: the physical file boundary is inert.
add("SOL-TCK-0198", "REQ-1505", "abstract",
    'include "lib/base.sol"\n'
    "class Sub extends Base {\n}\n"
    "val s: Base = Sub()\nprint(\"included\")\n",
    "SUCCESS",
    fixture={"lib/base.sol": "abstract class Base {\n}\n"},
    stdout="included")

add("SOL-TCK-0199", "REQ-1506", "abstract",
    "abstract class Base {\n}\n"
    "class Sub extends Base {\n}\n"
    'val s: Base = Sub()\nval n = match s {\n    sub: Sub => 4\n    _ => 0\n}\nprint("same" .. n)\n',
    "SUCCESS", stdout="same4")

# --- REQ-1507: the admitted pattern forms.
add("SOL-TCK-0200", "REQ-1507", "match",
    SIB + "val s: Shape = Ci()\n"
          "val n = match s {\n    q: Sq => 1\n    c: Ci => 2\n    _ => 0\n}\nprint(\"both\" .. n)\n",
    "SUCCESS", stdout="both2")

add("SOL-TCK-0201", "REQ-1507", "match",
    ENUM + "val c = Color.RED\n"
           "val n = match c {\n    RED => 1\n    GREEN => 2\n}\nprint(\"enum\" .. n)\n",
    "SUCCESS", stdout="enum1")

add("SOL-TCK-0202", "REQ-1507", "match",
    SIB + "val s: Shape = Ci()\n"
          "val n = match s {\n    _ => 5\n}\nprint(\"under\" .. n)\n",
    "SUCCESS", stdout="under5")

# --- REQ-1508: join is the nearest common declared supertype.
add("SOL-TCK-0203", "REQ-1508", "match",
    ENUM + "val c = Color.RED\n"
           "val v: Number = match c {\n    RED => 1\n    GREEN => 2L\n}\nprint(\"join\" .. v)\n",
    "SUCCESS", stdout="join1")

add("SOL-TCK-0204", "REQ-1508", "match",
    ENUM + "val c = Color.RED\n"
           "val v: Integer = match c {\n    RED => 1\n    GREEN => 2L\n}\nprint(v)\n" + NEG,
    "COMPILE_ERROR", diag={})

# --- REQ-1509: a branch may be a block with a tail result; branch bodies have their
#     own scope (both branches bind the same name `t`).
add("SOL-TCK-0205", "REQ-1509", "match",
    ENUM + 'val c = Color.GREEN\n'
           'val n = match c {\n'
           '    RED => {\n'
           '        val t = 1\n'
           '        print("r")\n'
           '        t\n'
           '    }\n'
           '    GREEN => {\n'
           '        val t = 2\n'
           '        print("g")\n'
           '        t\n'
           '    }\n'
           '}\n'
           'print(n)\n',
    "SUCCESS", stdout="g2")


def verify_quotes():
    bad = []
    for rid, r in REQS.items():
        for q in r["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append((rid, q))
    return bad


def main():
    bad = verify_quotes()
    if bad:
        for rid, q in bad:
            print("QUOTE NOT IN SPEC %s: %r" % (rid, q))
        sys.exit(1)
    print("all %d normative quotes verified verbatim in LANGUAGE_SPEC.md"
          % sum(len(r["quotes"]) for r in REQS.values()))
    for tid, src in sorted(S.items()):
        d = os.path.join(CORPUS, tid)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "main.sol"), "w") as fh:
            fh.write(src)
        e = EXPECT[tid]
        man = {
            "manifestSchemaVersion": 1, "specVersion": "2026.11-draft", "testId": tid,
            "category": CATEGORY[tid], "profile": "full-language", "status": "required",
            "requirements": [REQ_FOR[tid]], "entryPoint": "main.sol",
            "outcome": e["outcome"], "expectation": (
                {"languageExit": 0, "stdoutBase64":
                 base64.b64encode(e["stdout"].encode()).decode()}
                if e["outcome"] == "SUCCESS" else {"diagnostic": e["diag"]}),
        }
        if tid in FIXTURE:
            man["fixtureRoot"] = "."
            for rel, body in sorted(FIXTURE[tid].items()):
                sub = os.path.join(d, os.path.dirname(rel))
                if sub != d:
                    os.makedirs(sub, exist_ok=True)
                with open(os.path.join(d, rel), "w") as fh:
                    fh.write(body)
        with open(os.path.join(d, tid + ".manifest.json"), "w") as fh:
            fh.write(json.dumps(man, indent=2) + "\n")
    print("wrote %d test directories, %d requirements" % (len(S), len(REQS)))


if __name__ == "__main__":
    main()
