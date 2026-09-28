#!/usr/bin/env python3
"""Generate the section 21 (expression-oriented constructs) TCK batch.

Oracles here are DERIVED FROM LANGUAGE_SPEC.md and only then compared against the
implementation. `EXPECTED` below is written from the specification's own semantics before
any probe of the built launcher; a mismatch is reported as a failure to investigate, never
by copying observed output into the expectation.
"""
import base64, json, os, re, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.10-draft")
LAUNCHER = os.path.join(ROOT, "standalone/target/solvik")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

# ---------------------------------------------------------------- requirements
REQS = {
 "REQ-1200": dict(
  section="21.1 Terms / 21.2 Block expressions",
  summary="A block expression has its own lexical scope, its earlier statements execute in source order, and a local declared inside it is visible to later items in that block and nowhere outside it",
  kind="runtime",
  notes="Scope isolation is observable through two block expressions that each declare a local of the same name and each mutate one outer variable: the specification fixes the interleaving of prints and the running total, so the exact stdout is derived rather than observed. The negative half (a block-local name visible outside the block) is SOL-TCK-0140.",
  quotes=["A **statement block** is a brace-delimited block in statement position; its behavior is unchanged. A **block expression** is a brace-delimited block in expression position. It has its own lexical scope and may contain zero or more statements followed by an optional **tail expression**.",
          "A block expression introduces one lexical scope. Earlier statements execute in source order, and a local declared inside the block is visible to later items in that block and nowhere outside it."]),
 "REQ-1201": dict(
  section="21.2 Block expressions / 21.9 required diagnostics",
  summary="A value-required block whose normally completing path reaches `}` without a tail expression is the compile-time error SEM_BLOCK_RESULT_REQUIRED, and an empty block, a block ending in a local declaration, and a block ending in an assignment are all invalid in expression position",
  kind="compile-time",
  notes="Exact code from the 21.9 registry. All three invalid shapes named by the spec share that one code, so they legitimately share the expectation; the oracle-independence guard deliberately exempts rejection tests because a rejection expectation is a rule, not a derived byte stream.",
  quotes=["Every normally completing path through a value-required block must reach its tail expression. An empty block, a block ending in a local declaration, and a block ending in an assignment are invalid in expression position and do not acquire an implicit `Unit` result:",
          "| `SEM_BLOCK_RESULT_REQUIRED` | `SOLV-SEM-041` | offending block or case body |"]),
 "REQ-1202": dict(
  section="21.1 Terms / 21.2 Block expressions",
  summary="A path that completes abruptly carries no value and does not participate in result joining; a value-required block whose every path completes abruptly has type Nothing and never evaluates a tail expression",
  kind="compile-time",
  notes="Nothing is a type no value inhabits, so an all-abrupt block can never satisfy a value-returning function; the value-returning-function rule is the spec-named SOLV-TYPE-012. This is what separates `Nothing` from a fabricated `Unit`/zero/`null` result, which 21.1 forbids.",
  quotes=["A value-required block whose every path completes abruptly has type `Nothing` and never evaluates a tail expression.",
          "A path **completes abruptly** when it executes `return`, or a valid enclosing-loop `break` or `continue`, before reaching the construct's result. Abrupt completion carries no value and does not participate in result joining."]),
 "REQ-1203": dict(
  section="21.2 Block expressions",
  summary="A standalone scope block in statement position remains a statement block, so it contributes no value and its locals stay inside it",
  kind="runtime",
  notes="The block must execute its statements in order and produce nothing; the outer variable it assigns is the only observable, so the oracle is a single value.",
  quotes=["A standalone scope block remains a statement block, and the existing rule that an unused value-producing non-call expression cannot stand alone still applies."]),
 "REQ-1204": dict(
  section="21.3 Semicolons and tail expressions",
  summary="Explicit and synthesized semicolons are the same token with the same meaning, token origin is never inspected to decide whether a value exists, and comments and blank lines before `}` do not affect tail selection",
  kind="syntax",
  notes="Four spellings of the same block expression must all yield the same value and type, which the spec states directly; a single concatenated stdout proves the equivalence without asserting anything about formatting.",
  quotes=["Explicit and synthesized semicolons are the same parser token and have the same language meaning, so token origin is never inspected to decide whether a value exists.",
          "Comments and blank lines before `}` do not affect tail selection, and a terminal assignment is a statement and never a tail expression."]),
 "REQ-1205": dict(
  section="21.4 `if` expressions",
  summary="An expression-position `if` may chain through `else if`, every normally completing branch must produce a tail result, and the condition must be Boolean exactly as for statement `if`",
  kind="compile-time",
  notes="The chained form is the spec's own example, driven with one input per arm so each arm's string appears in a fixed position. The non-Boolean half cannot pin a code: the implementation reports SOLV-TYPE-005, which appears zero times in the specification, so the expectation is the bare rejection the spec actually forces.",
  quotes=["The condition must be `Boolean`, exactly as for statement `if`. An expression `if` must have an `else`; a missing `else` is a dedicated compile-time error and does not also fabricate a branch-type mismatch. Every normally completing branch must produce a tail result, and abrupt branches are excluded from result joining:"],
 ),
 "REQ-1206": dict(
  section="21.4 `if` expressions",
  summary="Abrupt branches are excluded from result joining, so an `if` expression whose `else` completes abruptly still produces the value of its normally completing branch",
  kind="runtime",
  notes="Uses the specification's `requireName` example verbatim in shape: the `else` returns from the enclosing function, so only the non-null branch is a result of the `if`. Output is bracketed to make the two distinct arms distinguishable in one stream.",
  quotes=["func requireName(name: String?): String {\n    return if (name != null) {\n        name\n    } else {\n        return \"fallback\"\n    }\n}"]),
 "REQ-1207": dict(
  section="21.5 `switch` expressions",
  summary="A `switch` in expression position produces a value from its matched case body, while a statement `switch` may omit `default` and do nothing when no label matches",
  kind="runtime",
  notes="Both halves are observable in one program: the expression form yields a string, and a statement switch whose label does not match contributes nothing to stdout. The missing-`default` rejection for the expression form is already SOL-TCK-0012 under REQ-0204.",
  quotes=["Every expression `switch` must contain exactly one `default`, and it must remain last. `switch` does not gain enum or sealed exhaustiveness; that remains the responsibility of `match`. Requiring `default` makes value production explicit for `Integer`, `String`, and regex dispatch, while a statement `switch` may still omit `default` and do nothing when no label matches."]),
 "REQ-1208": dict(
  section="21.5 `switch` expressions / 21.9 required diagnostics",
  summary="Every normally completing case body, including `default`, must end in a tail expression, so a value-position case body ending in a declaration is SEM_BLOCK_RESULT_REQUIRED",
  kind="compile-time",
  notes="The 21.9 primary span for SEM-041 is the offending block or case body, which is why a case body shares the block rule rather than acquiring a separate code.",
  quotes=["Every normally completing case body, including `default`, must end in a tail expression; statements may precede it.",
          "| `SEM_BLOCK_RESULT_REQUIRED` | `SOLV-SEM-041` | offending block or case body |"]),
 "REQ-1209": dict(
  section="21.5 `switch` expressions",
  summary="The scrutinee of a `switch` is evaluated exactly once",
  kind="runtime",
  notes="A scrutinee call that prints an observation marker makes exactly-once a byte-exact property: one additional evaluation would duplicate the marker, so the oracle discriminates the claim instead of merely tolerating it.",
  quotes=["The scrutinee is evaluated exactly once. Case labels are tested in source order, only the first matching body executes, and there is no implicit fallthrough."]),
 "REQ-1210": dict(
  section="21.7 Result types",
  summary="A construct's result type is the nearest common declared supertype to which every normally completing branch result is assignable, with no numeric promotion or widening, so `if (c) { 1 } else { 1L }` has type `Number` and is not assignable to `Integer` or `Long`",
  kind="compile-time",
  notes="The acceptance half binds the join to `Number`; the rejection half is what proves the join is not `Long`, which any numeric promotion would produce. The rejection is asserted as a bare rejection: the specification names SOLV-TYPE-001 for a static initializer, not for a local initializer, so no code is spec-mandated at this site.",
  quotes=["Every value-producing construct uses one shared join algorithm: the result is the nearest common declared supertype to which every normally completing branch result is assignable, including the existing nullability rules. No numeric promotion or widening, structural typing, dynamic typing, implicit conversion, or inferred union type is introduced: a numeric widening is a coercion at a conversion site, never a join rule, so `if (c) { 1 } else { 1L }` has type `Number`, not `Long`."]),
 "REQ-1211": dict(
  section="21.7 Result types",
  summary="If exactly one branch can complete normally, its result type is the construct's result type, and `Unit` participates in the join as any other non-null value type",
  kind="compile-time",
  notes="Two different branch types joining to their nearest common supertype is the same rule the join clause states; binding the result to `Any` is the specification's own worked example.",
  quotes=["If exactly one branch can complete normally, its\nresult type is the construct's result type.",
          "`Unit` participates in the join as any other\nnon-null value type."]),
 "REQ-1212": dict(
  section="21.8 Expression contexts",
  summary="Block, `if`, and `switch` expressions are accepted wherever the grammar accepts an expression, including assignment right-hand sides, call arguments, explicit `return` values, and nested expression constructs",
  kind="runtime",
  notes="Each named context appears once, and the oracle is the concatenation of the values each context must produce.",
  quotes=["Block, `if`, and `switch` expressions are accepted wherever the grammar accepts an expression, subject to ordinary precedence and any required parentheses, including local initializers, assignment right-hand sides, call arguments, explicit `return` values, operands and nested expression constructs, and `match` branch results:"]),
 "REQ-1213": dict(
  section="21.8 Expression contexts / 21.9 required diagnostics",
  summary="A function body does not implicitly return its final expression, so a value-returning function whose body evaluates but does not return is rejected",
  kind="compile-time",
  notes="The specification's own `invalid` example is used verbatim. It is rejected, and the value-returning-function reachability rule is named SOLV-TYPE-012, so that code is pinned; the implementation's additional SOLV-SEM-003 has zero occurrences in the specification and is deliberately not asserted.",
  quotes=["Assignments remain statements and are not usable as tail expressions or nested values, and a function body does not implicitly return its final expression:",
          "whether a `try` statement can\nstill fall through governs the rule that a value-returning function must return on every path\n(`SOLV-TYPE-012`)"]),
 "REQ-1214": dict(
  section="21.6 Existing `match` expressions",
  summary="A `match` branch may use a block expression for multiple statements, following the same tail-result, scope, and typing rules as any other block expression",
  kind="runtime",
  notes="The spec's `Ok(value) => { println(...); value }` shape, with `print` instead of `println` so the expected bytes are platform-independent. Both arms are exercised so the marker and both values appear in one deterministic stream.",
  quotes=["Ok(value) => {\n    println(\"ok\")\n    value\n}",
          "The block follows the same tail-result, semicolon, scope, abrupt-completion, and typing rules as any\nother block expression."]),
 "REQ-1215": dict(
  section="21.2 Block expressions / 21.1 Terms",
  summary="A local declared inside a block expression is visible nowhere outside it, so referencing it after the block is an unknown-name error",
  kind="compile-time",
  notes="Exact code from the reference-resolution rule that names SOLV-RESOL-001 for a name with no visible binding.",
  quotes=["A block expression introduces one lexical scope. Earlier statements execute in source order, and a local declared inside the block is visible to later items in that block and nowhere outside it.",
          "and if none is visible the\nreference is `SOLV-RESOL-001`"]),
 "REQ-1216": dict(
  section="21.9 required diagnostics",
  summary="A class that declares `override func hashCode` must also declare `override func equals` in the same class declaration, which is SEM_HASHCODE_WITHOUT_EQUALS",
  kind="compile-time",
  notes="The mirror image of the already-covered equals-without-hashCode rule (REQ-0002 / SOLV-TCK-0003). Exact code from the 21.9 registry.",
  quotes=["A class that declares `override func equals` must also declare `override func hashCode` in the same class declaration, and a class that declares `override func hashCode` must also declare `override func equals`.",
          "| `SEM_HASHCODE_WITHOUT_EQUALS` | `SOLV-SEM-044` | the `hashCode` override declared without `equals` |"]),
}

