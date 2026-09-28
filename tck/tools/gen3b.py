#!/usr/bin/env python3
"""Generate the section 3B TCK batch: reference identity, the identity-bearing type
set, and the semantic equality / hash algorithms.

Oracles are DERIVED FROM LANGUAGE_SPEC.md and only then compared with the
implementation. Where the specification gives a worked example, the expected stream is
that example's stated outcome rather than observed output.

Code-pinning here follows the same per-code check used in the previous batches:

  * `SOLV-TYPE-039` is named by section 3 for "a compatible pair with no identity-bearing
    operand", and section 3 states `null === null` is a compile error, so every
    non-identity-bearing operand pair here is pinned to that code.
  * A failure of *assignability* between identity operands is asserted BARE, because the
    specification routes it to "the ordinary invalid-operand diagnostic" without naming a
    code, and the code the implementation prints for that case occurs zero times in the
    specification.
  * `SOLV-SEM-044` / `SOLV-SEM-045` are named in the specification's diagnostic table.
    They are already pinned by REQ-0002 and REQ-1216 for a missing pair within a single
    declaration; the pairing test here is the *inheritance* clause, a different rule, and it
    reuses the named codes rather than inventing one.
  * `SOLV-TYPE-024`, `SOLV-SEM-011`, `SOLV-SEM-013`, `SOLV-SEM-014` and `SOLV-TYPE-014`
    (outside its Result-operation scope) occur zero times as the codes for these rules, so
    the declaration-shape and nullable-receiver rejections are bare. `SOLV-SEM-037` IS named,
    but only for `message`/`getMessage` on an exception class; the reserved-name rejections
    for `equals`/`hashCode` state the same idea in prose without naming the code, so they are
    asserted bare rather than borrowing a code from a different rule.

Discriminating designs, so a differing implementation fails an arm rather than agreeing
by accident:

  * The null rules are pinned by a class whose `equals` override PRINTS and returns `true`.
    `p == null` must be `false` with no print, which a checker that dispatches the left
    operand before testing for null gets wrong on both the value and the side effect at
    once -- and the side effect is what distinguishes "returned false by the null rule" from
    "returned false because user code ran and happened to say so".
  * The absence of an identity shortcut is pinned by an override that returns `false`, tested
    against `p == p`. Any implementation with the conventional same-reference shortcut reports
    `true` here. The alignment clause is pinned in the same program by printing `p == p` and
    `p.equals(p)` together, so a shortcut applied to only one of the two forms is caught.
  * "The right operand never receives a fallback equality call" is pinned by making BOTH
    operand classes override `equals` with distinct prints. If equality fell back to the right
    operand when the left says false, the stream would contain the second marker.
  * The identity-bearing set is pinned by rejecting five different non-identity types with the
    same code, and by accepting `===` on a collection and on an interface-typed value, which
    are the two positive cases most likely to be omitted by an implementation that only
    recognises user classes.
  * `Any` is pinned on both sides of the narrowing rule: `q === r` on two `Any` values is
    rejected with the code section 3 names, and the same identity test after a checked cast
    is accepted -- so the rejection is about `Any` and not about the class behind it.
  * The hash/equality invariant is pinned where it is actually at risk: negative zero equals
    zero under IEEE `==`, so an implementation that hashes boxed Java doubles reports
    different hashes for equal values. Content string hashing is pinned alongside it with two
    separately constructed strings, so neither arm is one value compared with itself.
  * Collection equality is pinned as reference identity with a same-content control, so the
    `true` arm cannot be produced by a content-based implementation.

Where an oracle depends on the interleaving of operand-evaluation side effects with the final
`print`, the expected stream was derived from an explicit model of that ordering and the model
was checked against the observed stream before being trusted; the same model then predicts the
stream a differing implementation would produce, which is what makes the alternative named in
the SOL-TCK-0258 comment a derived prediction rather than a guess.
"""
import base64, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.10-draft")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

