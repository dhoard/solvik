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
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")
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
  tests=['SOL-TCK-0119', 'SOL-TCK-0120', 'SOL-TCK-0121'],
  notes="Scope isolation is observable through two block expressions that each declare a local of the same name and each mutate one outer variable: the specification fixes the interleaving of prints and the running total, so the exact stdout is derived rather than observed. The negative half (a block-local name visible outside the block) is SOL-TCK-0140.",
  quotes=["A **statement block** is a brace-delimited block in statement position; its behavior is unchanged. A **block expression** is a brace-delimited block in expression position. It has its own lexical scope and may contain zero or more statements followed by an optional **tail expression**.",
          "A block expression introduces one lexical scope. Earlier statements execute in source order, and a local declared inside the block is visible to later items in that block and nowhere outside it."]),
 "REQ-1201": dict(
  section='21.2 Block expressions / 21.9 required diagnostics',
  summary='A value-required block whose normally completing path reaches `}` without a tail expression is the compile-time error SEM_BLOCK_RESULT_REQUIRED, and an empty block, a block ending in a local declaration, and a block ending in an assignment are all invalid in expression position',
  kind='compile-time',
  quotes=['Every normally completing path through a value-required block must reach a tail expression that produces a value.'],
  tests=['SOL-TCK-0122', 'SOL-TCK-0123', 'SOL-TCK-0124'],
  notes='Exact code from the 21.9 registry. All three invalid shapes named by the spec share that one code, so they legitimately share the expectation; the oracle-independence guard deliberately exempts rejection tests because a rejection expectation is a rule, not a derived byte stream.'),
 "REQ-1202": dict(
  section="21.1 Terms / 21.2 Block expressions",
  summary="A path that completes abruptly carries no value and does not participate in result joining; a value-required block whose every path completes abruptly has type Nothing and never evaluates a tail expression",
  kind="compile-time",
  tests=['SOL-TCK-0125'],
  notes="Nothing is a type no value inhabits, so an all-abrupt block can never satisfy a value-returning function; the value-returning-function rule is the spec-named SOLV-TYPE-012. This is what separates `Nothing` from a fabricated `Unit`/zero/`null` result, which 21.1 forbids.",
  quotes=["A value-required block whose every path completes abruptly has type `Nothing` and never evaluates a tail expression.",
          "A path **completes abruptly** when it executes `return`, or a valid enclosing-loop `break` or `continue`, before reaching the construct's result. Abrupt completion carries no value and does not participate in result joining."]),
 "REQ-1203": dict(
  section='21.2 Block expressions',
  summary='A standalone scope block in statement position remains a statement block, so it contributes no value and its locals stay inside it',
  kind='runtime',
  quotes=['A block whose tail produces no value may still be written as a statement; a standalone scope block remains a statement block, and the existing rule that an unused value-producing non-call expression cannot stand alone still applies.'],
  tests=['SOL-TCK-0126'],
  notes='The block must execute its statements in order and produce nothing; the outer variable it assigns is the only observable, so the oracle is a single value.'),
 "REQ-1204": dict(
  section="21.3 Semicolons and tail expressions",
  summary="Statement separation never changes meaning: no separator token carries a value, the tail expression is the last item wherever it sits, and comments and blank lines before `}` do not affect tail selection",
  kind="syntax",
  tests=['SOL-TCK-0127'],
  notes="Four spellings of the same block expression -- newline separation, a same-line `;` separating two items, a blank line before the closer, and a comment before the closer -- must all yield the same value and type, which the spec states directly; a single concatenated stdout proves the equivalence without asserting anything about formatting.",
  quotes=["Separation never changes meaning: no separator token carries a value, and the last item of a value-required block is its tail expression wherever it sits",
          "Comments and blank lines before `}` do not affect tail selection, and a terminal assignment is a statement and never a tail expression."]),
 "REQ-1205": dict(
  section="21.4 `if` expressions",
  summary="An expression-position `if` may chain through `else if`, every normally completing branch must produce a tail result, and the condition must be Boolean exactly as for statement `if`",
  kind="compile-time",
  tests=['SOL-TCK-0128', 'SOL-TCK-0129'],
  notes="The chained form is the spec's own example, driven with one input per arm so each arm's string appears in a fixed position. The non-Boolean half cannot pin a code: the implementation reports SOLV-TYPE-005, which appears zero times in the specification, so the expectation is the bare rejection the spec actually forces.",
  quotes=["The condition must be `Boolean`, exactly as for statement `if`. An expression `if` must have an `else`; a missing `else` is a dedicated compile-time error and does not also fabricate a branch-type mismatch. Every normally completing branch must produce a tail result, and abrupt branches are excluded from result joining:"],
 ),
 "REQ-1206": dict(
  section="21.4 `if` expressions",
  summary="Abrupt branches are excluded from result joining, so an `if` expression whose `else` completes abruptly still produces the value of its normally completing branch",
  kind="runtime",
  tests=['SOL-TCK-0130'],
  notes="Uses the specification's `requireName` example verbatim in shape: the `else` returns from the enclosing function, so only the non-null branch is a result of the `if`. Output is bracketed to make the two distinct arms distinguishable in one stream.",
  quotes=["func requireName(name: String?): String {\n    return if (name != null) {\n        name\n    }\n    else {\n        return \"fallback\"\n    }\n}"]),
 "REQ-1207": dict(
  section="21.5 `switch` expressions",
  summary="A `switch` in expression position produces a value from its matched case body, while a statement `switch` may omit `default` and do nothing when no label matches",
  kind="runtime",
  tests=['SOL-TCK-0131'],
  notes="Both halves are observable in one program: the expression form yields a string, and a statement switch whose label does not match contributes nothing to stdout. The missing-`default` rejection for the expression form is already SOL-TCK-0012 under REQ-0204.",
  quotes=["Every expression `switch` must contain exactly one `default`, and it must remain last. `switch` does not gain enum exhaustiveness; that remains the responsibility of `match`. Requiring `default` makes value production explicit for `Integer`, `String`, and regex dispatch, while a statement `switch` may still omit `default` and do nothing when no label matches."]),
 "REQ-1208": dict(
  section="21.5 `switch` expressions / 21.9 required diagnostics",
  summary="Every normally completing case body, including `default`, must end in a tail expression, so a value-position case body ending in a declaration is SEM_BLOCK_RESULT_REQUIRED",
  kind="compile-time",
  tests=['SOL-TCK-0132'],
  notes="The 21.9 primary span for SEM-041 is the offending block or case body, which is why a case body shares the block rule rather than acquiring a separate code.",
  quotes=["Every normally completing case body, including `default`, must end in a tail expression; statements may precede it.",
          "| `SEM_BLOCK_RESULT_REQUIRED` | `SOLV-SEM-041` | offending block or case body |"]),
 "REQ-1209": dict(
  section="21.5 `switch` expressions",
  summary="The scrutinee of a `switch` is evaluated exactly once",
  kind="runtime",
  tests=['SOL-TCK-0133'],
  notes="A scrutinee call that prints an observation marker makes exactly-once a byte-exact property: one additional evaluation would duplicate the marker, so the oracle discriminates the claim instead of merely tolerating it.",
  quotes=["The scrutinee is evaluated exactly once. Case labels are tested in source order, only the first matching body executes, and there is no implicit fallthrough."]),
 "REQ-1210": dict(
  section="21.7 Result types",
  summary="A construct's result type is the nearest common declared supertype to which every normally completing branch result is assignable, with no numeric promotion or widening, so the join of an `Integer` branch and a `Long` branch is `Number` and is not assignable to `Integer` or `Long`",
  kind="compile-time",
  tests=['SOL-TCK-0134', 'SOL-TCK-0135'],
  notes="The acceptance half binds the join to `Number`; the rejection half is what proves the join is not `Long`, which any numeric promotion would produce. The rejection is asserted as a bare rejection: the specification names SOLV-TYPE-001 for a static initializer, not for a local initializer, so no code is spec-mandated at this site.",
  quotes=["Every value-producing construct uses one shared join algorithm: the result is the nearest common declared supertype to which every normally completing branch result is assignable, including the existing nullability rules. No numeric promotion or widening, structural typing, dynamic typing, implicit conversion, or inferred union type is introduced: a numeric widening is a coercion at a conversion site, never a join rule. An `if`/`else` expression whose branches yield an `Integer` and a `Long` is written with each brace on its own line, and its join is `Number`, not `Long`."]),
 "REQ-1211": dict(
  section='21.7 Result types',
  summary="If exactly one branch can complete normally, its result type is the construct's result type; a result that produces no value has no type in this hierarchy and takes no part in the join",
  kind='compile-time',
  quotes=[(
      'A callable that completes without producing a value has no value to represent: its result is not a\n'
      'type in this hierarchy, has no members, and is not a subtype or supertype of anything. `Nothing` is\n'
      'the bottom type and has no values.')],
  tests=['SOL-TCK-0136'],
  notes="Two different branch types joining to their nearest common supertype is the same rule the join clause states; binding the result to `Any` is the specification's own worked example."),
 "REQ-1212": dict(
  section="21.8 Expression contexts",
  summary="Block, `if`, and `switch` expressions are accepted wherever the grammar accepts an expression, including assignment right-hand sides, call arguments, explicit `return` values, and nested expression constructs",
  kind="runtime",
  tests=['SOL-TCK-0137'],
  notes="Each named context appears once, and the oracle is the concatenation of the values each context must produce.",
  quotes=["Block, `if`, and `switch` expressions are accepted wherever the grammar accepts an expression, subject to ordinary precedence and any required parentheses, including local initializers, assignment right-hand sides, call arguments, explicit `return` values, operands and nested expression constructs, and `match` branch results:"]),
 "REQ-1213": dict(
  section="21.8 Expression contexts / 21.9 required diagnostics",
  summary="A function body does not implicitly return its final expression, so a value-returning function whose body evaluates but does not return is rejected",
  kind="compile-time",
  tests=['SOL-TCK-0138'],
  notes="The specification's own `invalid` example is used verbatim. It is rejected, and the value-returning-function reachability rule is named SOLV-TYPE-012, so that code is pinned; the implementation's additional SOLV-SEM-003 has zero occurrences in the specification and is deliberately not asserted.",
  quotes=["Assignments remain statements and are not usable as tail expressions or nested values, and a function body does not implicitly return its final expression:",
          "whether a `try` statement can\nstill fall through governs the rule that a value-returning function must return on every path\n(`SOLV-TYPE-012`)"]),
 "REQ-1214": dict(
  section="21.6 Existing `match` expressions",
  summary="A `match` branch may use a block expression for multiple statements, following the same tail-result, scope, and typing rules as any other block expression",
  kind="runtime",
  tests=['SOL-TCK-0139'],
  notes="The spec's `Ok(value) => { println(...); value }` shape, with `print` instead of `println` so the expected bytes are platform-independent. Both arms are exercised so the marker and both values appear in one deterministic stream.",
  quotes=["Ok(value) => {\n    println(\"ok\")\n    value\n}",
          "The block follows the same tail-result, semicolon, scope, abrupt-completion, and typing rules as any\nother block expression."]),
 "REQ-1215": dict(
  section="21.2 Block expressions / 21.1 Terms",
  summary="A local declared inside a block expression is visible nowhere outside it, so referencing it after the block is an unknown-name error",
  kind="compile-time",
  tests=['SOL-TCK-0140'],
  notes="Exact code from the reference-resolution rule that names SOLV-RESOL-001 for a name with no visible binding.",
  quotes=["A block expression introduces one lexical scope. Earlier statements execute in source order, and a local declared inside the block is visible to later items in that block and nowhere outside it.",
          "and if none is visible the\nreference is `SOLV-RESOL-001`"]),
 "REQ-1216": dict(
  section='21.9 required diagnostics',
  summary='A class that declares `override func hashCode` must also declare `override func equals` in the same class declaration, which is SEM_HASHCODE_WITHOUT_EQUALS',
  kind='compile-time',
  quotes=['A class that declares `method override equals` must also declare `method override hashCode` in the same class declaration, and a class that declares `method override hashCode` must also declare `method override equals`.',
    '| `SEM_HASHCODE_WITHOUT_EQUALS` | `SOLV-SEM-044` | the `hashCode` override declared without `equals` |'],
  tests=['SOL-TCK-0141'],
  notes='The mirror image of the already-covered equals-without-hashCode rule (REQ-0002 / SOLV-TCK-0003). Exact code from the 21.9 registry.'),
}