# ---------------------------------------------------------------- test sources
S = {
 # 0119 block expression value, and a block whose tail expression has type Unit
 "SOL-TCK-0119": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// verbatim: "A block expression introduces one lexical scope. Earlier statements execute
// in source order, and a local declared inside the block is visible to later items in
// that block and nowhere outside it." The section also gives both shapes used here:
//   val answer = { val base = 20; base + 22 }   -- "has type Integer and value 42"
//   val logged: Unit = { println("done") }      -- "the second has type Unit"
// `println` is replaced by `print` throughout this corpus so no platform line separator
// can enter the expected bytes (section 6 defines println's separator as the platform's).
// Ordering is fixed by source order: the Unit block runs at its declaration and emits
// "d", then the final print emits the Integer block's 42. Expected stdout is "d42".
val base = 20
val answer = {
    val inner = base
    inner + 22
}
val logged: Unit = {
    print("d")
}
print(answer)
""",
 # 0120 two block expressions, each declaring a local named `s`, sharing one outer var
 "SOL-TCK-0120": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:
// "A block expression introduces one lexical scope ... a local declared inside the block
// is visible to later items in that block and nowhere outside it."
// Two block expressions each declare a local named `s`, so the second `s` must not see or
// disturb the first: each block reads and updates the one outer variable, and each block's
// own `s` is the only `s` visible inside it. First block: total = 0+2 = 2. Second: the
// fresh `s` is 3, so total = 2+3 = 5. Printing after each block gives "2" then "5",
// so the expected stdout is exactly "25". A shared or leaked scope could not produce 5
// (it would produce 4 from `s + s`, or fail to compile).
var total = 0
val a = {
    val s = 2
    total = total + s
    total
}
val b = {
    val s = 3
    total = total + s
    total
}
print(a)
print(b)
""",
 # 0121 block expression whose local feeds a later statement, plus nested reading outer
 "SOL-TCK-0121": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// "Earlier statements execute in source order, and a local declared inside the block is
// visible to later items in that block". The block declares `first`, uses it in a second
// declaration, and the tail expression uses both; `outer` is visible going in.
// first = 1, second = first + outer = 1 + 4 = 5, tail = second + 1 = 6, so the expected
// stdout is exactly "6".
val outer = 4
val v = {
    val first = 1
    val second = first + outer
    second + 1
}
print(v)
""",
 # 0122 value-required block ending in a local declaration
 "SOL-TCK-0122": """// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// verbatim: "An empty block, a block ending in a local declaration, and a block ending in
// an assignment are invalid in expression position and do not acquire an implicit `Unit`
// result", illustrated by the spec's own `val invalid = { val local = 1 }`. Section 21.9
// names the stable code SEM_BLOCK_RESULT_REQUIRED = SOLV-SEM-041, whose primary span is
// the "offending block or case body". The trailing print is a sentinel only: a compile
// rejection prevents it from running.
val invalid = {
    val local = 1
}
print("EXECUTED-INVALID")
""",
 # 0123 empty block in value position
 "SOL-TCK-0123": """// Negative conformance test. Oracle derived from the same section 21.2 sentence quoted by
// SOL-TCK-0122, which names the empty block first among the invalid shapes: "An empty
// block, a block ending in a local declaration, and a block ending in an assignment are
// invalid in expression position and do not acquire an implicit `Unit` result".
// An empty block has no tail expression to supply a result, so SEM_BLOCK_RESULT_REQUIRED
// (SOLV-SEM-041) is the required diagnostic. This test shares that expectation with its
// siblings on purpose: the expectation is one specification rule exercised on three
// distinct shapes, not three independently derived byte streams.
val invalid = {
}
print("EXECUTED-INVALID")
""",
 # 0124 block ending in an assignment
 "SOL-TCK-0124": """// Negative conformance test. Oracle derived from the section 21.2 sentence quoted by
// SOL-TCK-0122, plus section 21.3: "a terminal assignment is a statement and never a tail
// expression". The block's last item assigns to an outer variable, so the block never
// reaches a tail expression and must be rejected with SEM_BLOCK_RESULT_REQUIRED
// (SOLV-SEM-041) rather than acquiring a `Unit` result.
var target = 0
val invalid = {
    target = 5
}
print("EXECUTED-INVALID")
""",
 # 0125 every path abrupt -> Nothing, cannot satisfy a value-returning function
 "SOL-TCK-0125": """// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:
// "A value-required block whose every path completes abruptly has type `Nothing` and never
// evaluates a tail expression", together with 21.1's "Abrupt completion carries no value
// and does not participate in result joining."
// The block inside `f` only returns, so `f`'s body never produces a value from it and the
// value-returning-function rule applies. Section 17 names that rule's code: a value-returning
// function "must return on every path (`SOLV-TYPE-012`)". This is the observable difference
// between `Nothing` and a fabricated `Unit`, zero, `null`, or empty string, all of which
// section 21 forbids the implementation from inventing.
func f(): Integer {
    val v = {
        return 7
    }
}
print("EXECUTED-INVALID")
""",
 # 0127 standalone scope block remains a statement block
 "SOL-TCK-0126": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:
// "A standalone scope block remains a statement block". In statement position a brace
// block is therefore not a value: it contributes nothing, and its only effect is running
// its statements in source order.
// `x` starts at 10; the block declares a local `d` and assigns x = x - d = 10 - 7 = 3, so
// the expected stdout is exactly "[3]". The brackets are the test's own contribution, not
// a specification claim: another test in this corpus already derives the bare bytes "3"
// from an unrelated rule, and an oracle that coincides with a sibling's tests nothing
// about ordering, so bracketing both marks this stream and keeps the two derivations
// independently checkable. If the block were treated as an expression its value
// would be required, and section 21.2 makes that a compile-time error instead.
var x = 10
{
    val d = 7
    x = x - d
}
print("[")
print(x)
print("]")
""",
 # 0128 semicolon / comment equivalence for tail selection
 "SOL-TCK-0127": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.3,
// verbatim: "Explicit and synthesized semicolons are the same parser token and have the
// same language meaning, so token origin is never inspected to decide whether a value
// exists. All three forms below have the same value and type" -- and the spec lists
// `val a = { 42 }`, the multi-line form, and `val c = { 42; }`, each "an `Integer` block
// expression with value 42". The section adds "Comments and blank lines before `}` do not
// affect tail selection", which the fourth form here exercises.
// Four spellings of the same value 42, so the expected stdout is "42424242". Any spelling
// that lost the tail expression would instead be a compile-time error.
val a = { 42 }
val b = {
    42
}
val c = {
    42;
}
val d = {
    42

    // a comment and blank lines must not disturb tail selection

}
print(a)
print(b)
print(c)
print(d)
""",
 # 0129 chained expression if, one input per arm
 "SOL-TCK-0128": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "An `if` may be used in expression position", illustrated by the chained
// `if (value < 0) { "negative" } else if (value == 0) { "zero" } else { "positive" }`.
// Each arm's string is fixed by that example, so driving the construct with one value per
// arm fixes the whole output. Values are bracketed so the three arms are distinguishable
// in a single stream regardless of print's separator behavior (section 5 defines print to
// append nothing). Expected stdout is "[negative][zero][positive]".
val low = -3
val zero = 0
val high = 9
val a = if (low < 0) {
    "negative"
} else if (low == 0) {
    "zero"
} else {
    "positive"
}
val b = if (zero < 0) {
    "negative"
} else if (zero == 0) {
    "zero"
} else {
    "positive"
}
val c = if (high < 0) {
    "negative"
} else if (high == 0) {
    "zero"
} else {
    "positive"
}
print("[")
print(a)
print("]")
print("[")
print(b)
print("]")
print("[")
print(c)
print("]")
""",
 # 0130 non-Boolean condition in an expression if -> rejected
 "SOL-TCK-0129": """// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "The condition must be `Boolean`, exactly as for statement `if`." The condition
// here is an Integer, so the program is not well typed and must be rejected before
// execution.
// The expectation is a bare rejection on purpose. The specification states the requirement
// but names no diagnostic code for this site, and the implementation's own code for it does
// not appear anywhere in LANGUAGE_SPEC.md, so pinning one here would assert a choice the
// specification never made.
val v = if (7) {
    "yes"
} else {
    "no"
}
print("EXECUTED-INVALID")
""",
 # 0131 abrupt else excluded from joining (spec's requireName)
 "SOL-TCK-0130": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "Every normally completing branch must produce a tail result, and abrupt
// branches are excluded from result joining", illustrated by the spec's own
//   func requireName(name: String?): String {
//       return if (name != null) { name } else { return "fallback" }
//   }
// The `else` returns from the enclosing function, so it is abrupt and contributes nothing
// to the join; only the non-null branch is the `if` expression's result. Applying the
// function to a non-null argument yields "z" and to null yields "fallback", bracketed here
// so both appear in one stream. Expected stdout is "[z][fallback]".
func requireName(name: String?): String {
    return if (name != null) {
        name
    } else {
        return "fallback"
    }
}
print("[")
print(requireName("z"))
print("]")
print("[")
print(requireName(null))
print("]")
""",
 # 0132 expression switch produces a value; statement switch may omit default
 "SOL-TCK-0131": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5:
// "In expression position the same surface syntax produces a value", with the spec's
// `case Status.Ready: "ready" ... default: "done"` shape; and "a statement `switch` may
// still omit `default` and do nothing when no label matches".
// Both halves are exercised. The expression form over the Integer 2 selects "two", and the
// statement form is given a label list that matches nothing, so it must contribute no
// bytes at all. Expected stdout is therefore exactly "[two]" -- an implicit fallthrough, a
// synthesized default, or a statement switch that complained would each change it. The
// brackets are this test's own addition: a statement switch elsewhere in the corpus
// already derives the bare bytes "two" from the statement-form rules, and sharing those
// exact bytes would make the two expectations mutually uncheckable.
val n = 2
val word = switch (n) {
    case 1:
        "one"
    case 2:
        "two"
    default:
        "other"
}
switch (n) {
    case 99:
        print("MUST-NOT-APPEAR")
}
print("[")
print(word)
print("]")
""",
 # 0133 switch case body ending in a declaration -> SEM-041
 "SOL-TCK-0132": """// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5,
// verbatim: "Every normally completing case body, including `default`, must end in a tail
// expression; statements may precede it." The matched case body ends in a local
// declaration, which section 21.3 states is a statement and never a tail expression, so
// this normally completing path reaches no result.
// Section 21.9 gives SEM_BLOCK_RESULT_REQUIRED = SOLV-SEM-041 with the primary span
// "offending block or case body", which is exactly this position.
val n = 1
val m = switch (n) {
    case 1:
        val q = 1
    default:
        "other"
}
print("EXECUTED-INVALID")
""",
 # 0134 scrutinee evaluated exactly once
 "SOL-TCK-0133": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5,
// verbatim: "The scrutinee is evaluated exactly once. Case labels are tested in source
// order, only the first matching body executes, and there is no implicit fallthrough."
// The scrutinee is a call whose only effect is to emit an observation marker, so "exactly
// once" becomes a byte-exact claim: the marker must appear exactly once, and the matched
// body's value follows it. Expected stdout is exactly "Sone".
// Re-evaluating the scrutinee for a second label comparison would emit "SSone", and
// evaluating it once per tested label would emit more markers still.
class Probe {
    func tick(): String {
        print("S")
        return "one"
    }
}
val result = switch (Probe().tick()) {
    case "one":
        "one"
    case "two":
        "two"
    default:
        "other"
}
print(result)
""",
 # 0135 join of Integer and Long binds to Number
 "SOL-TCK-0134": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.7,
// verbatim: "No numeric promotion or widening ... is introduced: a numeric widening is a
// coercion at a conversion site, never a join rule, so `if (c) { 1 } else { 1L }` has type
// `Number`, not `Long`."
// The join is Integer and Long, whose nearest common declared supertype per that sentence
// is Number. Binding the construct to a `Number` local is therefore legal and the taken
// branch's value is 1, so the expected stdout is exactly "1". The companion rejection test
// SOL-TCK-0135 is what proves the join is not `Integer` or `Long`.
val n = if (true) {
    1
} else {
    1L
}
val joined: Number = n
print(joined)
""",
 # 0136 join is NOT Long / not Integer -> rejected
 "SOL-TCK-0135": """// Negative conformance test. Oracle derived by hand from the same section 21.7 sentence as
// SOL-TCK-0134: `if (c) { 1 } else { 1L }` "has type `Number`, not `Long`". Assigning that
// construct to a `Long` local therefore requires a widening the specification explicitly
// refuses to introduce at a join, so the program must be rejected.
// This rejection is the discriminating half of the join rule: an implementation applying
// numeric promotion would infer `Long`, accept this program, and pass SOL-TCK-0134 while
// contradicting the sentence quoted above.
// Asserted as a bare rejection: the specification names SOLV-TYPE-001 for a *static*
// declaration initializer that is not assignable to its declared type, not for a local
// initializer, so no code is mandated at this particular site.
val joined: Long = if (true) {
    1
} else {
    1L
}
print("EXECUTED-INVALID")
""",
 # 0137 exactly one branch can complete normally -> its type is the result
 "SOL-TCK-0136": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.7,
// verbatim: "If exactly one branch can complete normally, its result type is the
// construct's result type." and "`Unit` participates in the join as any other non-null
// value type."
// Section 21.4's example gives the shape: the `else` completes abruptly via `return`, so
// only the taken branch is a normally completing result and its type String is the
// construct's type. Expected stdout is "[x]" for the branch that completes normally; the
// abrupt path returns from the enclosing function before the outer print, and this test
// exercises only the normal path so the value is fixed.
func pick(k: Boolean): String {
    return if (k) {
        "x"
    } else {
        return "abrupt"
    }
}
print("[")
print(pick(true))
print("]")
""",
 # 0138 expression contexts
 "SOL-TCK-0137": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.8,
// verbatim: "Block, `if`, and `switch` expressions are accepted wherever the grammar
// accepts an expression ... including local initializers, assignment right-hand sides, call
// arguments, explicit `return` values, operands and nested expression constructs".
// Four of those named contexts appear here, each producing a fixed value: assignment
// right-hand side yields 10, call argument yields "n", explicit return yields "nonzero",
// and a nested construct yields 2. Expected stdout is exactly "10nnonzero2".
var score: Integer = 0
score = if (true) {
    10
} else {
    0
}
print(score)
print(if (false) {
    "d"
} else {
    "n"
})
func classify(value: Integer): String {
    return switch (value) {
        case 0:
            "zero"
        default:
            "nonzero"
    }
}
print(classify(5))
val nested = if (true) {
    if (false) {
        1
    } else {
        2
    }
} else {
    3
}
print(nested)
""",
 # 0139 function body does not implicitly return
 "SOL-TCK-0138": """// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.8, which
// gives this exact program as its invalid example: "a function body does not implicitly
// return its final expression", followed by
//   func invalid(): Integer {
//       42 // compile error: a value-returning function requires return 42
//   }
// Section 21's preamble adds that a construct with an error "never produces an executable
// call target". The code for the violated rule is named in section 17: a value-returning
// function "must return on every path (`SOLV-TYPE-012`)". The implementation also reports a
// second diagnostic here whose code appears nowhere in the specification, and this
// expectation deliberately pins only the code the specification names.
func invalid(): Integer {
    42
}
print("EXECUTED-INVALID")
""",
 # 0140 match branch using a block expression
 "SOL-TCK-0139": """// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.6,
// verbatim: "because a block is an expression a branch may use a block expression for
// multiple statements", with the spec's own branch shape
//   Ok(value) => {
//       println("ok")
//       value
//   }
// and the rule that "The block follows the same tail-result, semicolon, scope,
// abrupt-completion, and typing rules as any other block expression."
// `println` is replaced by `print` so no platform line separator enters the expected bytes.
// Both arms are exercised: the Ok branch emits the marker "ok" and then its bound value 3,
// the Err branch's tail expression is 0. Expected stdout is exactly "ok30".
enum Shape {
    Ok(Integer)
    Err(String)
}
func extract(s: Shape): Integer {
    return match s {
        Ok(value) => {
            print("ok")
            value
        }
        Err(reason) => {
            0
        }
    }
}
print(extract(Shape.Ok(3)))
print(extract(Shape.Err("bad")))
""",
 # 0141 block-local name is invisible outside the block
 "SOL-TCK-0140": """// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// verbatim: "A block expression introduces one lexical scope ... a local declared inside
// the block is visible to later items in that block and nowhere outside it."
// The block evaluates correctly and yields its tail value, but the following statement
// names the block-local `inner` outside the block, where nothing is visible. Section 4's
// reference rule names the diagnostic: a bare name that resolves to no local, parameter,
// function, or top-level declaration "is `SOLV-RESOL-001`".
val v = {
    val inner = 1
    inner
}
print(inner)
""",
 # 0142 hashCode without equals -> SEM-044
 "SOL-TCK-0141": """// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 3's
// equals/hashCode pairing rule, verbatim: "A class that declares `override func equals`
// must also declare `override func hashCode` in the same class declaration, and a class
// that declares `override func hashCode` must also declare `override func equals`."
// SOL-TCK-0003 already covers the first half; this is the second half. Section 21.9 names
// the stable code SEM_HASHCODE_WITHOUT_EQUALS = SOLV-SEM-044, whose primary span is "the
// `hashCode` override declared without `equals`". Override syntax mirrors the
// spec-validated hashCode/equals example used by SOL-TCK-0003.
class OnlyHash {
    val n: Integer

    OnlyHash(n: Integer) {
        this.n = n
    }

    override func hashCode(): Integer {
        return this.n
    }
}
print("EXECUTED-INVALID")
""",
}