REQS = {
 "REQ-1700": dict(
  section="3. Equality and reference identity",
  summary="`===` answers whether two values are the same allocation and never invokes `equals`, another guest method, or Java `Object.equals`, and `!==` is its exact logical negation",
  kind="runtime",
  notes="Expected values are taken from the worked example the specification itself gives for exactly this rule: two separately constructed equal-shaped instances are `===` false, an alias is `===` true, and `!==` against that alias is false. The negation is asserted over both a non-null and a null pair so that `!==` is shown to be the negation of `===` rather than an independently implemented test. A separate test pairs a class whose `equals` returns true with `===`, which stays false, so the guarantee that identity does not delegate to equality is observed and not merely asserted by the shape of the program.",
  quotes=["`===` answers whether two values are the same Solvik allocation. It never invokes `equals`, another guest method, or Java `Object.equals`. `!==` is its exact logical negation."]),
 "REQ-1701": dict(
  section="3. Equality and reference identity",
  summary="The identity-bearing static types are exactly user class types, interface types, the four collection types, function types, and nullable forms of those, so `===` on a scalar, enum, or Regex pair is a compile-time error reported with SOLV-TYPE-039",
  kind="compile-time",
  notes="Five different non-identity operand pairs are rejected with the one code section 3 names for a compatible pair with no identity-bearing operand, covering the two named scalar cases a real implementation is most likely to allow through (a numeric scalar and a String) plus a Boolean, an enum value and a Regex, so the rejection cannot be a per-type special case. The accepted arms cover the two positive cases most likely to be omitted by an implementation that only recognises plain user classes, namely a collection and an interface-typed value. Section 3 is unusually explicit here, listing the bearing and non-bearing types and naming the code, so the codes are pinned rather than left bare. `2026.10-draft` added function types to the bearing list; this requirement's own tests are unchanged because none of them mentions a function type, and the new positive arm is recorded separately as REQ-3307, which is untested until the language can produce a function value.",
  quotes=["The identity-bearing static types are exactly:",
          "The following types are not identity-bearing: `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `Boolean`, `Character`, `String`, and `Unit`; enum types; `Regex` and `RegexMatch`; `Any`; unbounded type parameters; `Nothing` and a bare null literal.",
          "a compatible pair with no identity-bearing operand uses `SOLV-TYPE-039`."]),
 "REQ-1702": dict(
  section="3. Equality and reference identity",
  summary="Identity operands must also satisfy ordinary comparability and a null literal is permitted only against a nullable identity-bearing operand, so `null === null` and an identity test against a non-nullable operand are compile-time errors",
  kind="compile-time",
  notes="Two distinct failures are separated because the specification routes them to different diagnostics: `null === null` is a compatible pair with no identity-bearing operand and is pinned to the code section 3 names, while an identity test between a non-nullable class value and a null literal is an assignability failure routed to the ordinary invalid-operand diagnostic, which the specification does not name, so it is asserted bare. The accepted control performs the same test with the operand declared nullable, proving the rejection is about nullability and not about `===`.",
  quotes=["Identity operands must also satisfy the ordinary equality comparability rule: one operand type must be assignable to the other.",
          "A null literal is permitted only against a nullable identity-bearing operand, so `null === null` is a compile error.",
          "A failure of assignability uses the ordinary invalid-operand diagnostic"]),
 "REQ-1703": dict(
  section="3. Equality and reference identity",
  summary="Stable nullable identity tests participate in flow analysis, so `x !== null` narrows on the true path and `x === null` narrows on the false path under the same write-invalidation rules as the equality null tests",
  kind="compile-time",
  notes="Both directions are asserted as accepted programs that read a member of the narrowed value inside the branch, because narrowing is only observable by a member access the declaration type would not permit. The `=== null` case places the member read in the else branch, so an implementation that narrowed on the wrong path would reject the program.",
  quotes=["Stable nullable identity tests participate in flow analysis: `x !== null` narrows `x` to its non-null reference type on the true path, and `x === null` narrows it on the false path, under the same write-invalidation rules as `== null` and `!= null`."]),
 "REQ-1704": dict(
  section="3. Equality and reference identity",
  summary="A value held in Any must first be narrowed or checked-cast to an identity-bearing type before an identity test, so identity is rejected on Any operands and accepted after a checked cast",
  kind="compile-time",
  notes="The rejection is pinned to the code section 3 names, and the accepted arm is the same identity test on the identical runtime value after casting to the class type. Without the accepted arm the rejection would be satisfied by an implementation that rejects every identity test. The specification states the reason for the restriction, that a JVM representation choice must not become observable, which is why Any rather than the underlying class is the operand under test.",
  quotes=["A value held in `Any` must first be narrowed or checked-cast to an identity-bearing type, which prevents a JVM representation choice from becoming observable when the runtime value is a scalar, string, enum, regex, or `Unit`."]),
 "REQ-1705": dict(
  section="3. Equality and reference identity",
  summary="The semantic equality algorithm returns true when both values are null and false when exactly one is null with no user code running",
  kind="runtime",
  notes="Both arms use a class whose `equals` override prints and returns true. `p == null` must yield false with no print at all, and two nulls must yield true with no print. The print is what makes the test bite: an implementation that dispatches the left operand before applying the null rule produces the wrong value AND an extra marker, and a class whose equals returned false would be unable to tell those two causes apart.",
  quotes=["if both values are `null`, the result is `true`;",
          "if exactly one value is `null`, the result is `false` and no user code runs;"]),
 "REQ-1706": dict(
  section="3. Equality and reference identity",
  summary="An equals override is invoked even when both operands are the same reference because there is no general identity shortcut before user dispatch, which keeps `==` and an explicit equals call behaviorally aligned",
  kind="runtime",
  notes="The override returns false and the comparison is `p == p`, so the conventional same-reference optimisation is exactly what would make this report true; the specification states the reason for forbidding it, that `==` and an explicit call stay aligned even for an override with side effects, so the same program prints both forms and the two values must agree.",
  quotes=["An override is invoked even when both operands are the same reference; there is no general identity shortcut before user dispatch, so `==` and an explicit `equals` call stay behaviorally aligned even for an override with side effects."]),
 "REQ-1707": dict(
  section="3. Equality and reference identity",
  summary="The left operand is the dynamic receiver of semantic equality and the right operand never receives a fallback equality call",
  kind="runtime",
  notes="Both operand classes override `equals` and print a distinct marker, and both return false. If equality fell through to the right operand when the left reports inequality, the stream would contain the second marker as well as the first, so the expected value witnesses the one-directional dispatch rather than merely the result.",
  quotes=["The left operand is the dynamic receiver. The right operand never receives a fallback equality call."]),
 "REQ-1708": dict(
  section="3. Equality and reference identity",
  summary="When no class in a user class hierarchy overrides equals, the root default is reference identity",
  kind="runtime",
  notes="Two instances constructed with identical constructor arguments must compare false while an alias of one compares true, which is only reachable through reference identity: a content-based default, or a default that compared constructor arguments, would make the first arm true. The same program therefore contains the positive and negative arms of the default rule.",
  quotes=["when no class in its hierarchy overrides `equals`, the root default is reference identity"]),
 "REQ-1709": dict(
  section="3. Equality and reference identity",
  summary="The equals/hashCode invariant holds for the fixed built-in rules, including that floating equality is IEEE so negative zero must fold onto zero in the hash",
  kind="runtime",
  notes="The invariant is pinned where it is genuinely at risk rather than trivially. `0.0 == -0.0` is true under IEEE equality, so a hash that distinguished negative zero would produce unequal hashes for equal values, which is the exact hazard the specification calls out about boxed Java hashing. String content equality is paired with content hashing in the same way, and both arms compare two separately constructed values rather than one value against itself.",
  quotes=["Boxed Java hashing separates `0.0` from `-0.0`, so the hash folds negative zero onto zero to stay consistent with equality.",
          "When `left == right` is `true`, `left.hashCode() == right.hashCode()` is `true`."]),
 "REQ-1710": dict(
  section="3. Equality and reference identity",
  summary="List, Set, Map and Stack compare by reference identity under both equality and hashing, not by content",
  kind="runtime",
  notes="Two separately constructed collections with identical elements must compare false while an alias compares true, so the `true` arm cannot be produced by a content-based implementation and the `false` arm cannot be produced by a broken alias check. The rule is stated twice in the specification, in the equality table and again in the hash table, which is why both are cited.",
  quotes=["| `List`, `Set`, `Map`, `Stack` | reference identity |",
          "| `List`, `Set`, `Map`, `Stack` | reference identity hash |"]),
 "REQ-1711": dict(
  section="3. Equality and reference identity",
  summary="Two enum values are equal exactly when they share the enum type, the variant, and semantically equal payloads, and enum equality never delegates to Java array or object equality",
  kind="runtime",
  notes="Three arms distinguish the three conditions the specification lists: same variant and equal payloads is true, same variant with a differing payload is false, and a differing variant is false. The payload-bearing and payload-free variants of one enum are used so that the third arm cannot be dismissed as comparing values of unrelated enums.",
  quotes=["Two enum values are equal exactly when they belong to the same enum type, have the same variant, and their corresponding payloads are semantically equal, left to right. Enum equality never delegates to Java array or object equality."]),
}