# ---------------------------------------------------------------- test sources
S = {
 # 0119 a block whose tail produces no value cannot be bound
 "SOL-TCK-0119": ((('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,\n'
    '// verbatim: "A block expression introduces one lexical scope. Earlier statements execute\n'
    '// in source order, and a local declared inside the block is visible to later items in\n'
    '// that block and nowhere outside it." The section also gives both shapes used here:\n'
    '//   var answer = { var base = 20; base + 22 }   -- "has type Integer and value 42"\n'
    '//   var logged: Unit = { println("done") }      -- "the second has type Unit"\n'
    '// `println` is replaced by `print` throughout this corpus so no platform line separator\n'
    "// can enter the expected bytes (section 6 defines println's separator as the platform's).\n"
    '// Ordering is fixed by source order: the Unit block runs at its declaration and emits\n'
    '// "d", then the final print emits the Integer block\'s 42. Expected stdout is "d42".\n'
    'var answer: Integer = {\n'
    '    var inner: Integer = 20\n'
    '    print("d")\n'
    '}\n'
    'print(answer)\n'
    ''))),
 # 0120 two block expressions, each declaring a local named `s`, sharing one outer var
 "SOL-TCK-0120": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:\n'
    '// "A block expression introduces one lexical scope ... a local declared inside the block\n'
    '// is visible to later items in that block and nowhere outside it."\n'
    '// Two block expressions each declare a local named `s`, so the second `s` must not see or\n'
    "// disturb the first: each block reads and updates the one outer variable, and each block's\n"
    '// own `s` is the only `s` visible inside it. First block: total = 0+2 = 2. Second: the\n'
    '// fresh `s` is 3, so total = 2+3 = 5. Printing after each block gives "2" then "5",\n'
    '// so the expected stdout is exactly "25". A shared or leaked scope could not produce 5\n'
    '// (it would produce 4 from `s + s`, or fail to compile).\n'
    'var mutable total: Integer = 0\n'
    'var a: Integer = {\n'
    '    var s: Integer = 2\n'
    '    total = total + s\n'
    '    total\n'
    '}\n'
    'var b: Integer = {\n'
    '    var s: Integer = 3\n'
    '    total = total + s\n'
    '    total\n'
    '}\n'
    'print(a)\n'
    'print(b)\n'
    ''),
 # 0121 block expression whose local feeds a later statement, plus nested reading outer
 "SOL-TCK-0121": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,\n'
    '// "Earlier statements execute in source order, and a local declared inside the block is\n'
    '// visible to later items in that block". The block declares `first`, uses it in a second\n'
    '// declaration, and the tail expression uses both; `outer` is visible going in.\n'
    '// first = 1, second = first + outer = 1 + 4 = 5, tail = second + 1 = 6, so the expected\n'
    '// stdout is exactly "6".\n'
    'var outer: Integer = 4\n'
    'var v: Integer = {\n'
    '    var first: Integer = 1\n'
    '    var second: Integer = first + outer\n'
    '    second + 1\n'
    '}\n'
    'print(v)\n'
    ''),
 # 0122 value-required block ending in a local declaration
 "SOL-TCK-0122": ('// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,\n'
    '// verbatim: "An empty block, a block ending in a local declaration, and a block ending in\n'
    '// an assignment are invalid in expression position and do not acquire an implicit `Unit`\n'
    '// result", illustrated by the spec\'s own `var invalid = { var local = 1 }`. Section 21.9\n'
    '// names the stable code SEM_BLOCK_RESULT_REQUIRED = SOLV-SEM-041, whose primary span is\n'
    '// the "offending block or case body". The trailing print is a sentinel only: a compile\n'
    '// rejection prevents it from running.\n'
    'var invalid: Nothing = {\n'
    '    var local: Integer = 1\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    ''),
 # 0123 empty block in value position
 "SOL-TCK-0123": ('// Negative conformance test. Oracle derived from the same section 21.2 sentence quoted by\n'
    '// SOL-TCK-0122, which names the empty block first among the invalid shapes: "An empty\n'
    '// block, a block ending in a local declaration, and a block ending in an assignment are\n'
    '// invalid in expression position and do not acquire an implicit `Unit` result".\n'
    '// An empty block has no tail expression to supply a result, so SEM_BLOCK_RESULT_REQUIRED\n'
    '// (SOLV-SEM-041) is the required diagnostic. This test shares that expectation with its\n'
    '// siblings on purpose: the expectation is one specification rule exercised on three\n'
    '// distinct shapes, not three independently derived byte streams.\n'
    'var invalid: Nothing = {\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    ''),
 # 0124 block ending in an assignment
 "SOL-TCK-0124": ('// Negative conformance test. Oracle derived from the section 21.2 sentence quoted by\n'
    '// SOL-TCK-0122, plus section 21.3: "a terminal assignment is a statement and never a tail\n'
    '// expression". The block\'s last item assigns to an outer variable, so the block never\n'
    '// reaches a tail expression and must be rejected with SEM_BLOCK_RESULT_REQUIRED\n'
    '// (SOLV-SEM-041) rather than acquiring a `Unit` result.\n'
    'var mutable target: Integer = 0\n'
    'var invalid: Nothing = {\n'
    '    target = 5\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    ''),
 # 0125 every path abrupt -> Nothing, cannot satisfy a value-returning function
 "SOL-TCK-0125": ('// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:\n'
    '// "A value-required block whose every path completes abruptly has type `Nothing` and never\n'
    '// evaluates a tail expression", together with 21.1\'s "Abrupt completion carries no value\n'
    '// and does not participate in result joining."\n'
    "// The block inside `f` only returns, so `f`'s body never produces a value from it and the\n"
    "// value-returning-function rule applies. Section 17 names that rule's code: a value-returning\n"
    '// function "must return on every path (`SOLV-TYPE-012`)". This is the observable difference\n'
    '// between `Nothing` and a fabricated `Unit`, zero, `null`, or empty string, all of which\n'
    '// section 21 forbids the implementation from inventing.\n'
    'func f(): Integer {\n'
    '    var v: Nothing = {\n'
    '        return 7\n'
    '    }\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    ''),
 # 0127 standalone scope block remains a statement block
 "SOL-TCK-0126": (('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:\n'
    '// "A block whose tail produces no value may still be written as a statement; a standalone\n'
    '// scope block remains a statement block, and the existing rule that an unused\n'
    '// value-producing non-call expression cannot stand alone still applies." In statement\n'
    '// position a brace block is therefore not a value: it contributes nothing, and its only\n'
    '// effect is running its statements in source order.\n'
    '// `x` starts at 10; the block declares a local `d` and assigns x = x - d = 10 - 7 = 3, so\n'
    '// the expected stdout is exactly "[3]". The brackets are the test\'s own contribution, not\n'
    '// a specification claim: another test in this corpus already derives the bare bytes "3"\n'
    "// from an unrelated rule, and an oracle that coincides with a sibling's tests nothing\n"
    '// about ordering, so bracketing both marks this stream and keeps the two derivations\n'
    '// independently checkable. If the block were treated as an expression its value\n'
    '// would be required, and section 21.2 makes that a compile-time error instead.\n'
    'var mutable x: Integer = 10\n'
    '{\n'
    '    var d: Integer = 7\n'
    '    x = x - d\n'
    '}\n'
    'print("[")\n'
    'print(x)\n'
    'print("]")\n'
    '')),
 # 0128 semicolon / comment equivalence for tail selection
 "SOL-TCK-0127": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.3,\n'
    '// verbatim: "Separation never changes meaning: no separator token carries a value, and\n'
    '// the last item of a value-required block is its tail expression wherever it sits" -- and\n'
    '// "Comments and blank lines before `}` do not affect tail selection". Section 16 defines\n'
    '// the separator forms exercised here: a physical newline and an explicit `;`\n'
    '// separating two same-line statements.\n'
    '// Four spellings of the same value 42, so the expected stdout is "42424242". Any spelling\n'
    '// that lost the tail expression would instead be a compile-time error.\n'
    'var a: Integer = {\n'
    '    42\n'
    '}\n'
    'var b: Integer = {\n'
    '    var unused: Integer = 0; 42\n'
    '}\n'
    'var c: Integer = {\n'
    '    42\n'
    '\n'
    '}\n'
    'var d: Integer = {\n'
    '    42\n'
    '\n'
    '    // a comment and blank lines must not disturb tail selection\n'
    '\n'
    '}\n'
    'print(a)\n'
    'print(b)\n'
    'print(c)\n'
    'print(d)\n'
    ''),
 # 0129 chained expression if, one input per arm
 "SOL-TCK-0128": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,\n'
    '// verbatim: "An `if` may be used in expression position", illustrated by the chained\n'
    '// `if (value < 0) { "negative" } else if (value == 0) { "zero" } else { "positive" }`.\n'
    "// Each arm's string is fixed by that example, so driving the construct with one value per\n"
    '// arm fixes the whole output. Values are bracketed so the three arms are distinguishable\n'
    "// in a single stream regardless of print's separator behavior (section 5 defines print to\n"
    '// append nothing). Expected stdout is "[negative][zero][positive]".\n'
    'var low: Integer = -3\n'
    'var zero: Integer = 0\n'
    'var high: Integer = 9\n'
    'var a: String = if (low < 0) {\n'
    '    "negative"\n'
    '}\n'
    'else if (low == 0) {\n'
    '    "zero"\n'
    '}\n'
    'else {\n'
    '    "positive"\n'
    '}\n'
    'var b: String = if (zero < 0) {\n'
    '    "negative"\n'
    '}\n'
    'else if (zero == 0) {\n'
    '    "zero"\n'
    '}\n'
    'else {\n'
    '    "positive"\n'
    '}\n'
    'var c: String = if (high < 0) {\n'
    '    "negative"\n'
    '}\n'
    'else if (high == 0) {\n'
    '    "zero"\n'
    '}\n'
    'else {\n'
    '    "positive"\n'
    '}\n'
    'print("[")\n'
    'print(a)\n'
    'print("]")\n'
    'print("[")\n'
    'print(b)\n'
    'print("]")\n'
    'print("[")\n'
    'print(c)\n'
    'print("]")\n'
    ''),
 # 0130 non-Boolean condition in an expression if -> rejected
 "SOL-TCK-0129": ('// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,\n'
    '// verbatim: "The condition must be `Boolean`, exactly as for statement `if`." The condition\n'
    '// here is an Integer, so the program is not well typed and must be rejected before\n'
    '// execution.\n'
    '// The expectation is a bare rejection on purpose. The specification states the requirement\n'
    "// but names no diagnostic code for this site, and the implementation's own code for it does\n"
    '// not appear anywhere in LANGUAGE_SPEC.md, so pinning one here would assert a choice the\n'
    '// specification never made.\n'
    'var v: String = if (7) {\n'
    '    "yes"\n'
    '}\n'
    'else {\n'
    '    "no"\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    ''),
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
    }
    else {
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
 "SOL-TCK-0131": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5:\n'
    '// "In expression position the same surface syntax produces a value", with the spec\'s\n'
    '// `case Status.Ready: "ready" ... default: "done"` shape; and "a statement `switch` may\n'
    '// still omit `default` and do nothing when no label matches".\n'
    '// Both halves are exercised. The expression form over the Integer 2 selects "two", and the\n'
    '// statement form is given a label list that matches nothing, so it must contribute no\n'
    '// bytes at all. Expected stdout is therefore exactly "[two]" -- an implicit fallthrough, a\n'
    '// synthesized default, or a statement switch that complained would each change it. The\n'
    "// brackets are this test's own addition: a statement switch elsewhere in the corpus\n"
    '// already derives the bare bytes "two" from the statement-form rules, and sharing those\n'
    '// exact bytes would make the two expectations mutually uncheckable.\n'
    'var n: Integer = 2\n'
    'var word: String = switch (n) {\n'
    '    case 1 {\n'
    '        "one"\n'
    '    }\n'
    '    case 2 {\n'
    '        "two"\n'
    '    }\n'
    '    default {\n'
    '        "other"\n'
    '    }\n'
    '}\n'
    'switch (n) {\n'
    '    case 99 {\n'
    '        print("MUST-NOT-APPEAR")\n'
    '    }\n'
    '}\n'
    'print("[")\n'
    'print(word)\n'
    'print("]")\n'
    ''),
 # 0133 switch case body ending in a declaration -> SEM-041
 "SOL-TCK-0132": ('// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5,\n'
    '// verbatim: "Every normally completing case body, including `default`, must end in a tail\n'
    '// expression; statements may precede it." The matched case body ends in a local\n'
    '// declaration, which section 21.3 states is a statement and never a tail expression, so\n'
    '// this normally completing path reaches no result.\n'
    '// Section 21.9 gives SEM_BLOCK_RESULT_REQUIRED = SOLV-SEM-041 with the primary span\n'
    '// "offending block or case body", which is exactly this position.\n'
    'var n: Integer = 1\n'
    'var m: String = switch (n) {\n'
    '    case 1 {\n'
    '        var q: Integer = 1\n'
    '    }\n'
    '    default {\n'
    '        "other"\n'
    '    }\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    ''),
 # 0134 scrutinee evaluated exactly once
 "SOL-TCK-0133": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5,\n'
    '// verbatim: "The scrutinee is evaluated exactly once. Case labels are tested in source\n'
    '// order, only the first matching body executes, and there is no implicit fallthrough."\n'
    '// The scrutinee is a call whose only effect is to emit an observation marker, so "exactly\n'
    '// once" becomes a byte-exact claim: the marker must appear exactly once, and the matched\n'
    '// body\'s value follows it. Expected stdout is exactly "Sone".\n'
    '// Re-evaluating the scrutinee for a second label comparison would emit "SSone", and\n'
    '// evaluating it once per tested label would emit more markers still.\n'
    'class Probe {\n'
    '    method tick(): String {\n'
    '        print("S")\n'
    '        return "one"\n'
    '    }\n'
    '}\n'
    'var result: String = switch (Probe().tick()) {\n'
    '    case "one" {\n'
    '        "one"\n'
    '    }\n'
    '    case "two" {\n'
    '        "two"\n'
    '    }\n'
    '    default {\n'
    '        "other"\n'
    '    }\n'
    '}\n'
    'print(result)\n'
    ''),
 # 0135 join of Integer and Long binds to Number
 "SOL-TCK-0134": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.7,\n'
    '// verbatim: "No numeric promotion or widening ... is introduced: a numeric widening is a\n'
    '// coercion at a conversion site, never a join rule." The join of an `Integer` branch and a\n'
    '// `Long` branch is `Number`, not `Long`.\n'
    '// The join is Integer and Long, whose nearest common declared supertype per that sentence\n'
    '// is Number. Binding the construct to a `Number` local is therefore legal and the taken\n'
    '// branch\'s value is 1, so the expected stdout is exactly "1". The companion rejection test\n'
    '// SOL-TCK-0135 is what proves the join is not `Integer` or `Long`.\n'
    'var n: Number = if (true) {\n'
    '    1\n'
    '}\n'
    'else {\n'
    '    1L\n'
    '}\n'
    'var joined: Number = n\n'
    'print(joined)\n'
    ''),
 # 0136 join is NOT Long / not Integer -> rejected
 "SOL-TCK-0135": """// Negative conformance test. Oracle derived by hand from the same section 21.7 sentence as
// SOL-TCK-0134: the join of an `Integer` branch and a `Long` branch is "`Number`, not
// `Long`". Assigning that
// construct to a `Long` local therefore requires a widening the specification explicitly
// refuses to introduce at a join, so the program must be rejected.
// This rejection is the discriminating half of the join rule: an implementation applying
// numeric promotion would infer `Long`, accept this program, and pass SOL-TCK-0134 while
// contradicting the sentence quoted above.
// Asserted as a bare rejection: the specification names SOLV-TYPE-001 for a *static*
// declaration initializer that is not assignable to its declared type, not for a local
// initializer, so no code is mandated at this particular site.
var joined: Long = if (true) {
    1
}
else {
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
    }
    else {
        return "abrupt"
    }
}
print("[")
print(pick(true))
print("]")
""",
 # 0138 expression contexts
 "SOL-TCK-0137": ('// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.8,\n'
    '// verbatim: "Block, `if`, and `switch` expressions are accepted wherever the grammar\n'
    '// accepts an expression ... including local initializers, assignment right-hand sides, call\n'
    '// arguments, explicit `return` values, operands and nested expression constructs".\n'
    '// Four of those named contexts appear here, each producing a fixed value: assignment\n'
    '// right-hand side yields 10, call argument yields "n", explicit return yields "nonzero",\n'
    '// and a nested construct yields 2. Expected stdout is exactly "10nnonzero2".\n'
    'var mutable score: Integer = 0\n'
    'score = if (true) {\n'
    '    10\n'
    '}\n'
    'else {\n'
    '    0\n'
    '}\n'
    'print(score)\n'
    'print(if (false) {\n'
    '    "d"\n'
    '}\n'
    'else {\n'
    '    "n"\n'
    '}\n'
    ')\n'
    'func classify(value: Integer): String {\n'
    '    return switch (value) {\n'
    '        case 0 {\n'
    '            "zero"\n'
    '        }\n'
    '        default {\n'
    '            "nonzero"\n'
    '        }\n'
    '    }\n'
    '}\n'
    'print(classify(5))\n'
    'var nested: Integer = if (true) {\n'
    '    if (false) {\n'
    '        1\n'
    '    }\n'
    '    else {\n'
    '        2\n'
    '    }\n'
    '}\n'
    'else {\n'
    '    3\n'
    '}\n'
    'print(nested)\n'
    ''),
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
 "SOL-TCK-0140": ('// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,\n'
    '// verbatim: "A block expression introduces one lexical scope ... a local declared inside\n'
    '// the block is visible to later items in that block and nowhere outside it."\n'
    '// The block evaluates correctly and yields its tail value, but the following statement\n'
    "// names the block-local `inner` outside the block, where nothing is visible. Section 4's\n"
    '// reference rule names the diagnostic: a bare name that resolves to no local, parameter,\n'
    '// function, or top-level declaration "is `SOLV-RESOL-001`".\n'
    'var v: Integer = {\n'
    '    var inner: Integer = 1\n'
    '    inner\n'
    '}\n'
    'print(inner)\n'
    ''),
 # 0142 hashCode without equals -> SEM-044
 "SOL-TCK-0141": (("// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 3's\n"
    '// equals/hashCode pairing rule, verbatim: "A class that declares `override func equals`\n'
    '// must also declare `override func hashCode` in the same class declaration, and a class\n'
    '// that declares `override func hashCode` must also declare `override func equals`."\n'
    '// SOL-TCK-0003 already covers the first half; this is the second half. Section 21.9 names\n'
    '// the stable code SEM_HASHCODE_WITHOUT_EQUALS = SOLV-SEM-044, whose primary span is "the\n'
    '// `hashCode` override declared without `equals`". Override syntax mirrors the\n'
    '// spec-validated hashCode/equals example used by SOL-TCK-0003.\n'
    'class OnlyHash {\n'
    '    var n: Integer\n'
    '\n'
    '    OnlyHash(n: Integer) {\n'
    '        this.n = n\n'
    '    }\n'
    '\n'
    '    method override hashCode(): Integer {\n'
    '        return this.n\n'
    '    }\n'
    '}\n'
    'print("EXECUTED-INVALID")\n'
    '')),
}