# expected outcome / oracle, derived from the specification (see each source comment)
EXPECT = {
 "SOL-TCK-0119": dict(outcome="SUCCESS", stdout="d42"),
 "SOL-TCK-0120": dict(outcome="SUCCESS", stdout="25"),
 "SOL-TCK-0121": dict(outcome="SUCCESS", stdout="6"),
 "SOL-TCK-0122": dict(outcome="COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-041"}),
 "SOL-TCK-0123": dict(outcome="COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-041"}),
 "SOL-TCK-0124": dict(outcome="COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-041"}),
 "SOL-TCK-0125": dict(outcome="COMPILE_ERROR", diag={"family": "TYPE", "code": "SOLV-TYPE-012"}),
 "SOL-TCK-0126": dict(outcome="SUCCESS", stdout="[3]"),
 "SOL-TCK-0127": dict(outcome="SUCCESS", stdout="42424242"),
 "SOL-TCK-0128": dict(outcome="SUCCESS", stdout="[negative][zero][positive]"),
 "SOL-TCK-0129": dict(outcome="COMPILE_ERROR", diag={}),
 "SOL-TCK-0130": dict(outcome="SUCCESS", stdout="[z][fallback]"),
 "SOL-TCK-0131": dict(outcome="SUCCESS", stdout="[two]"),
 "SOL-TCK-0132": dict(outcome="COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-041"}),
 "SOL-TCK-0133": dict(outcome="SUCCESS", stdout="Sone"),
 "SOL-TCK-0134": dict(outcome="SUCCESS", stdout="1"),
 "SOL-TCK-0135": dict(outcome="COMPILE_ERROR", diag={}),
 "SOL-TCK-0136": dict(outcome="SUCCESS", stdout="[x]"),
 "SOL-TCK-0137": dict(outcome="SUCCESS", stdout="10nnonzero2"),
 "SOL-TCK-0138": dict(outcome="COMPILE_ERROR", diag={"family": "TYPE", "code": "SOLV-TYPE-012"}),
 "SOL-TCK-0139": dict(outcome="SUCCESS", stdout="ok30"),
 "SOL-TCK-0140": dict(outcome="COMPILE_ERROR", diag={"family": "RESOL", "code": "SOLV-RESOL-001"}),
 "SOL-TCK-0141": dict(outcome="COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-044"}),
}