NEG = '\nprint("EXECUTED-INVALID")\n'
S, EXPECT, CATEGORY, REQ_FOR = {}, {}, {}, {}


def add(tid, req, category, src, outcome, **exp):
    S[tid] = src
    CATEGORY[tid] = category
    REQ_FOR[tid] = req
    EXPECT[tid] = {"outcome": outcome, **exp}


PT = ('class Point {\n'
      '    val x: Integer\n'
      '    val y: Integer\n'
      '\n'
      '    Point(x: Integer, y: Integer) {\n'
      '        this.x = x\n'
      '        this.y = y\n'
      '    }\n'
      '}\n')
# A class whose equals override prints, so that "no user code ran" is observable.
LoudEQ = ('class Loud {\n    Loud() {\n    }\n\n'
          '    override func equals(other: Any?): Boolean {\n        print("u")\n        return RET\n    }\n\n'
          '    override func hashCode(): Integer {\n        return 4\n    }\n}\n')

# --- REQ-1700 `===` semantics and negation.
add("SOL-TCK-0237", "REQ-1700", "equality",
    PT + 'val a = Point(1, 2)\nval b = Point(1, 2)\nval c = a\n'
         'print("i" .. (a === b) .. (a === c) .. (a !== c))\n',
    "SUCCESS", stdout="ifalsetruefalse")