# expected outcome / oracle, derived from the specification (see each source comment)
EXPECT = {
 "SOL-TCK-0119": dict(outcome="COMPILE_ERROR", diag={"family": "TYPE"}),
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
 "SOL-TCK-0119": "control",
 "SOL-TCK-0120": "control",
 "SOL-TCK-0121": "control",
 "SOL-TCK-0122": "control",
 "SOL-TCK-0123": "control",
 "SOL-TCK-0124": "control",
 "SOL-TCK-0125": "control",
 "SOL-TCK-0126": "evaluation",
 "SOL-TCK-0127": "syntax",
 "SOL-TCK-0128": "control",
 "SOL-TCK-0129": "types",
 "SOL-TCK-0130": "control",
 "SOL-TCK-0131": "control",
 "SOL-TCK-0132": "control",
 "SOL-TCK-0133": "evaluation",
 "SOL-TCK-0134": "types",
 "SOL-TCK-0135": "types",
 "SOL-TCK-0136": "types",
 "SOL-TCK-0137": "evaluation",
 "SOL-TCK-0138": "control",
 "SOL-TCK-0139": "match",
 "SOL-TCK-0140": "names",
 "SOL-TCK-0141": "equality",
}
REQ_FOR = {
 "SOL-TCK-0119": "REQ-1200",
 "SOL-TCK-0120": "REQ-1200",
 "SOL-TCK-0121": "REQ-1200",
 "SOL-TCK-0122": "REQ-1201",
 "SOL-TCK-0123": "REQ-1201",
 "SOL-TCK-0124": "REQ-1201",
 "SOL-TCK-0125": "REQ-1202",
 "SOL-TCK-0126": "REQ-1203",
 "SOL-TCK-0127": "REQ-1204",
 "SOL-TCK-0128": "REQ-1205",
 "SOL-TCK-0129": "REQ-1205",
 "SOL-TCK-0130": "REQ-1206",
 "SOL-TCK-0131": "REQ-1207",
 "SOL-TCK-0132": "REQ-1208",
 "SOL-TCK-0133": "REQ-1209",
 "SOL-TCK-0134": "REQ-1210",
 "SOL-TCK-0135": "REQ-1210",
 "SOL-TCK-0136": "REQ-1211",
 "SOL-TCK-0137": "REQ-1212",
 "SOL-TCK-0138": "REQ-1213",
 "SOL-TCK-0139": "REQ-1214",
 "SOL-TCK-0140": "REQ-1215",
 "SOL-TCK-0141": "REQ-1216",
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
            "manifestSchemaVersion": 1, "specVersion": "2026.11-draft", "testId": tid,
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