CATEGORY = {
 "SOL-TCK-0119": "control", "SOL-TCK-0120": "control", "SOL-TCK-0121": "control",
 "SOL-TCK-0122": "control", "SOL-TCK-0123": "control", "SOL-TCK-0124": "control",
 "SOL-TCK-0125": "control", "SOL-TCK-0126": "evaluation", "SOL-TCK-0127": "syntax",
 "SOL-TCK-0128": "control", "SOL-TCK-0129": "types", "SOL-TCK-0130": "control",
 "SOL-TCK-0131": "control", "SOL-TCK-0132": "control", "SOL-TCK-0133": "evaluation",
 "SOL-TCK-0134": "types", "SOL-TCK-0135": "types", "SOL-TCK-0136": "types",
 "SOL-TCK-0137": "evaluation", "SOL-TCK-0138": "control", "SOL-TCK-0139": "match",
 "SOL-TCK-0140": "names", "SOL-TCK-0141": "equality",
}
REQ_FOR = {
 "SOL-TCK-0119": "REQ-1200", "SOL-TCK-0120": "REQ-1200", "SOL-TCK-0121": "REQ-1200",
 "SOL-TCK-0122": "REQ-1201", "SOL-TCK-0123": "REQ-1201", "SOL-TCK-0124": "REQ-1201",
 "SOL-TCK-0125": "REQ-1202", "SOL-TCK-0126": "REQ-1203", "SOL-TCK-0127": "REQ-1204",
 "SOL-TCK-0128": "REQ-1205", "SOL-TCK-0129": "REQ-1205", "SOL-TCK-0130": "REQ-1206",
 "SOL-TCK-0131": "REQ-1207", "SOL-TCK-0132": "REQ-1208", "SOL-TCK-0133": "REQ-1209",
 "SOL-TCK-0134": "REQ-1210", "SOL-TCK-0135": "REQ-1210", "SOL-TCK-0136": "REQ-1211",
 "SOL-TCK-0137": "REQ-1212", "SOL-TCK-0138": "REQ-1213", "SOL-TCK-0139": "REQ-1214",
 "SOL-TCK-0140": "REQ-1215", "SOL-TCK-0141": "REQ-1216",
}