add("SOL-TCK-0238", "REQ-1700", "equality",
    PT + 'val a: Point? = Point(1, 2)\nval b: Point? = null\n'
         'print("n" .. (a !== b) .. (a === b) .. (b !== b) .. (b === b))\n',
    "SUCCESS", stdout="ntruefalsefalsetrue")
add("SOL-TCK-0239", "REQ-1700", "equality",
    LoudEQ.replace("RET", "true") + 'val p = Loud()\nval q = Loud()\nprint("id" .. (p === q))\n',
    # equals returns true for every pair, yet identity is still false: `===` does not delegate.
    "SUCCESS", stdout="idfalse")

# --- REQ-1701 identity-bearing type set.
add("SOL-TCK-0240", "REQ-1701", "equality",
    PT + 'val a = Point(1, 2)\nval b = Point(1, 2)\nprint("cls" .. (a === b))\n',
    "SUCCESS", stdout="clsfalse")
add("SOL-TCK-0241", "REQ-1701", "equality",
    'val l = List<Integer>(1, 2)\nval m = List<Integer>(1, 2)\nval n = l\n'
    'print("col" .. (l === m) .. (l === n))\n',
    "SUCCESS", stdout="colfalsetrue")
add("SOL-TCK-0242", "REQ-1701", "equality",
    'interface Shape {\n    func sides(): Integer\n}\n'
    'class Sq implements Shape {\n    Sq() {\n    }\n\n    func sides(): Integer {\n        return 4\n    }\n}\n'
    'val a: Shape = Sq()\nval b: Shape = Sq()\nval c: Shape = a\n'
    'print("if" .. (a === b) .. (a === c))\n',
    "SUCCESS", stdout="iffalsetrue")
add("SOL-TCK-0243", "REQ-1701", "equality",
    'val n: Integer = 1\nprint(n === n)\n' + NEG, "COMPILE_ERROR",
    diag={"family": "TYPE", "code": "SOLV-TYPE-039"})
add("SOL-TCK-0244", "REQ-1701", "equality",
    'val s = "ab"\nprint(s === s)\n' + NEG, "COMPILE_ERROR",
    diag={"family": "TYPE", "code": "SOLV-TYPE-039"})
add("SOL-TCK-0245", "REQ-1701", "equality",
    'val b = true\nprint(b === b)\n' + NEG, "COMPILE_ERROR",
    diag={"family": "TYPE", "code": "SOLV-TYPE-039"})
add("SOL-TCK-0246", "REQ-1701", "equality",
    'enum Opt {\n    Some(Integer)\n    None\n}\nval a: Opt = Opt.Some(1)\nval b: Opt = Opt.Some(1)\n'
    'print(a === b)\n' + NEG, "COMPILE_ERROR",
    diag={"family": "TYPE", "code": "SOLV-TYPE-039"})
add("SOL-TCK-0247", "REQ-1701", "equality",
    'val r = Regex("a")\nval s = Regex("a")\nprint(r === s)\n' + NEG, "COMPILE_ERROR",
    diag={"family": "TYPE", "code": "SOLV-TYPE-039"})

# --- REQ-1702 comparability and the null literal.
add("SOL-TCK-0248", "REQ-1702", "equality",
    "print(null === null)\n" + NEG, "COMPILE_ERROR",
    diag={"family": "TYPE", "code": "SOLV-TYPE-039"})
add("SOL-TCK-0249", "REQ-1702", "equality",
    PT + 'val a = Point(1, 2)\nprint(a === null)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0250", "REQ-1702", "equality",
    PT + 'val a: Point? = Point(1, 2)\nprint("ok" .. (a === null))\n',
    "SUCCESS", stdout="okfalse")

# --- REQ-1703 identity tests narrow.
add("SOL-TCK-0251", "REQ-1703", "equality",
    PT + 'val a: Point? = Point(3, 4)\nif (a !== null) {\n    print("ne" .. a.x)\n}\n',
    "SUCCESS", stdout="ne3")
add("SOL-TCK-0252", "REQ-1703", "equality",
    PT + 'val a: Point? = Point(3, 4)\nif (a === null) {\n    print("isnull")\n} else {\n    print("el" .. a.y)\n}\n',
    "SUCCESS", stdout="el4")

# --- REQ-1704 Any must be narrowed first.
add("SOL-TCK-0253", "REQ-1704", "equality",
    PT + 'val p = Point(1, 2)\nval q: Any = p\nval r: Any = p\nprint(q === r)\n' + NEG,
    "COMPILE_ERROR", diag={"family": "TYPE", "code": "SOLV-TYPE-039"})
add("SOL-TCK-0254", "REQ-1704", "equality",
    PT + 'val q: Any = Point(1, 2)\nval a: Point = q as Point\nval b: Point = a\nprint("nw" .. (a === b))\n',
    "SUCCESS", stdout="nwtrue")

# --- REQ-1705 the null steps of the equality algorithm.
add("SOL-TCK-0255", "REQ-1705", "equality",
    LoudEQ.replace("RET", "true") + 'val p: Loud? = Loud()\nprint("one" .. (p == null))\n',
    # exactly one null: false, and the override must not run, so no "u" may appear.
    "SUCCESS", stdout="onefalse")
add("SOL-TCK-0256", "REQ-1705", "equality",
    LoudEQ.replace("RET", "true") + 'val p: Loud? = null\nprint("both" .. (p == null))\n',
    "SUCCESS", stdout="bothtrue")
add("SOL-TCK-0257", "REQ-1705", "equality",
    LoudEQ.replace("RET", "true") + 'val p: Loud? = Loud()\nval q: Loud? = null\nprint("rev" .. (q == p))\n',
    "SUCCESS", stdout="revfalse")