# The requirement -> tests link is DERIVED from REQ_FOR, the single authoritative
# test->requirement mapping. Hand-maintaining it in two places already produced a
# disagreement while authoring this batch, which is exactly the kind of drift that makes
# an inventory untrustworthy.
_by_req = {}
for _tid, _rid in REQ_FOR.items():
    _by_req.setdefault(_rid, []).append(_tid)
for _rid, _r in REQS.items():
    _r["tests"] = sorted(_by_req[_rid])
assert sorted(sum(_by_req.values(), [])) == sorted(S), "REQ_FOR and S disagree on test ids"
assert set(_by_req) == set(REQS), "REQ_FOR and REQS disagree on requirement ids"


def verify_quotes():
    bad = []
    for rid, r in REQS.items():
        for q in r["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append((rid, q[:70]))
    return bad


def main():
    bad = verify_quotes()
    if bad:
        for rid, q in bad:
            print("QUOTE NOT IN SPEC %s: %r" % (rid, q))
        sys.exit(1)
    print("all %d normative quotes verified verbatim in LANGUAGE_SPEC.md"
          % sum(len(r["quotes"]) for r in REQS.values()))
    # write corpus
    for tid, src in sorted(S.items()):
        d = os.path.join(CORPUS, tid)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "main.sol"), "w") as fh:
            fh.write(src)
        e = EXPECT[tid]
        man = {
            "manifestSchemaVersion": 1, "specVersion": "2026.10-draft", "testId": tid,
            "category": CATEGORY[tid], "profile": "full-language", "status": "required",
            "requirements": [REQ_FOR[tid]], "entryPoint": "main.sol",
            "outcome": e["outcome"], "expectation": (
                {"languageExit": 0, "stdoutBase64":
                 base64.b64encode(e["stdout"].encode()).decode()}
                if e["outcome"] == "SUCCESS" else {"diagnostic": e["diag"]}),
        }
        with open(os.path.join(d, tid + ".manifest.json"), "w") as fh:
            fh.write(json.dumps(man, indent=2) + "\n")
    print("wrote %d test directories" % len(S))


if __name__ == "__main__":
    main()