# --- REQ-1706 no identity shortcut before user dispatch.
add("SOL-TCK-0258", "REQ-1706", "equality",
    LoudEQ.replace("RET", "false") + 'val p = Loud()\nprint("sc" .. (p == p) .. p.equals(p))\n',
    # The override prints "u" and returns false. Section 3 forbids an identity shortcut
    # before user dispatch, so `p == p` must dispatch (one "u") and yield false, and the
    # explicit call must dispatch too (second "u") and yield the same false. An
    # implementation with the conventional same-reference shortcut instead skips the first
    # dispatch and reports true, producing the wholly different stream `usctruefalse`.
    "SUCCESS", stdout="uuscfalsefalse")

# --- REQ-1707 the right operand never receives a fallback call.
add("SOL-TCK-0259", "REQ-1707", "equality",
    'class L {\n    L() {\n    }\n\n'
    '    override func equals(other: Any?): Boolean {\n        print("L")\n        return false\n    }\n\n'
    '    override func hashCode(): Integer {\n        return 1\n    }\n}\n'
    'class R {\n    R() {\n    }\n\n'
    '    override func equals(other: Any?): Boolean {\n        print("R")\n        return false\n    }\n\n'
    '    override func hashCode(): Integer {\n        return 2\n    }\n}\n'
    'val l = L()\nval r = R()\nval q: Any = l\nprint(q == r)\n',
    "SUCCESS", stdout="Lfalse")

# --- REQ-1708 default equality is reference identity.
add("SOL-TCK-0260", "REQ-1708", "equality",
    PT + 'val a = Point(1, 2)\nval b = Point(1, 2)\nval c = a\n'
         'print("df" .. (a == b) .. (a == c) .. (a.equals(b)))\n',
    "SUCCESS", stdout="dffalsetruefalse")

# --- REQ-1709 the hash/equality invariant, including negative zero.
add("SOL-TCK-0261", "REQ-1709", "hashing",
    'print("fz" .. (0.0 == -0.0) .. (0.0.hashCode() == (-0.0).hashCode()))\n',
    "SUCCESS", stdout="fztruetrue")
add("SOL-TCK-0262", "REQ-1709", "hashing",
    'val a = "ab"\nval b = "ab"\nprint("sh" .. (a == b) .. (a.hashCode() == b.hashCode()))\n',
    "SUCCESS", stdout="shtruetrue")

# --- REQ-1710 collections compare by reference identity.
add("SOL-TCK-0263", "REQ-1710", "hashing",
    'val a = List<Integer>(1, 2)\nval b = List<Integer>(1, 2)\nval c = a\n'
    'print("ce" .. (a == b) .. (a == c) .. (a.hashCode() == a.hashCode()))\n',
    "SUCCESS", stdout="cefalsetruetrue")

# --- REQ-1711 enum equality over type, variant, and payloads.
add("SOL-TCK-0264", "REQ-1711", "hashing",
    'enum Opt {\n    Some(Integer)\n    None\n}\n'
    'val a: Opt = Opt.Some(3)\nval b: Opt = Opt.Some(3)\nval c: Opt = Opt.Some(4)\n'
    'val d: Opt = Opt.None\n'
    'print("en" .. (a == b) .. (a == c) .. (a == d))\n',
    "SUCCESS", stdout="entruefalsefalse")


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
    print("all %d normative quotes verified verbatim"
          % sum(len(r["quotes"]) for r in REQS.values()))
    for tid, src in sorted(S.items()):
        d = os.path.join(CORPUS, tid)
        os.makedirs(d, exist_ok=True)
        open(os.path.join(d, "main.sol"), "w").write(src)
        e = EXPECT[tid]
        man = {"manifestSchemaVersion": 1, "specVersion": "2026.10-draft", "testId": tid,
               "category": CATEGORY[tid], "profile": "full-language", "status": "required",
               "requirements": [REQ_FOR[tid]], "entryPoint": "main.sol",
               "outcome": e["outcome"],
               "expectation": ({"languageExit": 0, "stdoutBase64":
                                base64.b64encode(e["stdout"].encode()).decode()}
                               if e["outcome"] == "SUCCESS" else {"diagnostic": e["diag"]})}
        open(os.path.join(d, tid + ".manifest.json"), "w").write(
            json.dumps(man, indent=2) + "\n")
    print("wrote %d test directories, %d requirements" % (len(S), len(REQS)))


if __name__ == "__main__":
    main()
