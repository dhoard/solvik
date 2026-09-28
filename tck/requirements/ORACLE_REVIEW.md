# Oracle Review Record

Section 6.1 requires an independent review record for each authored/migrated
expectation, containing the requirement ID, reviewer rationale, and the source it
was derived from. It also forbids generating, approving, or silently updating an
oracle from the implementation under test. Every portable corpus expectation below
was **authored from the normative specification text by hand** and is traceable to
the exact requirement; none was captured from the current GraalVM/Truffle IUT.

The workflow used: read the normative requirement -> write the source -> derive the
expected observable from the quoted normative text -> only then run the IUT, solely
to *detect a discrepancy* (an IUT disagreement is a candidate implementation defect,
never an oracle change). Where the specification does not determine an observable,
no oracle was authored (see `requirements.json` rationale fields).

| Test | Requirement | Expected | Normative source (quoted) | Capture-from-IUT? |
|---|---|---|---|---|
| SOL-TCK-0001 | REQ-0001 | stdout `1x`, exit 0 | LANGUAGE_SPEC §4: "`..` concatenates: both operands are rendered through `toString` and the result is always `String`, so `1 .. "x"` is `"1x"`" + §5 "`print` ... [does not append] a line separator" | No |
| SOL-TCK-0002 | REQ-0001 | stdout `3z`, exit 0 | LANGUAGE_SPEC §4: "Concatenation binds looser than arithmetic, so `a + b .. c` is `(a + b) .. c`" -> `(1+2) .. "z"` = `3z` | No |
| SOL-TCK-0003 | REQ-0002 | COMPILE_ERROR `SOLV-SEM-045` | LANGUAGE_SPEC §3 pairing rule ("a class that declares `override func equals` must also declare `override func hashCode`") + §21.9 table `SEM_EQUALS_WITHOUT_HASHCODE` = `SOLV-SEM-045` | No (code quoted from §21.9; override syntax mirrors the spec-validated `HashCode.sol` example) |
| SOL-TCK-0004 | REQ-0003 | stdout `hi`, exit 7, normal completion | LANGUAGE_SPEC §5: "`exit(code: Integer)` ... terminates the program with `code` as the process exit status and returns no value"; §7/§8 classify explicit `exit(n)` as a normal completion, not a runtime error | No |
| SOL-TCK-0005 | REQ-0100 | RUNTIME_FAILURE, category `ARITHMETIC_ERROR`, empty stdout | LANGUAGE_SPEC §4: "division by zero raises a Solvik runtime arithmetic error". The fault must come from execution (operands stored in `var` bindings so no constant folding), which is why the outcome is `RUNTIME_ERROR`, not `COMPILE_ERROR` — a `COMPILE_ERROR` can never satisfy a runtime-error expectation (§8.1). `ARITHMETIC_ERROR` is the protocol's normative classification of a "Solvik runtime arithmetic error" (TCK.md §8), a spec+protocol derivation | No |
| SOL-TCK-0006 | REQ-0100 | RUNTIME_FAILURE, category `ARITHMETIC_ERROR`, empty stdout | LANGUAGE_SPEC §4: "Integral arithmetic is checked and raises a Solvik runtime arithmetic error on overflow", plus §4 "A literal outside the signed 32-bit range is a compile-time error" (so `Integer` max is 2^31−1 = 2147483647, derived rather than probed). Adding 1 at run time therefore overflows. Same `var`-binding rationale as SOL-TCK-0005; category as above | No |
| SOL-TCK-0007 | REQ-0200 | COMPILE_ERROR, family `SEM`, code `SOLV-SEM-053` | LANGUAGE_SPEC §22.2: "The operand type must be assignable to a guest exception type ...; throwing any other value is the compile-time error `SOLV-SEM-053` (`SEM_THROW_NON_EXCEPTION`), reported on the operand". §22.1 defines a guest exception type as a class whose superclass chain reaches `Exception`/`RuntimeException`/`ApplicationException`; the test's class reaches none | No (code named in §22.2 and the §22.6 required-diagnostic table) |
| SOL-TCK-0008 | REQ-0201 | COMPILE_ERROR, family `SEM`, code `SOLV-SEM-056` | LANGUAGE_SPEC §22.3: "A `try` with neither a `catch` clause nor a `finally` clause is the compile-time error `SOLV-SEM-056` (`SEM_TRY_NEEDS_HANDLER`), reported on the whole statement". Both absences are required, so the program supplies neither | No |
| SOL-TCK-0009 | REQ-0202 | COMPILE_ERROR, family `SEM`, code `SOLV-SEM-047` | LANGUAGE_SPEC §7 (Static members): "A static member is **not inherited** and is **not overridable**. ... Declaring `open` or `override` on a static member is `SOLV-SEM-047`", plus that section's table (`SEM_INVALID_STATIC_MODIFIER`) | No |
| SOL-TCK-0010 | REQ-0300 | COMPILE_ERROR, family `TYPE` **only, no code asserted** | LANGUAGE_SPEC §2: "Reassignment is illegal" (`count = 2 // compile error`) and the `val`-freezes-the-binding example (`user.name = "Douglas"` valid, `user = User("Other")` a compile error). The specification names **no** code here, and TCK.md §6 forbids promoting an implementation enum entry to normative, so the oracle is the protocol family only | No — deliberately weaker: asserting the IUT's code would be capture-from-IUT |
| SOL-TCK-0011 | REQ-0203 | COMPILE_ERROR, family `SEM`, code `SOLV-SEM-042` | LANGUAGE_SPEC §21.4: "An expression `if` must have an `else`; a missing `else` is a dedicated compile-time error and does not also fabricate a branch-type mismatch", plus §21.9 (`SEM_IF_EXPRESSION_MISSING_ELSE` = `SOLV-SEM-042`). "does not also fabricate" is why `SOLV-SEM-042` and *not* `SOLV-TYPE-038` is expected | No |
| SOL-TCK-0012 | REQ-0204 | COMPILE_ERROR, family `SEM`, code `SOLV-SEM-043` | LANGUAGE_SPEC §21.9 table: `SEM_SWITCH_EXPRESSION_MISSING_DEFAULT` = `SOLV-SEM-043`, "whole `switch` expression"; §21.5 requires exactly one last `default` in an expression `switch` | No (see discrepancy note below — the first draft's arm syntax was corrected from §21.5's own example, not from IUT output) |
| SOL-TCK-0013 | REQ-0206 | RUNTIME_FAILURE, category `RESULT_WRONG_VARIANT`, empty stdout | LANGUAGE_SPEC §23 table ("`unwrap` ... the success payload; faults on the error variant") + §23.1: these "raise a Solvik runtime fault of the same class as an arithmetic, cast, or bounds fault (an ordinary guest failure ... not an internal error)" and are "detected at run time (at the operation)" — hence `RUNTIME_ERROR`, not `COMPILE_ERROR`. Category is TCK.md §8's normative classification | No. **Location deliberately not asserted** although §23.1 mentions a location, because the spec does not define the span and TCK.md §6.1 forbids the TCK choosing an undetermined observable |
| SOL-TCK-0014 | REQ-0206 | RUNTIME_FAILURE, category `RESULT_WRONG_VARIANT`, empty stdout | Same as SOL-TCK-0013 for the mirror operation: §23 ("`unwrapErr` ... faults on the success variant") + §23.1. The two tests are separate so a failure names the operation, per TCK.md §10's one-obligation preference | No (same location reasoning) |
| SOL-TCK-0015 | REQ-0205 | COMPILE_ERROR, family `SEM`, code `SOLV-SEM-052` | LANGUAGE_SPEC §23.2: "A `Result`-typed value used as a standalone statement (a call expression whose result type is a `Result`) is the compile-time error `SEM_UNUSED_RESULT` (`SOLV-SEM-052`)" and "`compute().ignore()` is accepted and `compute()` alone is rejected" — the program is exactly the rejected shape | No |
| SOL-TCK-0016 | REQ-0207 | RUNTIME_FAILURE, category `UNCAUGHT_EXCEPTION`, empty stdout | LANGUAGE_SPEC §22.5: "When a thrown value reaches this boundary, no Solvik handler remains, so the value is uncaught. An uncaught thrown value is a guest-visible failure: it terminates the program with a non-zero exit status ... and it is reported as an ordinary guest error, never as a host internal error"; §22.4 supplies the cross-call unwinding | No. The class/message report text is **not** asserted: §22.5 does not place it on guest stdout |
| SOL-TCK-0017 | REQ-0400 | stdout (byte-exact) `{"name":"Doug","path":"C:\\temp"}arbitrary "# content` | LANGUAGE_SPEC §15 raw-string rule: "r + N '#' characters + '\"' + content + '\"' + exactly N '#' characters", "The token's semantic value is the content between the delimiters", raw strings "do not process backslash escapes". The `C:\temp` backslash survives because escapes are not processed; inside `r###"…"###` the embedded `"#` is quote+ONE hash (not exactly three) so it does not close the token | No |
| SOL-TCK-0018 | REQ-0400 | stdout (byte-exact) `\nSELECT *\nFROM users\n` | LANGUAGE_SPEC §15: raw strings "preserve embedded newlines"; the value is the content between the delimiters, which includes the newline after `r#"` and the newline before the closing `"#`. `print` appends no separator (§5) | No |
| SOL-TCK-0019 | REQ-0401 | stdout (byte-exact) 13 bytes `61 5C 62 22 63 0A 64 0D 65 09 66 00 67` | LANGUAGE_SPEC §15: normal strings "support exactly `\\`, `\"`, `\n`, `\r`, `\t`, `\0`, and `N` (`\N`). Applying that closed list to the source yields each byte | No |
| SOL-TCK-0020 | REQ-0402 | stdout `no $ interpolation and not ${value} either` | LANGUAGE_SPEC §15: "String interpolation is deferred. A `$` has no interpolation meaning in the initial implementation." The `$`/`${…}` text is fixed literal content, so the emitted bytes are spec-determined. Asserts the specified *absence* of meaning, not a deferred feature | No |
| SOL-TCK-0021 | REQ-0301 | stdout `Douglas` | LANGUAGE_SPEC §2: "`val` freezes the binding, not the complete reachable object graph", with the annotated example marking `user.name = "Douglas"` "valid". Asserts the valid half; SOL-TCK-0010 asserts the rejected half | No |
| SOL-TCK-0022 | REQ-0403 | stdout `1-2` | LANGUAGE_SPEC §16: "Programmers may explicitly write `;`, but normal style uses newlines" — the two termination forms are equivalent, so both bindings exist and are readable | No |
| SOL-TCK-0023 | REQ-0001 | stdout `xnull\|123` | LANGUAGE_SPEC §3: "`1 .. \"x\"` is `\"1x\"` and `\"x\" .. null` is `\"xnull\""` (the literal null-render) plus "it is left-associative", so `1 .. 2 .. 3` = `(1 .. 2) .. 3` = `123` | No |
| SOL-TCK-0024 | REQ-0401 | COMPILE_ERROR, family `LEX` **only, no code asserted** | LANGUAGE_SPEC §15: "Any other escape is a lexical error" — the `\q` escape is outside the closed supported list. The spec names no `SOLV-LEX-*` code anywhere, so asserting only the protocol family; `diagnosticNormative: false` | No — adopting the IUT's `SOLV-LEX-003` would be capture-from-IUT |
| SOL-TCK-0025 | REQ-0404 | COMPILE_ERROR, family `LEX` **only** | LANGUAGE_SPEC §15: "An unterminated raw string is a lexical error at its opening delimiter." §15 also requires the diagnostic to show the expected closing delimiter, but TCK.md §7 says diagnostic wording is not normative unless the spec says so, and §15 fixes no message text — so that detail is NOT asserted (TCK.md §6.1 forbids choosing an undetermined observable) | No — IUT's `SOLV-LEX-002` not adopted |
| SOL-TCK-0026 | REQ-0405 | COMPILE_ERROR, family `LEX` **only**; LEX must be PRESENT (a follow-on PARS diagnostic is permitted) | LANGUAGE_SPEC §15: "Normal strings cannot contain an unescaped physical newline." Requiring only that a LEX-family diagnostic is present (not that it is the sole diagnostic) avoids over-constraining an observable the spec leaves open; the IUT legitimately also emits `SOLV-PARS-001` | No — IUT codes not adopted |
| SOL-TCK-0027 | REQ-0450 | stdout `3\|-3\|-3\|3` | LANGUAGE_SPEC §4: "Integral division truncates toward zero". All four sign combinations are asserted because truncation and floor division differ only on negative operands — the negative cases are what pin the rule (floor would give `-4`) | No |
| SOL-TCK-0028 | REQ-0451 | stdout `false\|true\|true\|true` | LANGUAGE_SPEC §4: "`Float` and `Double` follow IEEE 754 arithmetic"; §3: "NaN is unequal to every value including itself, positive and negative zero are equal, and infinities compare by their values". NaN/infinities are computed via `0.0/0.0` and `±1.0/0.0`, so no floating literal's rendered text is relied on | No |
| SOL-TCK-0029 | REQ-0452 | stdout `true\|true` | LANGUAGE_SPEC §3 gives both asserted cases as its own examples: "`1 == 1L` compares as `Long` and `1.5f == 1.5` compares as `Double`". Literal spellings are §1's `L`/`F` suffixes | No |
| SOL-TCK-0030 | REQ-0453 | stdout `Unknown\|true\|Doug` | LANGUAGE_SPEC §5: "For `receiver?.member`, the member is evaluated only when the receiver is non-null and the result type is the member type made nullable. For `left ?? right`, `left` must be nullable; the result is the common type of non-null `left` and `right`", plus the §5 narrowing example | No |
| SOL-TCK-0031 | REQ-0454 | stdout `Doug` | LANGUAGE_SPEC §5: "Flow-sensitive narrowing is required" (`// name is String here`). Made **load-bearing**: `greet` takes a non-null `String`, so the call type-checks only if narrowing really changed `String?`→`String` | No |
| SOL-TCK-0037 | REQ-0454 | stdout `a` | Matched control for SOL-TCK-0032: same shape on a `var` with **no** intervening write, so narrowing is valid and the non-null call is accepted | No |
| SOL-TCK-0032 | REQ-0455 | COMPILE_ERROR, family `TYPE` only | LANGUAGE_SPEC §5: "A write to a `var` invalidates its prior narrowing". The write sits between the null test and a use requiring non-null; the accepted control (SOL-TCK-0037) differs by that one statement, isolating the cause. Spec names no code → family only, `diagnosticNormative: false` | No |
| SOL-TCK-0033 | REQ-0456 | COMPILE_ERROR, family `TYPE` only | LANGUAGE_SPEC §5: "`null` is assignable only to nullable types" with the annotated example `val bad: String = null // compile error` — the program is that declaration. **No code asserted** (see the spec-gap note below) | No |
| SOL-TCK-0034 | REQ-0457 | COMPILE_ERROR, family `TYPE` only | LANGUAGE_SPEC §5: "`S?` is not assignable to non-null `T`" (S = T = String) | No |
| SOL-TCK-0035 | REQ-0458 | COMPILE_ERROR, family `TYPE` only | LANGUAGE_SPEC §4: widening holds "for exactly" the listed pairs, "No other conversion is implicit", "No narrowing is implicit" → `Double`→`Float` is not implicit | No |
| SOL-TCK-0036 | REQ-0458 | COMPILE_ERROR, family `TYPE` only | LANGUAGE_SPEC §4 names this non-relation explicitly: "`Integer` does not widen to `Float` (the 24-bit `Float` significand cannot hold every `Integer`)" | No |
| SOL-TCK-0038 | REQ-0459 | COMPILE_ERROR, family `TYPE` only | LANGUAGE_SPEC §4: "When two numeric operands have no such common type (**for example `Long` and `Float`**) the operator is ill-typed" — the spec supplies this exact operand pair | No |

## Byte-exactness and platform separators

SOL-TCK-0001/0002/0004 use `print`, which per §5 appends **no** line separator, so
their expected stdout is a fixed byte string with no platform-line-separator
dependency and **no** normalization is declared. Programs whose expectation would
depend on `println`'s platform separator must declare the closed
`platform-line-separator` normalization on the named field; none of the seeded tests
rely on it today.

## Runtime-error oracles and their dependency on the adapter boundary

A `RUNTIME_ERROR` oracle can only be trusted once the distribution exposes a genuine
structured execute boundary (TCK.md §9): without it, an adapter would have to infer
the fault class from a process exit code or human-readable text, which cannot separate
`exit(1)` from an arithmetic fault from a crash, and would make the "oracle" a product
of the IUT's error-reporting accidents.

That boundary now exists (Slice 8/9): the launcher writes a structured `RUNTIME_FAILURE`
record carrying a protocol-category name, and the adapter translates the fault site into
the protocol's UTF-8 byte-offset convention. `REQ-0100` was therefore migrated into the
portable corpus as SOL-TCK-0005/0006, with the fault class taken from the specification's
own words ("a Solvik runtime arithmetic error") mapped onto the protocol's
`ARITHMETIC_ERROR` category (TCK.md §8) — not read back from the implementation.

Before that boundary existed, `REQ-0100` was correctly recorded as `untested-portable`
with a rationale rather than given an invented category string. Recording a gap that
blocks certification was the correct action; inventing an oracle would not have been.

## Deliberately unauthored oracles

Where the specification does not determine an observable, no oracle is authored. Two
classes remain unauthored today:

* Diagnostic codes that the specification does not name are recorded as the family
  plus the observed code but are marked `diagnosticNormative: false`, so they are never
  presented as normative expectations.
* Fault categories for which the specification states only that an error is raised,
  without classifying the fault further, are asserted only at the protocol category the
  specification's own words imply (as with `REQ-0100` above). Where even the category is
  genuinely underdetermined, the correct action remains a recorded gap, not an invented
  expectation.

## Discrepancies found during authoring, and how they were resolved

TCK.md §6.1 permits running the IUT only to *detect a discrepancy*. Three arose in this
batch; in each case the resolution was determined by the specification, never by adopting
whatever the IUT produced.

1. **SOL-TCK-0008 (REQ-0201) — corrected before it ever ran.** The first draft used
   `try` with a `finally` and no `catch`, assuming that was the unhandled-`try` error.
   Re-reading §22.3 showed `finally` counts as a handler for this rule: the error
   requires the absence of *both* clauses. The draft was invalid as an oracle and was
   rewritten to supply neither. Because it was caught by re-reading the normative text,
   no IUT output influenced the fix.
2. **SOL-TCK-0012 (REQ-0204) — a genuine FAIL against the IUT.** The first draft wrote
   `switch` arms as `1 => { "one" }`, which the parser rejected with `SOLV-PARS-001`, so
   the program never reached the semantic rule under test and the expected
   `SOLV-SEM-043` never appeared. §21.5's own example gives the arm syntax as
   `case <label>:` / `default:` with a tail expression, and §21.5 states "The `switch`
   statement remains valid and unchanged. In expression position the same surface syntax
   produces a value." The source was corrected to that syntax and the expectation was
   left exactly as authored. The IUT's parse error was a signal that the *test program*
   was malformed, not evidence about the oracle.
3. **SOL-TCK-0010 (REQ-0300) — IUT emitted a code the spec does not name.** The IUT
   reports `SOLV-TYPE-006` for `val` reassignment. That code appears nowhere in
   `LANGUAGE_SPEC.md`, so per §6 ("an implementation enum entry ... does not make a code
   normative") it was *not* adopted. The manifest asserts the family only, and
   `requirements.json` marks the requirement `diagnosticNormative: false`.

The other new programs matched their hand-derived expectations on the first run: every
spec-named code (`SOLV-SEM-042/043/047/052/053/056`) and every protocol runtime category
(`RESULT_WRONG_VARIANT` ×2, `UNCAUGHT_EXCEPTION`, with empty stdout) was already produced
by both distributions.

## Specification gap: ordinary type/assignability mismatches have no normative code

`LANGUAGE_SPEC.md` never binds a diagnostic code to the commonest rejection in the
language — an ordinary declaration, argument, return, or assignment whose value is not
assignable to the declared type. It names `SOLV-TYPE-001` in only two narrow places:

* §7: "A static declaration initializer that is not assignable to the declared type is
  `SOLV-TYPE-001`."
* §22.1: "a non-`String?` message is `SOLV-TYPE-001` (`TYPE_MISMATCH`)".

For an ordinary local such as `val bad: String = null` the specification says only
"compile error". So SOL-TCK-0032/0033/0034/0035/0036/0038 assert the `TYPE` family and are
recorded with `diagnosticNormative: false`.

**Recommended specification change:** add a general required-diagnostic entry binding
type/assignability mismatch to `SOLV-TYPE-001` (and the no-common-widened-type operator
rule to a named code). Once the specification does so, these six manifests can be upgraded
to assert codes, which strictly strengthens them; until then asserting the family is the
honest ceiling. The TCK must not perform that generalization on the specification's
behalf.

### A fabricated citation found and retracted during this batch

While authoring SOL-TCK-0033..0036 and 0038, an earlier draft of these files justified
`SOLV-TYPE-001` by citing "section 10.6" and quoting a `TYPE_MISMATCH`/`TYPE_OPERAND`
diagnostic table. **No such section or table exists in `LANGUAGE_SPEC.md`** — the text was
pattern-completed rather than verified. It was caught by grepping the cited string out of
the specification and re-reading the actual occurrences (§7, §22.1, §22.6 -- each a narrow,
specific assignability context), then removed
from all five sources; those tests now assert the family only. The incident is recorded
here because the failure mode it illustrates — writing a plausible-looking normative
citation from memory instead of verifying it against the document — is the single most
dangerous way to corrupt a conformance oracle, and it is the reason every quoted passage
in this repository's oracle notes is required to be grep-verifiable in the specification.

## Batch: control flow, type tests/casts, and program display

Requirements REQ-0500..REQ-0508, tests SOL-TCK-0039..0056.

### Range for-in loops (REQ-0500)

`LANGUAGE_SPEC.md` section 17 defines the three range operators and the zero-iteration
rule in prose that is complete enough to derive byte-exact output without consulting any
implementation:

* "`...` ascends from the start and includes the end" -> `1...5` is exactly `12345`.
* "`..<` ascends from the start and excludes the end" -> `0..<5` is exactly `01234`.
* "`..>` descends from the start and excludes the end" -> `5..>0` is exactly `54321`.

The three oracles are mutually discriminating: an implementation that treats `...` as
exclusive, or that descends where it should ascend, cannot satisfy all three
simultaneously. Each expected value was written down before the program was run.

Zero iterations is observable in two distinct ways so that "loop did not run" is not
confused with "program produced no output". SOL-TCK-0042 prints a counter that must still
be `0` (the reversed range `5...1`); SOL-TCK-0045 prints a marker prefix so the expected
stream is the three bytes `empty:` rather than an empty stream (the empty range `0..<0`).

SOL-TCK-0049 tests "Both bounds are `Integer` expressions evaluated once before the first
iteration" by assigning to the bound variable inside the body. The oracle is the two
independent observables `passes` and `sum`, printed as `2:6`. This case is included with
an explicit caveat: the sentence fixes that the bounds are evaluated once, but whether an
implementation *also* snapshots the bound cell against later assignment is arguably
distinct from the iteration count itself. Because section 17 says the bounds are
evaluated once and says nothing about a live view of the bound cell, the literal reading
is the oracle; if this ever fails, the question to settle is whether section 17 is
silent on the cell-binding question, not whether the implementation is wrong.

### Three-clause `for`, omitted condition, break/continue (REQ-0501)

SOL-TCK-0043 accumulates `i` for `i` in 0..9 while skipping `3` and breaking at `6`, so
the operands are 0,1,2,4,5 and the derived total is 12. The value is discriminating in
both directions independently (each variant computed, not asserted): dropping the
`continue` gives 15, dropping the `break` gives 42, and dropping both gives 45. No
single-value confusion can land on 12 by accident. SOL-TCK-0044 omits the condition
clause entirely and counts passes; the derived value is 3, and an implementation reading
an omitted condition as `false` yields 0.

### `is` narrowing and checked `as` (REQ-0502)

Section 18 mandates narrowing ("The compiler must narrow the type where the checked value
is stable") and mandates the failure mode ("An unsuccessful `as` cast raises a Solvik
runtime type error"). SOL-TCK-0050 therefore reads a member declared **only** on the
subtype inside the guarded block -- a member read is what distinguishes narrowing from
mere truth of the test. SOL-TCK-0051 casts a `Cat` to `Dog`; the program is well typed
because `Dog` is a subtype of the static type, so the failure cannot be a static one, and
it is classified as `CAST_FAILURE` from the protocol's own taxonomy in protocol.md section 4.1,
not from an implementation string.

**No null-dereference oracle exists in this corpus, deliberately.** `grep` finds no
vocabulary for null dereference anywhere in `LANGUAGE_SPEC.md`, and the manifest schema
happens to list a `NULL_DEREFERENCE` category. A category in the protocol is not a
language guarantee, so asserting one would invent semantics the specification does not
state.

### Order-independent lookup and display (REQ-0503, REQ-0504)

SOL-TCK-0052 calls a function declared textually after the call site and derives `got=42`
from `2 * 21`. SOL-TCK-0053 renders an override through `..` and derives `[$5]`, which is
the override's text rather than the inherited class name. SOL-TCK-0054 pins `null`,
`true` and `false` byte-exactly.

Two clauses of the quoted sentences are **deliberately not asserted**:

* `println`'s appended separator is defined as *the platform line separator*, so baking a
  specific newline into a portable manifest would smuggle platform dependence into a
  portable oracle. Those tests use `print`, which appends nothing.
* "`Unit` renders `Unit`" is not testable because `Unit` is not a nameable value
  expression in the current grammar; `print(Unit)` is rejected as `unknown name 'Unit'`.
  The clause is recorded as untested rather than replaced with an invented proxy.

### Exact codes where the specification names them (REQ-0505 vs REQ-0506..0508)

REQ-0505 is the notable case in this batch. Section 20 states "An explicit `func main` in
any participating file remains `SOLV-SEM-001`", so SOL-TCK-0055 pins the **full stable
code**. This is the same kind of justification that permits pinning `SOLV-SEM-045` and
`SOLV-SEM-036`: the specification itself binds the code to the rule.

REQ-0506 (`break`/`continue` outside a loop), REQ-0507 (non-`Boolean` condition) and
REQ-0508 (arity) are stated as rules **without** naming a code, so their manifests assert
only the diagnostic family. Verified by `grep`: `SOLV-SEM-002` and `SOLV-TYPE-005` occur
nowhere in `LANGUAGE_SPEC.md`. The arity case is subtler -- `SOLV-TYPE-003` *does* appear
in the specification, but only as `TYPE_ARITY_MISMATCH` for **`Result` operation calls**
(section 23.4). Generalizing it to all call arity would be exactly the substitution the
TCK must not perform, so SOL-TCK-0058/0059 assert the `TYPE` family only.

### Authoring errors found and corrected in this batch

1. **A stale expected value.** After rewriting SOL-TCK-0053 to drop a `println` and use
   only `print`, its manifest still carried the old expected bytes `$5[$5]`. The
   discrepancy surfaced on the first run against the distribution. The correct derived
   value for the surviving program is `[$5]`. This was an authoring bug, not an
   implementation disagreement -- the fix was to the oracle, and the fix was justified by
   re-deriving from the sentence rather than by accepting the observed output.
2. **Comments that described a different program than the file contained.** SOL-TCK-0050's
   comment claimed a subtype-only member read that the code did not perform, and
   SOL-TCK-0053's comment described a `println` comparison the program no longer made.
   Both programs were brought into agreement with their stated obligation (or the
   obligation was narrowed to what the program actually does) rather than left as
   plausible narration.
3. **An unverified claim of an implementation inconsistency.** While scoping this batch I
   stated that `..` and `println` rendered a default object inconsistently (`user` vs
   `User`) without having run either one. A direct probe showed both render `User`, which
   matches section 4's "default representation (its class name)". There was no
   inconsistency. The claim was withdrawn before it reached any oracle, and the episode is
   recorded here because asserting a defect one has not observed is the same failure mode
   as the fabricated citation below.

### Section citations are now machine-checked, and found real errors

The fabricated-citation incident generalized: a `section` field pointing at a section that
does not exist, or at a real section whose stated title does not match, lends the same
false authority to an oracle. `test_oracle_quotes.py` now parses the heading structure of
`LANGUAGE_SPEC.md` and verifies every citation in `requirements.json` resolves to a real
`## N.` section or a real `### N.M`/`### Title` subsection, including its title.

Applying it immediately found **ten** citations that named the wrong section, all
corrected from evidence (locating each requirement's own quoted text and reading its
enclosing heading) rather than guesswork. The most consequential:

* REQ-0003 cited "5. Functions"; section 5 is *Nullability* and `exit` is defined in
  **section 6**.
* REQ-0001 and REQ-0450 cited "4. Root Type Hierarchy" for text that lives in **section 3**.
* REQ-0452 cited a "4. Implicit widening" heading that does not exist.
* REQ-0205 cited "21-23 diagnostics" and REQ-0207 cited "22.4 propagation" for quotes that
  live only in 23.2 and 22.5 respectively; both now cite only where their text is.

A separate correction: the retraction above originally stated the occurrences of
`SOLV-TYPE-001` as "§7 and §22.1"; the actual set is §7, §22.1 and §22.6. That assertion
is now machine-checked as an exact set rather than a bare count.

### Specification conflict found: `+` on strings (blocks interface/example-derived oracles)

The §8 interfaces example contains

```solvik
func greeting(): String {
    return "Hello " + name()
}
```

and §12's match examples use `"value=" + value` / `"error=" + error`. However §3 states
normatively:

* "The initial arithmetic and ordering operators require a numeric operand";
* "`..` concatenates: both operands are rendered through `toString`";
* "Solvik performs no other implicit conversion to `String`."

and the precedence table lists `+` in the arithmetic group (level 7) below `..` (level 6),
while §15 defines no `+`-on-strings rule at all. The current distribution rejects
`"Hello " + name()` with an ill-typed-operator diagnostic, consistent with §3.

Per AGENTS.md this is reported rather than resolved. Two readings exist: (a) §3 is
normative and the examples in §8/§12 are stale syntax; (b) `+` is supposed to work on
`String` and §3's operand sentence is incomplete. The TCK cannot pick one, so no oracle in
this corpus uses `+` on strings, and the interface-defaults, enum/match arm, and any other
examples that copy that style remain uncovered until the conflict is resolved. Resolving
it as (a) would make the examples non-conforming text that should be corrected in the
specification itself; resolving it as (b) would add a portable concatenation obligation
and the TCK would need `+`-on-String tests added then.

### Batch: classes and member resolution (REQ-0600..REQ-0605)

Tests SOL-TCK-0057..0063. Two of the six requirements are backed by section 7 prose that
names its own diagnostic code; the other four state rules without naming one (verified by
grep: `SOLV-SEM-011` and `SOLV-SEM-014`, the codes the current implementation emits for the
two rejection cases, occur zero times in the specification). The batch therefore mixes
exact-code and family-only assertions so the distinction stays visible rather than
defaulting to whichever is easier:

* exact codes: `SOLV-RESOL-001` (bare name is never a property; SOL-TCK-0061) and
  `SOLV-RESOL-005` (`this` outside an instance member; SOL-TCK-0063), both written
  verbatim in section 7;
* family-only: missing `override` and override-parameter mismatch, where the
  specification states rules but names no code.

The SOLV-RESOL-001 case has an unusually strong structure: section 7 states the negative
rule (a bare name is never a property) *and* the positive rule (a local may shadow a
property without ambiguity), so the corpus pairs a rejection test (SOL-TCK-0061) with a
byte-exact acceptance test (SOL-TCK-0062, `shadow=3/99`). Any implementation that
resolved the bare name to the property would fail one of the two, and any implementation
that rejected the shadowing local would fail the other.

### Batch: interfaces and delegation (REQ-0700, REQ-0701)

Tests SOL-TCK-0064..0068.

Interfaces and delegation turned out to be testable **only after** the `+`-on-`String`
conflict was isolated: the specification's own interface example cannot be compiled as
written. Rather than skip the area, the tests use the section 3 concatenation operator, so
they exercise interface defaults and delegation without depending on the disputed
operator. This is the correct shape for a TCK facing a spec defect -- cover what is
unambiguous and record precisely which clause is blocked.

SOL-TCK-0066/0067 are a deliberate pair: identical scaffolding, and the only difference is
one explicitly declared method. The expected value changes from the delegate's result to
the explicit method's result exactly when that method is added, so "explicit methods take
precedence over delegated members" is pinned in both directions and cannot be satisfied by
an implementation that simply always forwards or never forwards.

The current distribution additionally rejects a `delegate val` whose type is a class
("delegate must have an interface type"). That is consistent with the section 9 example but
is nowhere stated as its own rule, so **no oracle depends on it** -- asserting it would
adopt an implementation constraint as a language requirement.

### Two copy-paste oracle defects, and the invariant that now prevents them

While authoring SOL-TCK-0065, a manifest was left holding SOL-TCK-0064's expected stdout
even though the two programs are different; the same class of defect had already occurred
once in this session with SOL-TCK-0053, whose expectation survived an edit to the program
it described. Both were caught only by hand-running the programs -- which is exactly the
path an oracle must never depend on, because a copy-pasted expectation that happens to
match the observed output is indistinguishable from a correct one.

The corpus therefore now enforces two structural invariants
(`check_oracle_independence`, `check_empty_stdout_is_intentional` in
`test_oracle_quotes.py`):

* no two SUCCESS tests with **different executable sources** may share a byte-exact stdout
  oracle. Deriving distinct programs to identical exact output is the signature of a copied
  expectation, and every exact oracle in this corpus is specific enough to make collisions a
  defect rather than a coincidence. Rejection tests are exempt, since a family-only
  expectation is legitimately shared across a rule's siblings. Where two programs are
  genuinely expected to produce the same output (for example two syntaxes that must agree),
  the fix the invariant pushes toward is the right one anyway: print a per-test marker so
  each oracle remains individually discriminating.
* a SUCCESS test whose oracle is empty stdout must contain no printing call in its
  executable text. This catches the inverse mistake -- a program that prints but whose
  manifest says it prints nothing.

Both guards were verified to be falsifiable: reintroducing the SOL-TCK-0065 defect makes
the suite fail with `duplicated exact oracle: [('Doug/hi Doug', ['SOL-TCK-0064',
'SOL-TCK-0065'])]` and exit 1. Comment lines are stripped before source comparison, so
identical oracle prose shared between tests cannot mask or trigger the check.

The first version of that check silently discovered zero manifests and would have passed
vacuously; it was caught by an explicit `len(corpus_tests()) > 0` assertion, which is
itself the general lesson: a guard that cannot be shown to fire is not a guard.

### Batch: switch statements (REQ-0800, REQ-0801)

Tests SOL-TCK-0069..0075.

`Cases are tested in source order and exactly the first matching case executes` is
observable only through a program with **two cases matching the same value**
(SOL-TCK-0071): first-match-wins prints `nine`, all-matches-wins prints
`ninenine again`, last-match-wins prints `nine again`. Without the duplicate case the
sentence collapses to the ordinary single-match case and stops discriminating.

Likewise "Cases never implicitly fall through" and "No `break` is required to terminate a
case" are jointly pinned by SOL-TCK-0069: fallthrough would print `twoother`, and a
mandated-break reading would reject a program the specification makes legal.

SOL-TCK-0073/0074 are the legal/illegal pair for "A `break` inside a case is illegal unless
it exits a loop nested inside that case", which the specification states as a conditional
and therefore demands both branches of. SOL-TCK-0075 pins "Each case body is an implicit
block" through `continue` reaching the enclosing loop, which distinguishes a block reading
from a switch-boundary reading.

**One rule is recorded as untestable rather than forced.** "A switch contains at most one
`default`, and it must be last" bundles two clauses that cannot be separated: any program
containing two `default`s necessarily also has a `default` that is not last, so such a
program triggers the ordering rejection regardless of whether the implementation enforces
the count at all. Writing a rejection test for it would therefore assert behaviour whose
triggering clause is implementation-chosen, which is precisely what a conformance oracle
must not do. Isolating the count clause would need either a specification change or a
construct the grammar does not admit; both `SOLV-SEM-033` (count) and `SOLV-SEM-034`
(order) occur zero times in the specification, so neither could be pinned even if the
construct existed. The clause is listed as uncovered in `IMPLEMENTATION_PLAN.md`.

### Batch: generics and collections (REQ-0900..REQ-0908)

Nine requirements and sixteen tests covering LANGUAGE_SPEC section 11: the `List`, `Set`,
`Map`, and `Stack` operation tables, construction forms (explicit type arguments, inference
from a declared type, empty construction, the three-alternative compile-time rule), initial
element assignability, `Map` `key: value` entries including the repeated-key rule, the three
failure sentences, type-argument invariance, the non-reified type-test rule, and a
user-declared nominal generic class.

Section 11's operation tables are the entire normative source for collection behavior, so
each test's expected bytes are derived by reading the table signatures: an entry typed
`func get(index: Integer): T` yields a printable value, and an entry typed
`func add(element: T)` with no declared return type is Unit-returning and is therefore used
only as a statement. Asserting a rendering for `Unit` would invent semantics the section
never gives, so no test prints an `add`, `set`, `put`, `push`, or `clear` result.

Two oracle-scope decisions where the specification is deliberately silent:

* **"keeps its position" is not asserted.** Section 11 states that a repeated `Map` key
  "keeps its position and takes the latest value" but gives `Map` no iteration order in this
  revision, so no position-derived observable exists. SOL-TCK-0088 asserts only the two
  consequences that *are* observable -- the entry count does not grow, and `get` returns the
  value written last -- which keeps the requirement testable without inventing deferred
  ordering semantics.
* **`List.isEmpty` is asserted only where the table types it.** The `List<T>` row does list
  `val isEmpty: Boolean`, and SOL-TCK-0076 asserts both transitions (non-empty before
  `clear`, empty after), so an implementation whose `clear` silently kept elements fails.

**A test that would have passed under a covariant implementation, and its fix.** The first
draft of the invariance test assigned `List<Integer>` to `List<Number>`. That pair looks
like a subtype relationship but is not one: section 4 states "Widening is a coercion
applied at conversion sites, **not a subtype relation**: numeric types remain siblings
under `Number`". The assignment is therefore rejected by a covariant implementation too, so
the test would have certified invariance while proving nothing. SOL-TCK-0091 now uses a
genuine nominal subtype (`class Derived extends Base`), and SOL-TCK-0090 is its positive
control, differing only in the declared type argument, so the rejection is isolated to
invariance.

**Assertion strength follows the specification's vocabulary, not the implementation's.**
For the three compile-time rejections section 11 states without naming a code, the manifests
differ deliberately. SOL-TCK-0086 asserts the `TYPE` family because the sentence requires
each initial element to be "assignable to the element type" and assignability is a typing
relation no conforming implementation can decide outside type checking. SOL-TCK-0084,
0085, 0087 and 0091 assert a bare compile-time rejection with **no code and no family**,
because their sentences ("is a compile-time error", "is not valid in a `Map` construction")
name neither a code nor a phase, and pinning one would test an implementation choice. This
was not hypothetical: the implementation reports the un-inferable construction of
SOL-TCK-0084 with *both* `SOLV-TYPE-030` and `SOLV-RESOL-004`, so an asserted `TYPE` family
would have encoded one admissible phase as though it were the only correct one. Note that
the specification does name `SOLV-TYPE-001` for 0084-shaped mismatches in only two narrow
places -- a static declaration initializer (section 7) and a message argument (sections 22,
23) -- neither of which is a collection construction, so no code could be pinned.

### Runtime-category provenance: a taxonomy that existed only in a schema

While preparing the collection failure tests it emerged that every `runtimeCategory` oracle
in the corpus -- and there were many already -- cited "TCK.md section 8" as the source of
the closed category taxonomy. **TCK.md section 8 contains no such taxonomy.** It defines the
JSON wire format and, in section 8.1, the outcome-matching state machine; it requires that
structured language runtime failures cross the boundary but never enumerates them. The nine
members (`ARITHMETIC_ERROR`, `CAST_FAILURE`, `NULL_DEREFERENCE`, `COLLECTION_FAILURE`,
`INDEX_OUT_OF_BOUNDS`, `UNCAUGHT_EXCEPTION`, `REGEX_FAILURE`, `RESULT_WRONG_VARIANT`,
`OTHER_RUNTIME_ERROR`) existed only in the two JSON schemas this TCK itself authors, so the
human-facing protocol specification was incomplete and twenty-one oracle comments across
corpus, inventory, and review documents attributed a TCK editorial artifact to the
governing brief. Twenty-three citations across seventeen files were rewritten.

Two corrections were made:

1. `protocol/protocol.md` gained sections 4.1 and 4.2. Section 4.1 defines the category set
   as TCK-owned, states that it is never taken from an implementation's own error strings or
   enum values, and maps every member to a verbatim sentence of `LANGUAGE_SPEC.md` with its
   section. Section 4.2 does the same for the diagnostic `family` values, whose basis TCK.md
   does provide (section 6 recognizes the families, section 7 permits asserting one) but
   which were likewise undefined as protocol values.
2. Every false "TCK.md section 8" taxonomy citation was rewritten to
   `protocol.md section 4.1`. The legitimate citations were left alone after checking them:
   `runner/tck_runner/protocol.py` cites section 8 for the wire format and
   `runner/tck_runner/outcome.py` cites section 8.1 for the state machine, and both are
   correct.

`NULL_DEREFERENCE` and `REGEX_FAILURE` are now explicitly **reserved**: `LANGUAGE_SPEC.md`
has no vocabulary for a null-dereference fault and none for a regex-evaluation fault, so
asserting either would claim a language guarantee the specification does not make. They
remain in the schema so an adapter can classify an observed failure without a protocol
violation, and `test_oracle_quotes.check_reserved_categories_unused` asserts that no
manifest uses them and that the protocol document still carries the reservation.

The check is bidirectional. Besides forbidding the two reserved categories, it requires
that every category a manifest *does* assert still appears in the section 4.1 table, so a
live oracle can never rest on a rule the protocol document stopped stating.
Falsifiability was proven by deleting the `CAST_FAILURE` row and watching SOL-TCK-0048's
category fail. A vacuity assertion confirms the corpus asserts at least one category and
that the table tabulates at least as many as are used -- without it, an empty table and an
empty corpus would satisfy the loop trivially.

A second audit extends the same verbatim rule to protocol.md section 4.1 itself. That
table's middle column is headed *Defining specification text*, so a paraphrase placed in it
would be indistinguishable from a rule by anyone auditing an oracle -- which is exactly how
the taxonomy came to be attributed to TCK.md section 8 in the first place. `check_runtime_category_table_citations`
requires each quoted passage in that column to occur verbatim in LANGUAGE_SPEC.md, requires
every non-editorial row to carry such a quotation at all, and requires the two rows whose
Section cell is an em dash to quote nothing. The `OTHER_RUNTIME_ERROR` row failed that rule
on writing: its cell opened with a coined phrase in quotation marks, which was rewritten to
state explicitly that the row is editorial rather than a specification quotation.

The row set is not asserted by a literal count either. It is derived from the schema's own
`runtimeCategory` enum and must equal that enum minus the reserved members, so the
reservation and the table cannot drift apart: a `NULL_DEREFERENCE` row was injected to
confirm both the set-equality check and the reservation check reject it. Three separate
injections -- a paraphrased sentence, a quotation in the editorial row, and a spec-grounded
row for a reserved category -- each produced a failing assertion, and all were reverted.

### A methodology failure during this batch, and the guards that now prevent it

The first probe of section 11 was written as `func main() { ... }`. Solvik rejected every
program with `SOLV-SEM-001` ("an explicit 'main' function is not supported; executable
top-level statements form the entry point", section 20), so **every probe printed nothing
and exited nonzero**. The probe harness read only stdout and reported the empty results as
if the operations had returned nothing, and the resulting note claimed section 11 behavior
"matches spec exactly". That claim was an artifact of never compiling successfully. The
batch was re-probed from top-level statements, which is the form the specification
mandates.

Three structural invariants were added to `test_oracle_quotes.py` so the class of error
fails loudly instead of silently:

* `check_success_tests_are_executable` -- no `SUCCESS` test may declare `func main`. A
  program containing a construct the specification makes categorically invalid can never
  produce its oracle, so such a test reports a failure against an expectation it never
  exercised. The check runs against comment-stripped source, so oracle prose cannot trip it
  and a real declaration cannot hide in it. Falsifiability was proven by injecting
  `func main() { print(1) }` into a `SUCCESS` test and observing the failure.
* `check_corpus_dirs_are_complete` -- every corpus directory must contain both a program and
  a manifest. A directory with a program but no manifest is invisible to every other corpus
  check and to the runner: it is never executed, counted, or reported, so coverage silently
  vanishes while the inventory still marks the requirement `tested`. This guard caught eight
  scaffold directories left empty during this batch.
* `check_reserved_categories_unused` -- described above.

The existing `check_empty_stdout_is_intentional` guard would already have caught the shape
of the failure *if the manifests had been written*, since a `SUCCESS` test that calls `print`
and expects empty stdout is rejected. It did not fire only because the batch was abandoned
before manifests existed -- which is precisely the gap `check_corpus_dirs_are_complete`
closes.

### Fabricated spec quotations in the draft section-11 oracles

The oracle comments drafted for the first two collection tests quoted the specification as
saying `List.add` "appends and returns the new length as Int", that `get`/`set`/`remove`
"return the previous value wrapped in `Option`", that `Set` "is an unordered collection of
distinct values", and that `contains` "reports membership". **None of these sentences exist
in `LANGUAGE_SPEC.md`** (`grep -c` returns 0 for each). They were plausible-sounding
reconstructions of a collection API written from expectation rather than from the document,
and they were in the process of becoming the stated basis for four oracles. The drafts were
rewritten from section 11's actual operation table, which types `add`, `set`, and `put` as
returning no value at all -- directly contradicting the invented "returns the new length"
and "returns the previous value" claims.

The same scan over the whole corpus found two genuine citation defects in already-committed
tests, both fixed:

* SOL-TCK-0002 cited a prose sentence, "Concatenation binds looser than arithmetic, so
  `a + b .. c` is `(a + b) .. c`", which does not appear in the document. The normative
  source is section 3's ordered precedence list, which places `..` at item 6 and `+`, `-` at
  item 7; the oracle's *derivation* was correct and the expected bytes did not change, but
  the comment claimed a quotation that does not exist. It now cites the list and is labelled
  a paraphrase.
* SOL-TCK-0047 spliced section 18's introductory labels onto code-block contents to form
  quoted "sentences" ("Support type tests: `if (value is String) { print(value) }`"). Those
  labels are section content, not rules.

SOL-TCK-0047 also did not demonstrate what it claimed: `v` was declared `Dog`, so the
member read inside `if (v is Dog)` needed no refinement and the test would pass under an
implementation that never narrows. It now declares `val v: Animal = Dog()` (with
`open class Animal`, since classes are final by default) so the read of `fetch`, which
`Animal` does not declare, compiles *only* if the compiler narrowed `v`, which is what the
quoted section 18 sentence requires. Its oracle bytes were unchanged by the fix.

Quoted-prose fidelity is now machine-checked rather than asserted: the scan used here --
every double-quoted passage of six or more words in an oracle comment must occur verbatim
in the specification after normalization, with `...` treated as an editorial elision whose
fragments must each appear -- is the same rule applied to `normativeQuotes` in the
requirement inventory. It reduced the corpus to zero non-verbatim prose claims.

### A false-coverage bug in the runner itself (found while auditing, not while testing)

Auditing the new section-11 links against the corpus by hand -- checking that every
manifest's requirement IDs exist and that every requirement's declared tests exist --
turned up an asymmetry in the shipped runner. The forward direction was enforced:
`check_manifest_against_inventory` rejects a manifest citing an unknown requirement. The
reverse direction was not checked at all, and `untested_requirements` / `_coverage` /
`_tested_count` all treat a requirement as tested whenever its `tests` array is merely
*non-empty*, never whether the named tests exist.

The consequence was reproduced by injection, not reasoned about: setting
`REQ-0900.tests = ["SOL-TCK-9999"]` -- a test that does not exist -- left
`python3 tck/runner/tck_cli.py validate` printing `requirement coverage: 58 tested / 58
active` and exiting **0**. Since TCK.md sections 5 and 5.1 gate aggregate certification on
requirement coverage, and `untested_requirements` feeds the report's gap list, any renamed,
renumbered, or deleted test would silently inflate coverage toward a certifiable profile.
The corpus was one careless renumbering away from that state, and this batch had already
renumbered test IDs once.

`inventory.validate_test_linkage` now closes the gap. Both `validate` and `run` load the
corpus and then require that every test named by an *active* requirement appear among the
loaded `testId` values, raising `InventoryError` (exit 1) otherwise; the check runs *before*
the coverage numbers are derived, so a broken link cannot be reported as a statistic. It
deliberately takes the corpus ID set as a required parameter rather than a default, because
an omitted argument silently disabling a coverage guard is the exact defect being fixed. Two
boundary cases are pinned by self-tests rather than assumed: a *retired* requirement's tests
are not audited (they may have been deleted alongside it), and an *empty* test list remains
a coverage gap reported through the gap list rather than a hard linkage error -- otherwise
every legitimately not-yet-tested requirement would abort validation.

After the fix, the same injection exits 1 on both paths while the clean corpus still exits
0, and the guard's rejection path is itself exercised by five new assertions in
`test_manifest_and_inventory.linkage_tests` -- verified to fail when the guard is neutered
into a no-op. The corpus is clean: all 91 manifests and all 58 requirements link in both
directions.

### Batch: file inclusion and modules (REQ-1000..REQ-1011)

Twelve requirements and thirteen tests covering LANGUAGE_SPEC section 20. This is the first
corpus batch that is genuinely multi-file: each test stages a `lib/` subtree through
`fixtureRoot: "."`, and the adapter's `_stage_into_run` copies the whole staged tree into the
run workspace, so relative include paths survive the compile/execute split.

Section 20 is also the richest source of **exact** diagnostic codes in the specification,
because unlike other sections it carries a table headed *Required diagnostics* that pairs
each condition with a stable code and a primary span. Seven tests therefore pin exact codes
(`SOLV-RESOL-002`, `-008`, `-011`, `-012`, `-013`, `-014`) rather than families. Two rules
whose governing sentences name no code -- alias-suppresses-module-name, and non-transitive
prefixes -- assert the `RESOL` family only.

Assertion strength again tracks what the text supports, not what the implementation emits:

* **`SOLV-RESOL-012` (invalid module name)** is pinned for `module Bad_Name`, because that
  spelling is a lexically valid single identifier and therefore *only* the naming rule can
  reject it. The dotted form `module com.example.math` is covered by the same naming
  sentence but is rejected earlier by the parser, and `module module` likewise; the test
  records that those shapes never reach the check the registry row describes, so asserting
  a code for them would certify which check happens to run first rather than the rule.
* **`SOLV-RESOL-011` (cycle)** is pinned using a *self*-include, where the include that
  closes the cycle is unambiguously the self-reference. In a two-file cycle the closing edge
  depends on traversal order, which the sentence leaves to "the include that closes the
  cycle" without pinning which edge that is; naming a specific directive there would be a
  guess.
* **`SOLV-RESOL-007` vs `-008` vs `-009`** -- the registry separates an invalid path (007),
  a missing file (008), and a path that names something that is not a file (009), and it
  distinguishes them by *primary span*: 007 is "path literal", 008 and 009 are both "include
  directive". The two unambiguous cases were probed and the implementation matches the
  registry exactly, including the span: a nonexistent path yields `SOLV-RESOL-008` spanning
  `[0..22)` of `include "lib/gone.sol"` -- the directive, not the literal -- while a
  directory argument yields `SOLV-RESOL-007` spanning `[8..15)` of `"lib/d"` -- the literal,
  not the directive. A directory is nevertheless on the 007/009 boundary as far as the
  specification's *text* goes: a directory genuinely is not a file, which is 009's stated
  condition, so choosing between 007 and 009 there would be an inference from span bookkeeping
  rather than from a normative sentence. Only the not-found case is asserted.

  **Why no manifest asserts a location, even though these spans were observed exactly.** The
  schema's location fields demand byte offsets, and the registry names only a construct, so a
  location oracle would have to translate a construct into boundaries the specification never
  states -- and that translation is not even univalent here. Section 20 says an include "ends
  with an explicit or lexically inserted `SEMI`", which makes a defensible case that the
  terminating semicolon lies *inside* the directive and therefore inside the primary span;
  the observed `[0..22)` stops before it. Both readings are consistent with the registry, so
  asserting either boundary would pin an implementation choice. The oracle comment in
  SOL-TCK-0098 states this limitation rather than silently omitting it. The primary span is asserted as "the include
  directive" nowhere in a manifest: the schema's location fields demand byte offsets, which
  the registry does not pin.

**Order-dependent oracles are legitimate here, and the reason matters.** Section 11 coverage
deliberately avoids any order-dependent claim because the specification gives `Map` no
iteration order. Section 20 is the opposite case: it states "Expansion is depth-first and
left-to-right" and then gives a worked example enumerating the resulting item order.
SOL-TCK-0103 executes that example literally and expects `[common] [a] [b] [root] `. Each
piece is bracketed and space-terminated so the *boundaries* between the four contributions
are observable; the naive `commonabroot` could also be produced by a different grouping of
the same characters, which the delimited form cannot. SOL-TCK-0102 covers the companion
rule from the same bullet -- an included top-level statement never runs twice -- using the
diamond the sentence names, expecting exactly one `[L]`.

**Two probe-harness errors that would have produced false results, and what fixed them.**
Neither was a language defect; both were the harness generating invalid programs, which the
previous `func main` incident had already shown to be the dangerous failure mode:

1. The first harness wrote single-line method bodies (`func add(...): Integer { return a + b }`).
   Section 16's semicolon-insertion rule terminates a `return` at a physical newline, so `}`
   on the same line is a genuine parse error and *every* multi-file probe came back
   `SOLV-PARS-001`/`SOLV-PARS-002`. Read carelessly, "all four include forms failed" looks
   like a broken include system.
2. A fixture named a function `val`. `val` is a reserved word, so the reference was a parse
   error, and the non-transitivity test and its positive control both "failed" for a reason
   unrelated to the rule under test.

Both were fixed by generating valid programs, after which the non-transitivity test yields
`SOLV-RESOL-015` while its control prints `7`, and the include rules behave exactly as
specified. A third subtlety was caught before authoring: include paths resolve relative to
the *including* file, so an inner file must name its sibling `"deep.sol"`; writing
`"lib/deep.sol"` there would target `lib/lib/deep.sol` and yield a not-found rejection that
would masquerade as a non-transitivity result. The fixtures and their oracle comments state
this explicitly, because the wrong layout would make the test pass for the wrong reason.

The exact-code rejections double as proof that multi-file staging works end to end: a
`SOLV-RESOL-002` duplicate-within-module or `SOLV-RESOL-012` bad module name can only be
produced if the included file was actually staged and parsed. Had staging dropped `lib/`,
those tests would report `SOLV-RESOL-008` (not found) and fail their manifests rather than
passing vacuously.

### Batch: Regex and switch dispatch (REQ-1100..REQ-1109)

Ten requirements and fourteen tests, covering LANGUAGE_SPEC section 14 plus one section 13
rule and one section 5 rule that only became testable once a `Regex`-returning API existed.

Section 14 is unusually well-specified for oracles: the complete-input rule for `matches`,
left-to-right non-overlapping iteration, exclusive `end`, literal replacements, and two
equality rows all state exact observable behavior. Assertion strength still tracks the text:

* **The rejection tests assert neither code nor family.** "Backreferences, lookaround,
  embedded flags, and engine-specific extensions are rejected" names a condition and an
  outcome but no code, and the required-diagnostics registry has no `Regex` row at all. The
  implementation does report `SOLV-TYPE-035` for all three constructs, but the phrase "are
  rejected" forces no analysis phase, so a conforming implementation could reject at lex or
  parse level. Asserting TYPE would convert an ambiguity into a requirement the specification
  never states.
* **Negative programs are built to be discriminating.** `a(?=b)` is tested against "ab" and
  `(?i)abc` against "ABC" -- inputs a supporting engine would *match* -- so an implementation
  that wrongly accepts the pattern cannot also satisfy the manifest by coincidence. One
  construct per test, because a mixed program would surface only the first rejection and
  silently stop covering the rest.
* **`matches` is pinned with both an anchored and an unanchored pattern.** `^\d+$` gives the
  same verdict under complete-input and prefix readings, so it cannot discriminate; `\d+`
  against "12a" is the case where only the complete-input reading answers false, and that is
  the load-bearing assertion.
* **`end` exclusivity is pinned by spans, not by prose.** Hand-derived offsets for
  "a1b22c333" are 1:2, 3:5, 6:9; an inclusive reading yields 1:3, 3:6, 6:10. Unlike section
  11, where `Map` is given no iteration order, section 14 states left-to-right order, so
  order is asserted here.

**Two oracle errors, both caught mechanically, neither by the implementation's consent.**
Each is recorded because the failure mode -- a plausible derivation that happens to agree
with an implementation -- is the one this TCK exists to prevent.

1. `RegexMatch` equality was first derived as `find("ab12y") == find("cd12y") -> false`, on
   the reasoning that different subjects must give different snapshots. Both strings place
   the digit run `12` at offset 2--4, so the immutable snapshots are *identical* and the
   correct answer is `true`. The runner reported the mismatch. The correction changes the
   second subject to `zzab12y`, which places the same `12` at offset 4--6: `value` stays
   equal while `start`/`end` differ, so the pair now distinguishes snapshot equality from
   value-only equality, which the original pair could not. Choosing by *offset* rather than by
   *digits* is what makes `start` observable at all.
2. The `findAll` oracle omitted `value:` from the emitted token text, reporting a mismatch
   purely against its own program. The specification fixes the offsets, not the print
   statement, so the fix corrected the expected string to match what the source prints,
   leaving the derived offsets -- the part actually derived from the spec -- untouched.

**A guard designed for copy-paste caught a design weakness instead.**
`check_duplicate_oracles` rejects two SUCCESS tests with different programs sharing one exact
stdout. Splitting the section 14 `switch` example into three single-input tests gave
SOL-TCK-0117 the oracle `other`, colliding with an unrelated integer-`switch` test that also
prints `other`. The collision was harmless but the guard's objection was sound: three
programs printing one bare word each is weak coverage. All three tests now drive the example
with all three inputs in a different order and print one bracketed token per arm, so each
oracle is a distinct sequence, every arm appears in multiple dispatch positions, and an extra
or omitted print perturbs the sequence. The guard was not weakened.

**The `RegexMatch?` rejection asserts a family, not a code.** `find` returns `RegexMatch?`
and `value` is declared on the non-null `RegexMatch`, so SOL-TCK-0114 needs the section 5
assignability rule -- whose vocabulary forces a type-checking phase, justifying `TYPE` -- but
the registry names no code for member access on a nullable receiver, and the code the
implementation emits for it does not appear anywhere in the specification.

**Two specification gaps found while probing, deliberately not asserted.** The
required-diagnostics registry has no `Regex` rows at all, so pattern-constructor rejections
carry an implementation code with no specification basis. Separately, the implementation
emits `SOLV-LEX-003` for an unescapable backslash in a string literal and `SOLV-TYPE-004` for
`null == "x"`, and neither code occurs in the specification: section 3 states the
one-null-result rule outright ("if exactly one value is `null`, the result is `false` and no
user code runs") without registering a diagnostic for it. Both are reported as gaps rather
than encoded as conformance requirements. Group indices outside the declared range are also
left untested: `group(index): String?` says what a *non-participating* group returns but not
what an out-of-range index yields, so any oracle there would invent behavior. Out-of-range
behavior was probed before being excluded, not assumed.

**Section 19 is not testable and is recorded as such.** "When language features conflict,
prefer: compile-time correctness; deterministic syntax; ..." is a design-time priority
ordering for resolving conflicts, not a claim about any program's observable behavior. The
only statement in the section addressed to implementations -- "Do not copy TypeScript's
unsound `any` behavior or JavaScript's automatic semicolon insertion behavior" -- is a
constraint on the language's *design*, and the observable consequence of its second half is
already covered by the lexical rules in section 16. A conformance test would have to pick a
feature conflict and assert one resolution as mandated, which would manufacture semantics
rather than check them. It stays listed as uncovered, like the section 12 exhaustiveness
items and the section 13 default-count clause.

## Batch: expression-oriented constructs (REQ-1200..REQ-1216)

17 requirements and 23 tests (SOL-TCK-0119..SOL-TCK-0141) from LANGUAGE_SPEC section 21
and its subsections. Each expectation was authored from the quoted normative text and only
then run against the IUT to detect a discrepancy; none was captured from the IUT. Three
places where a careless oracle would have followed the implementation instead of the
specification came up, and each resolved toward the specification:

* **The numeric-join rejection is the load-bearing test.** `if (c) { 1 } else { 1L }`
  joining to `Number` (accepted, SOL-TCK-0134) is *also* what an implementation with
  numeric promotion produces, so the acceptance alone proves nothing. SOL-TCK-0135 asserts
  the join is **not** `Long`, which only the spec's "No numeric promotion or widening" rule
  implies.
* **`SEM_BLOCK_RESULT_REQUIRED` fires only on abrupt completion.** Section 21.5's own
  example ends a non-last `case` with a bare `break`, so a "block whose tail is a void
  call" expectation would be wrong; only a `return`/`break`/`continue` tail carries no
  value. That came from the spec example, not from launcher output.
* **Codes are asserted only where section 21 names them.** Section 21.9's registry lists
  exactly six codes for this section and each is pinned exactly once. For a local
  initializer the specification names `SOLV-TYPE-001` only for a *static* initializer, so
  SOL-TCK-0135 asserts a bare rejection; the IUT additionally reports a code that occurs
  nowhere in the specification there, and asserting it would have been capture-from-IUT.
* **One ACCEPTED result was checked rather than assumed.** `if (true) { print("x") } else { 1 }`
  is accepted because section 6 makes `print` return `Unit` and section 21.7 joins `Unit`
  and `Integer` to `Any`, so it is not `SEM_BLOCK_RESULT_REQUIRED` and no diagnostic was
  asserted for it.

Two SUCCESS tests were also re-oracled after the corpus-wide duplicate-stdout guard fired
on them (see the earlier invariant note): SOL-TCK-0126 coincidentally emitted the same
`3` as SOL-TCK-0044 and SOL-TCK-0131 the same `two` as SOL-TCK-0069. Both are genuine
coincidences from different programs, but each expected stream is now bracketed with
literal markers so it is independently derivable from its own requirement plus the test's
own source, instead of relying on a bare value an earlier test happened to share.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0119, SOL-TCK-0120, SOL-TCK-0121 | REQ-1200 | 21.1 Terms / 21.2 Block expressions | stdout `d42`, exit 0; stdout `25`, exit 0; stdout `6`, exit 0. LANGUAGE_SPEC 21.1 Terms / 21.2 Block expressions: A block expression has its own lexical scope, its earlier statements execute in source order, and a local declared inside it is visible to later items in that block and nowhere outside it. Scope isolation is observable through two block expressions that each declare a local of the same name and each mutate one outer variable: the specification fixes the interleaving of prints and the running total, so the exact stdout is derived rather than observed. The negative half (a block-local name visible outside the block) is SOL-TCK-0140 | No |
| SOL-TCK-0122, SOL-TCK-0123, SOL-TCK-0124 | REQ-1201 | 21.2 Block expressions / 21.9 required diagnostics | COMPILE_ERROR `SOLV-SEM-041`; COMPILE_ERROR `SOLV-SEM-041`; COMPILE_ERROR `SOLV-SEM-041`. LANGUAGE_SPEC 21.2 Block expressions / 21.9 required diagnostics: A value-required block whose normally completing path reaches `}` without a tail expression is the compile-time error SEM_BLOCK_RESULT_REQUIRED, and an empty block, a block ending in a local declaration, and a block ending in an assignment are all invalid in expression position. Exact code from the 21.9 registry. All three invalid shapes named by the spec share that one code, so they legitimately share the expectation; the oracle-independence guard deliberately exempts rejection tests because a rejection expectation is a rule, not a derived byte stream | No |
| SOL-TCK-0125 | REQ-1202 | 21.1 Terms / 21.2 Block expressions | COMPILE_ERROR `SOLV-TYPE-012`. LANGUAGE_SPEC 21.1 Terms / 21.2 Block expressions: A path that completes abruptly carries no value and does not participate in result joining; a value-required block whose every path completes abruptly has type Nothing and never evaluates a tail expression. Nothing is a type no value inhabits, so an all-abrupt block can never satisfy a value-returning function; the value-returning-function rule is the spec-named SOLV-TYPE-012. This is what separates `Nothing` from a fabricated `Unit`/zero/`null` result, which 21.1 forbids | No |
| SOL-TCK-0126 | REQ-1203 | 21.2 Block expressions | stdout `[3]`, exit 0. LANGUAGE_SPEC 21.2 Block expressions: A standalone scope block in statement position remains a statement block, so it contributes no value and its locals stay inside it. The block must execute its statements in order and produce nothing; the outer variable it assigns is the only observable, so the oracle is a single value | No |
| SOL-TCK-0127 | REQ-1204 | 21.3 Semicolons and tail expressions | stdout `42424242`, exit 0. LANGUAGE_SPEC 21.3 Semicolons and tail expressions: Explicit and synthesized semicolons are the same token with the same meaning, token origin is never inspected to decide whether a value exists, and comments and blank lines before `}` do not affect tail selection. Four spellings of the same block expression must all yield the same value and type, which the spec states directly; a single concatenated stdout proves the equivalence without asserting anything about formatting | No |
| SOL-TCK-0128, SOL-TCK-0129 | REQ-1205 | 21.4 `if` expressions | stdout `[negative][zero][positive]`, exit 0; COMPILE_ERROR (no code asserted; spec names none at this site). LANGUAGE_SPEC 21.4 `if` expressions: An expression-position `if` may chain through `else if`, every normally completing branch must produce a tail result, and the condition must be Boolean exactly as for statement `if`. The chained form is the spec's own example, driven with one input per arm so each arm's string appears in a fixed position. The non-Boolean half cannot pin a code: the implementation reports SOLV-TYPE-005, which appears zero times in the specification, so the expectation is the bare rejection the spec actually forces | No |
| SOL-TCK-0130 | REQ-1206 | 21.4 `if` expressions | stdout `[z][fallback]`, exit 0. LANGUAGE_SPEC 21.4 `if` expressions: Abrupt branches are excluded from result joining, so an `if` expression whose `else` completes abruptly still produces the value of its normally completing branch. Uses the specification's `requireName` example verbatim in shape: the `else` returns from the enclosing function, so only the non-null branch is a result of the `if`. Output is bracketed to make the two distinct arms distinguishable in one stream | No |
| SOL-TCK-0131 | REQ-1207 | 21.5 `switch` expressions | stdout `[two]`, exit 0. LANGUAGE_SPEC 21.5 `switch` expressions: A `switch` in expression position produces a value from its matched case body, while a statement `switch` may omit `default` and do nothing when no label matches. Both halves are observable in one program: the expression form yields a string, and a statement switch whose label does not match contributes nothing to stdout. The missing-`default` rejection for the expression form is already SOL-TCK-0012 under REQ-0204 | No |
| SOL-TCK-0132 | REQ-1208 | 21.5 `switch` expressions / 21.9 required diagnostics | COMPILE_ERROR `SOLV-SEM-041`. LANGUAGE_SPEC 21.5 `switch` expressions / 21.9 required diagnostics: Every normally completing case body, including `default`, must end in a tail expression, so a value-position case body ending in a declaration is SEM_BLOCK_RESULT_REQUIRED. The 21.9 primary span for SEM-041 is the offending block or case body, which is why a case body shares the block rule rather than acquiring a separate code | No |
| SOL-TCK-0133 | REQ-1209 | 21.5 `switch` expressions | stdout `Sone`, exit 0. LANGUAGE_SPEC 21.5 `switch` expressions: The scrutinee of a `switch` is evaluated exactly once. A scrutinee call that prints an observation marker makes exactly-once a byte-exact property: one additional evaluation would duplicate the marker, so the oracle discriminates the claim instead of merely tolerating it | No |
| SOL-TCK-0134, SOL-TCK-0135 | REQ-1210 | 21.7 Result types | stdout `1`, exit 0; COMPILE_ERROR (no code asserted; spec names none at this site). LANGUAGE_SPEC 21.7 Result types: A construct's result type is the nearest common declared supertype to which every normally completing branch result is assignable, with no numeric promotion or widening, so `if (c) { 1 } else { 1L }` has type `Number` and is not assignable to `Integer` or `Long`. The acceptance half binds the join to `Number`; the rejection half is what proves the join is not `Long`, which any numeric promotion would produce. The rejection is asserted as a bare rejection: the specification names SOLV-TYPE-001 for a static initializer, not for a local initializer, so no code is spec-mandated at this site | No |
| SOL-TCK-0136 | REQ-1211 | 21.7 Result types | stdout `[x]`, exit 0. LANGUAGE_SPEC 21.7 Result types: If exactly one branch can complete normally, its result type is the construct's result type, and `Unit` participates in the join as any other non-null value type. Two different branch types joining to their nearest common supertype is the same rule the join clause states; binding the result to `Any` is the specification's own worked example | No |
| SOL-TCK-0137 | REQ-1212 | 21.8 Expression contexts | stdout `10nnonzero2`, exit 0. LANGUAGE_SPEC 21.8 Expression contexts: Block, `if`, and `switch` expressions are accepted wherever the grammar accepts an expression, including assignment right-hand sides, call arguments, explicit `return` values, and nested expression constructs. Each named context appears once, and the oracle is the concatenation of the values each context must produce | No |
| SOL-TCK-0138 | REQ-1213 | 21.8 Expression contexts / 21.9 required diagnostics | COMPILE_ERROR `SOLV-TYPE-012`. LANGUAGE_SPEC 21.8 Expression contexts / 21.9 required diagnostics: A function body does not implicitly return its final expression, so a value-returning function whose body evaluates but does not return is rejected. The specification's own `invalid` example is used verbatim. It is rejected, and the value-returning-function reachability rule is named SOLV-TYPE-012, so that code is pinned; the implementation's additional SOLV-SEM-003 has zero occurrences in the specification and is deliberately not asserted | No |
| SOL-TCK-0139 | REQ-1214 | 21.6 Existing `match` expressions | stdout `ok30`, exit 0. LANGUAGE_SPEC 21.6 Existing `match` expressions: A `match` branch may use a block expression for multiple statements, following the same tail-result, scope, and typing rules as any other block expression. The spec's `Ok(value) => { println(...); value }` shape, with `print` instead of `println` so the expected bytes are platform-independent. Both arms are exercised so the marker and both values appear in one deterministic stream | No |
| SOL-TCK-0140 | REQ-1215 | 21.2 Block expressions / 21.1 Terms | COMPILE_ERROR `SOLV-RESOL-001`. LANGUAGE_SPEC 21.2 Block expressions / 21.1 Terms: A local declared inside a block expression is visible nowhere outside it, so referencing it after the block is an unknown-name error. Exact code from the reference-resolution rule that names SOLV-RESOL-001 for a name with no visible binding | No |
| SOL-TCK-0141 | REQ-1216 | 21.9 required diagnostics | COMPILE_ERROR `SOLV-SEM-044`. LANGUAGE_SPEC 21.9 required diagnostics: A class that declares `override func hashCode` must also declare `override func equals` in the same class declaration, which is SEM_HASHCODE_WITHOUT_EQUALS. The mirror image of the already-covered equals-without-hashCode rule (REQ-0002 / SOLV-TCK-0003). Exact code from the 21.9 registry | No |

### Two false divergences in the differential comparator, found by an adapter and by an audit

The JVM-vs-reference-subset run reported 2 diagnostic "disagreements" (SOL-TCK-0098,
SOL-TCK-0099) and they looked like legitimate findings about an incomplete second front
end. They were neither. Both sides reported the same code, the same family, and the same
reason; the only difference was the byte span, which the reference adapter does not emit.

The comparator projected diagnostics to `(family, code, startByteOffset, endByteOffset)`
and compared the tuples unconditionally, while `outcome._diagnostic_matches` -- the code
that actually judges a test -- compares a diagnostic component only when the oracle
declares it. The two halves of the same system applied different rules to the same
question. Checking the corpus settled which one was right: **none** of its 57 diagnostic
oracles declares a `location`, so every span comparison the comparator performed was
against an observable no oracle constrains, in exactly the way unguarded `stderr`
comparison was constrained only by luck. SOL-TCK-0100 "agreed" only because both sides
happened to report no span.

Fix: `_declared_diag_fields` derives the normative projection from the oracle, and a
difference confined to an undeclared component is reported as `unconstrained` rather than
counted. The corpus-wide effect is that the reference differential now reports
`disagreements=0 compared=8`, which is the honest answer: on every program both sides
judge, they agree on everything the specification fixes.

Two things about this are worth recording. First, the comparator had a test asserting the
wrong behavior -- "a span reported by one side only is a disagreement" -- which is what a
green suite looks like when a test was written to match an implementation instead of a
rule. It was replaced with paired assertions that a span is unconstrained under a
family/code oracle *and* a disagreement under one that declares a location, so the guard
now discriminates instead of merely remembering one answer. Second, nothing executed the
reference adapter before this audit at all. Its behavior was a number in a table, and a
"second independent implementation" nobody runs cannot make an agreement claim true;
`tests/test_reference_adapter.py` now drives the real runner over the real corpus against
it, and was proven to catch fabricated acceptance, corrupted output bytes, and an
invented diagnostic code by having each injected in turn.

## Batch: unchecked exceptions (REQ-1300..REQ-1317)

18 requirements and 24 tests (SOL-TCK-0142..SOL-TCK-0165) from LANGUAGE_SPEC section 22.
Each expectation was authored from the quoted normative text and only then run against the
IUT to detect a discrepancy; none was captured from the IUT.

Authoring this batch surfaced a genuine implementation defect, which was fixed in the
compiler *before* these expectations were accepted as passing. The three built-in exception
bases have no source declaration, and the reachability graph for catch matching was built
from source declarations alone, so the edges between the built-in bases were missing. A
handler written on the root `Exception` type therefore matched nothing throwables: a thrown
`ParseError` reached the program boundary instead of running the handler. The same truncated
graph also silenced the unreachable-clause diagnostic for a `catch (e: RuntimeException)`
written after a `catch (e: Exception)`. Both symptoms are consequences of section 22.1's
"declared superclass chain reaches one of the three built-in bases" and section 22.3's
"C's superclass chain reaches H", which are the rules SOL-TCK-0142/0143 and SOL-TCK-0157
assert. The expectation was written from that text; the launcher's refusal to satisfy it was
treated as a candidate implementation defect and investigated, not copied into the oracle.

Other points where a careless oracle would have followed the implementation:

* **`SOLV-TYPE-022` is deliberately not asserted.** SOL-TCK-0154 rejects constructing the
  built-in `RuntimeException`, which section 22.1 states is not constructible but for which
  it names no diagnostic code; the implementation reports a code that occurs nowhere in the
  specification. The oracle is a bare rejection.
* **`e.message` is the one place section 22.1 names `SOLV-RESOL-004`** for an exception
  member, so it is pinned; the catch-binding scope failure in SOL-TCK-0158 is a different
  rule (section 4's bare-name reference rule), pinned to the same code only because that
  rule names it.
* **The reservation rule is asserted in both directions.** SOL-TCK-0151/0152 reject
  `message`/`getMessage` on an exception type; SOL-TCK-0153 requires the identical
  declarations to compile and be readable on an unrelated class. An implementation that
  reserved the names globally passes the rejections and fails the acceptance, so the pair
  constrains the *scope* of the reservation rather than just its existence.
* **Ordering is the oracle for first-match-wins.** SOL-TCK-0156 writes the specific clause
  first and prints exactly one of two distinct markers, so a last-match or sorted
  implementation prints the other marker rather than merely a different ordering nobody
  notices.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0142, SOL-TCK-0143 | REQ-1300 | 22.1 Exception types / 22.3 `try`, `catch`, and `finally` | stdout 'handledbad int', exit 0; stdout 'deep', exit 0. LANGUAGE_SPEC 22.1 Exception types / 22.3 'try', 'catch', and 'finally': A guest exception type is a class whose declared superclass chain reaches one of the three built-in bases directly or transitively, and a handler whose type is a built-in base catches a thrown value whose chain reaches it. The three built-in bases have no source declaration, so a reachability graph built only from declarations truncates every chain at RuntimeException or ApplicationException and a handler on the root Exception type catches nothing. This is the load-bearing test for that rule: the observable is the handler actually running and printing the message, which no truncated graph can produce. Rejection halves of the same reachability question are SOLV-SEM-054 (an unrelated class is not a handler type) and the SOLV-SEM-055 unreachable clause | No |
| SOL-TCK-0144, SOL-TCK-0145 | REQ-1301 | 22.1 Exception types | stdout '[null]', exit 0; stdout '[bad int]', exit 0. LANGUAGE_SPEC 22.1 Exception types: Construction of a guest exception type accepts a single optional trailing String? message, and the synthesized getMessage() returns that message or null when no message argument was supplied. Both halves are needed: a construction with no message argument must store nothing and report null rather than an empty string, and a supplied message must be reported back exactly. Expected bytes are derived from section 5's rule that print renders null as the literal null and appends no separator, so the two tests are separate programs rather than one concatenated stream | No |
| SOL-TCK-0146 | REQ-1302 | 22.1 Exception types | COMPILE_ERROR 'SOLV-RESOL-004'. LANGUAGE_SPEC 22.1 Exception types: The message is a compiler-synthesized private slot and never a readable member, so reading e.message on an exception is the compile-time error SOLV-RESOL-004. The specification names the code for exactly this access, and the class here is a real exception type so the failure cannot be a misresolution of an unrelated name. Asserting the code is not capture-from-IUT: section 22.1 states it directly | No |
| SOL-TCK-0147, SOL-TCK-0148 | REQ-1303 | 22.1 Exception types / 22.6 Required diagnostics | COMPILE_ERROR 'SOLV-TYPE-001'; COMPILE_ERROR 'SOLV-TYPE-003'. LANGUAGE_SPEC 22.1 Exception types / 22.6 Required diagnostics: A message argument must be assignable to String? or the construction is SOLV-TYPE-001, and supplying more than one extra trailing argument is the arity error SOLV-TYPE-003. Two different codes for two different violations of the same construction, so they are separate requirements-level obligations with separate programs: a wrong-typed single argument and a well-typed surplus argument. Both codes are named for this exact rule in the section 22.6 paragraph | No |
| SOL-TCK-0149 | REQ-1304 | 22.1 Exception types | stdout '7[sub message]', exit 0. LANGUAGE_SPEC 22.1 Exception types: The message is independent of the class's declared constructor, so a subclass keeps its own declared parameters and the message is still the single trailing argument. The oracle requires both channels to be observed separately from one construction: the declared parameter reaching the constructor and the trailing argument becoming the message. Printing the declared field first and the message second is what distinguishes this from a construction that ignored the declared parameter or swallowed the message | No |
| SOL-TCK-0150 | REQ-1305 | 22.1 Exception types / 22.3 `try`, `catch`, and `finally` | stdout '[via base]', exit 0. LANGUAGE_SPEC 22.1 Exception types / 22.3 'try', 'catch', and 'finally': getMessage() is available on every guest exception type, including on a handler written on a base type, so a handler typed on a base can read the message of a value caught as that base. The handler is typed on the base rather than on the thrown class, which is what makes this a distinct obligation from reachability: the synthesized accessor must exist on the handler's own declared type, not merely on the runtime class. Expected bytes are the message text alone, derived from the message-argument rule | No |
| SOL-TCK-0151, SOL-TCK-0152, SOL-TCK-0153 | REQ-1306 | 22.1 Exception types / 22.6 Required diagnostics | COMPILE_ERROR 'SOLV-SEM-037'; COMPILE_ERROR 'SOLV-SEM-037'; stdout 'finefine', exit 0. LANGUAGE_SPEC 22.1 Exception types / 22.6 Required diagnostics: The names message and getMessage are reserved on every guest exception class and declaring either is SOLV-SEM-037, while on a class that is not a guest exception both remain ordinary user-declarable members. The reservation is bounded, so both halves are asserted: a declaration on an exception type is rejected with the named code, and the identical declarations on an unrelated class must compile and be readable. An implementation that reserved the names globally would pass the rejection test and fail this one. The off-hierarchy program reads the field and calls the method, so the acceptance is not merely a parse | No |
| SOL-TCK-0154, SOL-TCK-0155 | REQ-1307 | 22.1 Exception types | COMPILE_ERROR (no code asserted; the spec names none for this rule); stdout '[no config]', exit 0. LANGUAGE_SPEC 22.1 Exception types: The built-in bases Exception, RuntimeException and ApplicationException have no declaration and are not constructible, and serve instead as handler and superclass types. The non-constructibility half is asserted as a bare rejection: the specification states the rule but names no diagnostic code for it, and the implementation reports a code that occurs nowhere in the specification, so pinning that code would be capture-from-IUT. The accepted half uses ApplicationException as a handler type and is kept separate from the root-type reachability test so a failure names which base misbehaved | No |
| SOL-TCK-0156 | REQ-1308 | 22.3 `try`, `catch`, and `finally` | stdout 'specific', exit 0. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally': When the try block throws, the catch clauses are tried in source order and the first clause whose declared type matches the thrown runtime class runs. The discriminating detail is ordering: the specific clause is written first so exactly one marker is printed, and a last-match or declaration-sorted implementation would print the base marker instead. Printing only the winner, with the two markers distinct, makes the first-match-wins rule the sole explanation | No |
| SOL-TCK-0157 | REQ-1309 | 22.3 `try`, `catch`, and `finally` / 22.6 Required diagnostics | COMPILE_ERROR 'SOLV-SEM-055'. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally' / 22.6 Required diagnostics: A catch clause whose handler type is a subtype of an earlier clause's handler type can never run and is the compile-time error SOLV-SEM-055. The clauses are ordered with the root Exception type first and RuntimeException second, which is unreachable only if the graph records the built-in base edge. The same missing edge that broke matching also silenced this diagnostic, so this test pins the second symptom independently: an implementation that fixed matching but not unreachability analysis fails here | No |
| SOL-TCK-0158 | REQ-1310 | 22.3 `try`, `catch`, and `finally` | COMPILE_ERROR 'SOLV-RESOL-001'. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally': A catch binding has the clause's declared type, is scoped to that handler body only, is not visible after the clause, and may shadow an outer name. Invisibility after the clause is observable as an unknown-name resolution at the use site; section 4's reference rule names SOLV-RESOL-001 for a bare name resolving to nothing, and the binding name is unique in the program so no other rule can produce the failure | No |
| SOL-TCK-0159 | REQ-1311 | 22.3 `try`, `catch`, and `finally` | stdout '[one]done', exit 0. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally': Because each handler body has its own scope, two clauses in the same try may reuse the same binding name without conflict. Both clauses bind 'e' and the first one runs, so the oracle is the thrown message printed once plus a marker proving the try completed. A single-scope implementation would reject the program as a duplicate name, so acceptance itself is the observable | No |
| SOL-TCK-0160 | REQ-1312 | 22.3 `try`, `catch`, and `finally` | stdout 'outer[kept]', exit 0. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally': The catch binding is initialized before the handler body runs, so the handler may read it immediately and rethrow it with throw e. The inner handler rethrows the same value, which the outer handler observes with its message intact. This distinguishes an initialized binding from one merely declared but empty, which could not carry the message across | No |
| SOL-TCK-0161 | REQ-1313 | 22.3 `try`, `catch`, and `finally` / 22.6 Required diagnostics | COMPILE_ERROR 'SOLV-SEM-054'. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally' / 22.6 Required diagnostics: Declaring a handler type that is not a guest exception type is the compile-time error SOLV-SEM-054, reported on the type reference. The handler names an ordinary class whose superclass chain reaches none of the built-in bases, which is exactly the shape the rule rejects. Distinct from the throw-operand rule, which has its own code and its own requirement | No |
| SOL-TCK-0162 | REQ-1314 | 22.3 `try`, `catch`, and `finally` | stdout 'releasedhandled[p]', exit 0. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally': If no clause matches, the thrown value keeps propagating outward after the finally clause runs, and an enclosing handler above the throw site still receives it. The ordering between the finally body and the outer handler is the whole content of this rule, so the oracle is a single concatenated stream in which the release marker strictly precedes the handler marker. A finally that ran after propagation, or a value converted at the inner boundary, would reorder or destroy the sequence | No |
| SOL-TCK-0163 | REQ-1315 | 22.3 `try`, `catch`, and `finally` | stdout 'bodyfinafter', exit 0. LANGUAGE_SPEC 22.3 'try', 'catch', and 'finally': A try consists of a try block, zero or more catch clauses and an optional finally clause, so a try with no catch clause and a finally clause is accepted and the finally body runs on normal completion. This is the acceptance half of the rule whose rejection half is already covered: a try needs a catch *or* a finally, so a finally-only try must compile and must run its finally body. The marker after the whole statement proves the try completed normally rather than aborting | No |
| SOL-TCK-0164 | REQ-1316 | 22.2 `throw` | stdout '[5]', exit 0. LANGUAGE_SPEC 22.2 'throw': A throw completes abruptly and produces no value, so a throw as the final statement of a value-returning function satisfies the value-on-all-paths rule the same way return does. The function is value-returning and one of its paths ends in a throw rather than a return, so the program only compiles if the abrupt path counts as covering the value obligation. Printing the returned value proves the accepted path ran; an implementation that demanded a literal return would reject the program outright | No |
| SOL-TCK-0165 | REQ-1317 | 22.4 Propagation across call boundaries | stdout 'handled[two frames down]', exit 0. LANGUAGE_SPEC 22.4 Propagation across call boundaries: A thrown value crosses function-call boundaries during unwinding and is caught by a handler in any dynamically enclosing frame, and an ordinary call target must not itself terminate the program. The throw site and the handler are separated by two call frames, so the oracle proves the value travelled across both. Conversion at the inner call target would surface as an uncaught failure rather than the handler's own output | No |

## Batch: lexical basics (REQ-1400..REQ-1408)

9 requirements and 19 tests (SOL-TCK-0166..SOL-TCK-0184) from LANGUAGE_SPEC section 1,
"Lexical basics" -- the only purely lexical material in the specification and, until this
batch, the section with zero requirement coverage. Expectations were written from the quoted
text first and only then run against the IUT to detect a discrepancy; none was captured from
the IUT.

Section 1 names no diagnostic codes anywhere. Every rejection in this batch therefore carries
a bare `{}` expectation rather than a code: the specification normatively forbids each
construct but never names a diagnostic for forbidding it, and a fabricated code would assert a
fact the specification does not state (TCK.md section 6.1 forbids pinning codes the baseline
does not name). The acceptance tests carry the normative weight, and each was built so a
plausible *alternative lexer* fails it rather than merely re-emitting the same bytes:

* **Non-nesting block comments are pinned by both arms.** SOL-TCK-0171 requires
  `/* a /* b */` + statement to run (under non-nesting the first `*/` closes the comment),
  while SOL-TCK-0172 requires `/* a /* b */ */` to be rejected (under non-nesting the trailing
  ` */` survives as source text). A nesting lexer fails *both* arms -- it rejects 0171 as an
  unterminated outer comment and accepts 0172 as balanced nesting -- so the pair constrains
  the nesting rule itself, not just the fact that comments are ignored.
* **The "newlines remain visible to semicolon insertion" clause has its own pair.**
  SOL-TCK-0173 (line comment) and SOL-TCK-0174 (block comment spanning a newline) each print
  two statements' values with no separator; the second arm only compiles if the physical
  newline *inside* the comment still terminated the first statement. A lexer that blanked a
  block comment into nothing, discarding its newlines, merges the statements into a parse
  error and fails that arm while passing every test that only asks whether comments are
  skipped.
* **The 32-bit literal rule is pinned at the boundary, not near it.** SOL-TCK-0175 accepts
  and prints 2147483647; SOL-TCK-0176 rejects the very next integer. A lexer that simply
  rejected all large literals would fail the acceptance; one that wrapped or truncated would
  fail the rejection or print wrong bytes.
* **The `L` and `F` suffixes are type oracles, not display tests.** `5L` assigned to
  `Integer` is rejected (SOL-TCK-0178) and `1.5` assigned to `Float` is rejected
  (SOL-TCK-0181), which is what makes the acceptances in SOL-TCK-0177/0180 evidence that the
  suffix selected that type rather than the literal merely being accepted at *some* type.
* **Exponent semantics are pinned by equality, not by rendering.** SOL-TCK-0182 prints the
  results of `1.5e3 == 1500.0` and `16e-1 == 1.6`, so a lexer that parsed the significand and
  then ignored or mis-hung the exponent could not print `truetrue`, while a rendering-only
  expectation could be satisfied by accident.
* **Character literals are asserted in both directions.** SOL-TCK-0183 prints a plain scalar
  and the `\n` escape (one real newline of output, discriminating an escape-aware lexer from
  one emitting the two characters `\` `n`); SOL-TCK-0184 rejects a two-scalar literal, which
  a lexer that closed the literal at the interior quote and re-lexed the remainder might
  otherwise accept.
* The exact-oracle duplication guard forced two programs to be strengthened during authoring
  rather than merely renamed: the Double acceptance (SOL-TCK-0179) now also binds a
  `Float = 1.5F` in the same program, so its oracle additionally witnesses that Double and
  Float coexist as distinct declared types, and the comment tests print distinguishable value
  pairs so each expected stream identifies the rule that produced it.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0166, SOL-TCK-0167 | REQ-1400 | 1. Design Goals (Lexical basics) | stdout '42', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 1. Design Goals (Lexical basics): Identifiers match [A-Za-z_][A-Za-z0-9_]*, so a leading underscore or letter followed by letters, digits and underscores is a single identifier that can be bound and read back. The observable is the bound value printed through the identifier itself, so a lexer whose identifier grammar rejected any accepted character would fail to resolve the name and print nothing. Section 1 names no diagnostic code for identifier syntax, so the negative counterpart (a leading digit) is a bare rejection while this acceptance carries byte-exact stdout | No |
| SOL-TCK-0168, SOL-TCK-0169 | REQ-1401 | 1. Design Goals (Lexical basics) | COMPILE_ERROR (bare rejection; the spec names no code for this rule); COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 1. Design Goals (Lexical basics): Keywords are reserved and cannot be used as an identifier, and an identifier may not begin with a digit. Both are prohibitions, and section 1 names no code for either, so both programs carry a bare rejection expectation rather than a fabricated code. The keyword arm uses a word that is otherwise a perfectly ordinary identifier position, so an implementation that reserved only some keywords would accept the program and print the sentinel | No |
| SOL-TCK-0170 | REQ-1402 | 1. Design Goals (Lexical basics) | COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 1. Design Goals (Lexical basics): $ is not an identifier character, so a name containing a dollar sign is not a valid identifier. The specification states this as a property of the identifier character set rather than as a rule with a diagnostic, so the expectation is a bare rejection. The test places the dollar sign inside an otherwise-legal name, which distinguishes it from a lexer that merely disallowed a leading dollar sign | No |
| SOL-TCK-0171, SOL-TCK-0172 | REQ-1403 | 1. Design Goals (Lexical basics) | stdout 'ok', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 1. Design Goals (Lexical basics): // starts a line comment and /* ... */ is a NON-nesting block comment; a nesting block-comment lexer would fail to reject a trailing delimiter left after the outer comment closes. The two arms are the discriminating pair for non-nesting. Under the non-nesting rule the first */ closes the comment, so '/* a /* b */' followed by a statement compiles and runs, while '/* a /* b */ */' leaves the trailing */ as source text and must be rejected. A nesting lexer would reject the first program (unterminated outer comment) and accept the second (balanced nesting), so the pair pins the rule in both directions rather than only asserting that a comment is ignored | No |
| SOL-TCK-0173, SOL-TCK-0174 | REQ-1404 | 1. Design Goals (Lexical basics) | stdout '34', exit 0; stdout '56', exit 0. LANGUAGE_SPEC 1. Design Goals (Lexical basics): Comments are otherwise whitespace, but their physical newlines remain visible to semicolon insertion. Two independent observables are pinned by two programs. A block comment that terminates on the same physical line lets the following statement begin normally, while a block comment whose closing delimiter lands on a later line must still let the newline inside it separate the two statements -- the expected stream is the two values with no separator, which only holds if the comment's embedded newline was seen as a terminator rather than swallowed. A lexer that replaced a block comment with nothing at all, discarding its newlines, merges the statements and fails the second arm | No |
| SOL-TCK-0175, SOL-TCK-0176 | REQ-1405 | 1. Design Goals (Lexical basics) | stdout '2147483647', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 1. Design Goals (Lexical basics): A decimal integer literal has type Integer in the initial typed core, and a literal outside the signed 32-bit range is a compile-time error. The boundary is pinned on both sides: 2147483647 is accepted and printed exactly, and 2147483648 -- the next value -- is rejected. The acceptance is what makes the rejection meaningful, because a lexer that mis-parsed every large literal would also fail to print the accepted one. Section 1 states the range rule but names no diagnostic code, so the rejection is bare | No |
| SOL-TCK-0177, SOL-TCK-0178 | REQ-1406 | 1. Design Goals (Lexical basics) | stdout '9223372036854775807', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 1. Design Goals (Lexical basics): An L-suffixed literal has type Long, and a Long-suffixed literal is not usable where Integer is declared. The pair is a type oracle rather than a display test: the acceptance shows the suffix selecting Long for a literal far beyond the Integer range, and the rejection shows the same suffix really did select Long, because the identical suffixed literal is refused when Integer is declared. Section 1 names no code for the mismatch, so the rejection is bare | No |
| SOL-TCK-0179, SOL-TCK-0180, SOL-TCK-0181, SOL-TCK-0182 | REQ-1407 | 1. Design Goals (Lexical basics) | stdout '1.51.5', exit 0; stdout '1.5', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule); stdout 'truetrue', exit 0. LANGUAGE_SPEC 1. Design Goals (Lexical basics): A decimal floating-point literal with an optional exponent has type Double, an F suffix selects Float, and the exponent is applied to the significand. Three separate obligations, each with its own program. The exponent is pinned by equality rather than by rendering, so a lexer that read '1.5e3' as the literal 1.5 and then choked or ignored the exponent could not satisfy '1.5e3 == 1500.0'; a negative exponent is pinned the same way. The Double and Float declarations are pinned by accepting the matching form and rejecting the cross-assignment, which is what shows F really selects Float instead of the literal simply being accepted at some type | No |
| SOL-TCK-0183, SOL-TCK-0184 | REQ-1408 | 1. Design Goals (Lexical basics) | stdout 'A\n', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 1. Design Goals (Lexical basics): A character literal uses single quotes and contains exactly one Unicode scalar value or one escape supported by normal strings. The acceptance arm prints a plain scalar and an escape, so the escape is observed to produce the one character the string rule defines rather than its two source characters. The rejection arm puts two scalars inside the quotes, which the exactly-one rule forbids; section 1 names no diagnostic code, so the expectation is a bare rejection. A lexer that took the closing quote of a two-scalar literal and re-lexed the remainder could accidentally accept, which is why the negative arm is required | No |

## Third false divergence, found by the first: an "independent" partner that agreed for no reason

The §1 batch added lexical programs, and the reference front end answered six of them that
its maintainers had not selected. Three of those agreements were real. Three were not, and
the way they failed is the most instructive kind of failure this kit has produced, because the
adapter was *not* wrong about a language rule -- it was silently wrong about which programs it
was allowed to have an opinion on.

Its front end is a set of line regexes. A line that matches is understood; anything else
refuses the whole program. That makes its accepted set *implicit*: defined by which regexes
happen to match rather than by anything the maintainers chose. So when new programs arrived,
the subset grew by itself, and three of the three grew into unsound acceptances:

* it **bound `val class = 5`** and `val func = 5`, programs section 1 forbids ("keywords are
  reserved"), because the name regex matched `[A-Za-z_][A-Za-z0-9_]*` and nothing more;
* it **accepted `2147483648`** as an `Integer` literal, which section 1 forbids outright.

In each case the adapter reported `COMPILE_ACCEPTED` for a program the implementation under
test rejects. These are the strongest possible claims a compile-only partner can make -- it
has certified as legal something the specification does not permit -- and they are exactly
what a second opinion exists to expose.

The fix refuses rather than judges, and says why in the refusal, because in both cases the
underlying obstacle is a specification gap rather than an implementation gap:

* **Reserved words.** Section 1 says keywords are reserved and names no keyword list -- the
  same gap already recorded against the module-name rule. A binding name therefore cannot be
  certified legal at all, so the adapter refuses any name that occurs anywhere in the
  specification as a backticked bare lowercase word, extracted mechanically and *not curated*.
* **Out-of-range decimal literals.** The range rule is decidable but the diagnostic is not:
  section 1 names no code for the error, so the adapter can prove a program bad and still be
  unable to report it correctly. Refusal is the only sound output.

Both refusals cost the partner coverage and cannot produce a wrong verdict, which is the
correct trade for a component whose entire value is that its verdicts are trustworthy. The
version moved 1.0.0 -> 1.1.0 as part of this: the fingerprint is derived from name and
version, so a front end that reaches different verdicts on the same program must present a
different identity, or a differential result could not be attributed to the code that
produced it.

Two guards hold this in place, both proven falsifiable by injection:

* dropping one word from the refused set -- `exit`, chosen because it changes no program's
  behavior and no other guard observes it -- is caught by exactly one check, the one asserting
  the set still equals the mechanical extraction from LANGUAGE_SPEC.md. That is the strongest
  form of the property: the guard is the *only* thing standing between a comfortable curation
  and a partner that has quietly started deciding language questions it has no authority to
  decide.
* deleting the 32-bit range check is caught by two independent checks: the new refusal check,
  and the pre-existing answered-set allowlist, which notices the adapter answering a program
  nobody selected it for.

## Fourth false divergence: the comparator hid these divergences, and reported it as a clean run

Fixing the adapter made the three unsound acceptances disappear from the report, which is
correct but not sufficient, so they were reproduced against a saved copy of the pre-fix
adapter to confirm the kit would actually have caught them. It would not have.

Under the comparator as written, all three programs -- implementation under test
`COMPILE_REJECTED`, partner `COMPILE_ACCEPTED` -- came back `inconclusive` with an empty
`axes` list, in a run whose count line read `disagreements=0` and whose exit status was 0.
The single most fundamental divergence two implementations can have, one accepting a program
the other rejects, was reported as a clean differential run.

The cause was a collapsed concept. The comparator asked one question, "did this side produce a
language result", and defined a yes as *either* a rejected program *or* an accepted **and
executed** one. That definition is right for the observables that live after execution, but it
also quietly classified a compile-only *acceptance* as an absence of observation -- so the
partner's positive claim contributed nothing, and `usable() == False` on one side forced
`inconclusive` and suppressed every axis. The suppression was defended by a real and correct
principle, "comparing absences manufactures agreement", which was simply being applied to a
side that had not been absent at all: it had answered the only question it was asked.

The fix splits the collapsed question into the two that were always distinct:

* **A position on legality** -- raw compile status `COMPILE_ACCEPTED` or `COMPILE_REJECTED`.
  A compile-only adapter holds one, because accepting a program *is* a claim about it.
* **A full language result** -- a legal position plus an executed program, which is what makes
  stdout, stderr, exit status and runtime category comparable.

Two sides that both hold a position on legality are always comparable *on that axis*, even when
only one of them ever ran the program; the post-compile axes remain suppressed unless both are
full results, so a real execution is never compared against a side that performed none. A
refusal still holds no position, and neither does a crash, even though a crashed process may
have recorded a well-formed `COMPILE_ACCEPTED` before it died -- treating that as a position
would let a dead adapter be reported as having *judged* a program, and would disagree with
anything that answered normally. That exclusion is why the pre-existing crash test still
reports inconclusive rather than a fabricated objection.

Reproducing the pre-fix adapter against the fixed comparator now yields
`disagreements=3` with `compileAcceptance` named on all three programs, and exit 1. The
fixed adapter, of course, yields `disagreements=0`.

Ten assertions were added, and the fix is proven falsifiable in both of its parts: restoring
the single-level rule fires five of them, and removing only the infrastructure-event exclusion
fires four more, including the end-to-end CLI crash test. No assertion in the suite had
covered a compile-only acceptance opposite a rejection before this, which is the reason the
hole survived: the helper every acceptance-producing test used always executed the program, so
the shape that broke the comparator was never constructed.

The split created one new reporting obligation, and it was met rather than left implied. Once a
compile-only acceptance can make a test *comparable*, a pair of compile-only adapters would
produce a `compared` count that looks like a substantive differential run while having executed
nothing whatsoever -- a number whose old meaning ("behavior was checked on both sides") had been
silently narrowed by the fix. Comparable tests are now additionally marked and reported as
compared on legality alone when at least one side never ran the program, printed as a separate
line only when it applies so the established summary line keeps its shape. It is deliberately not
a vacuity flag and does not affect the exit code: a legality-only comparison really is a
comparison, and refusing to run programs is not the same as having nothing to compare. Both
directions are asserted -- an executed pair and a pair of rejections must *not* carry the marker,
and injecting "always false" or "ignore whether execution happened" each fire exactly the
assertions that should notice.

The general lesson is the one this kit keeps re-learning in a new costume: an agreement is only
evidence when you can say *why* both sides reached it. The first two false divergences were the
comparator comparing details the oracle never mandated; the third was a partner concurring on
programs it had never actually evaluated; the fourth was the comparator declaring silence where
one side had in fact spoken.

## Batch: enums, sealed types, and exhaustive match (REQ-1500..REQ-1509)

10 requirements and 21 tests (SOL-TCK-0185..SOL-TCK-0205) from LANGUAGE_SPEC section 12 --
the largest section in the specification with zero prior requirement coverage. Expectations
were written from the quoted normative text first and only then run against the IUT to
detect a discrepancy; none was captured from the IUT. All 21 passed on the first run
against the launcher, which is recorded here as an observation rather than as evidence of
correctness: the oracles were derived from the quoted text before the run, and no expectation
was altered to match a probe result. Two probe results did contradict what the section's
prose first suggested, and both were investigated to a conclusion before being written off --
the sealed transitive-subtype case described below, and generic enum construction, which
needs a declared type because section 12's `Result.Ok(5)` gives the compiler nothing from
which to infer the second type parameter. Neither became an oracle of its own.

Section 12 is almost entirely without named diagnostics. `SOLV-SEM-028`, `SOLV-SEM-029` and
`SOLV-SEM-030` occur ZERO times in the specification, although the implementation emits them
for precisely the sealed-construction, non-exhaustive-match and unreachable-branch rules
section 12 states. Every one of those rejections therefore carries a bare `{}` expectation.
This is the batch where that discipline matters most, because section 12 states many rules
and names almost none of their codes: pinning the codes the launcher happens to print would
have turned the oracle into a transcription of the implementation it exists to judge.

Exactly one code is pinned. `SOLV-SEM-039` appears verbatim in the specification's
required-diagnostics table and section 12 is the section stating the rule it reports, so
SOL-TCK-0198 pins it; nothing else in the batch is pinned to a code.

Two authoring decisions were forced by checking rather than assuming:

* **A suspected exhaustiveness defect turned out not to be one.** A `match` over a sealed
  `A` covering only its open subclass `B`, omitting `B`'s own subclass `C`, is *accepted*.
  Section 12 says the "complete transitive subtype set is closed", which first reads as
  requiring every transitive subtype to be named. It does not: a `b: B` branch already
  covers every `C`, and adding a `c: C` branch is rejected as unreachable -- which the
  implementation reports, correctly, via the source-order/reachability rule. The
  implementation is internally consistent and specification-conformant, and the acceptance
  is *entailed* by the rules rather than an exception to them. This was investigated to
  conclusion before any defect was reported, and the batch therefore asserts the
  specific-first/base-first pair (SOL-TCK-0192/0193) instead of a fabricated non-exhaustive
  expectation that would have failed against correct code.
* **The join rejection is bare, following a precedent already in the corpus.** Binding a
  match whose branches yield `Integer` and `Long` to a `Number` local is accepted
  (SOL-TCK-0203, the least-upper-bound computation observed), and to an `Integer` local is
  rejected (SOL-TCK-0204). The rejection asserts no code, because the specification names
  `SOLV-TYPE-001` for a *static* declaration initializer that is not assignable to its
  declared type and not for a local initializer -- the same reasoning already recorded for
  SOL-TCK-0135, applied consistently rather than re-decided.

Rules asserted so that a plausible alternative implementation fails an arm rather than
agreeing by accident:

* **Source order is pinned by both orders of the same two branches.** Specific-first
  compiles and prints `first10`, so first-match-wins is *observed* rather than assumed;
  base-first must be rejected because the specific branch is unreachable once the base
  branch covers it. An implementation choosing the last, or sorting by specificity, would
  have to accept the second program and print the other marker in the first.
* **Exhaustiveness is pinned three ways** (omitted variant rejected, variant restored
  accepted, wildcard accepted), so "covered" cannot collapse into "some branch happened to
  match at runtime" -- the wildcard arm is accepted precisely because section 12 permits a
  wildcard to substitute for full coverage.
* **The sealed file boundary needs a real two-file fixture**, because the rule is *about*
  the physical file boundary and a single-file program cannot express it; SOL-TCK-0199 is
  the same-file control, which is what makes the rejection mean "wrong file" rather than
  "sealed subclassing is broken". An implementation treating `include` as erasing the
  boundary -- which section 12 forbids explicitly -- fails the pair.
* **Branch scope isolation is pinned positively**: SOL-TCK-0205 declares a binding named
  `t` in *every* branch, which only compiles if each branch body is its own scope, and each
  branch's side effect and tail value are both observed, pinning the tail-result rule and
  the scope rule in one program.
* **Variant qualification is pinned by one spelling that is legal in one context and illegal
  in the other** (bare in a `match` over a known enum, qualified outside it), so the pair
  constrains the nesting rule itself rather than the variant's existence.

As in earlier batches, the exact-oracle duplication guard did real work rather than
bookkeeping: it rejected the first draft of ten accepted programs whose stdout was a bare
number, because an oracle of `1` does not record *which* rule produced it and collides with
unrelated tests. Each now prints a marker naming the rule, which also strengthened them --
`print("first" .. n)` witnesses first-match-wins, where `print(n)` witnessed only that some
branch ran. Marker strings are spec-derivable from section 3's `..` rule ("both operands are
rendered through `toString` and the result is always `String`, so `1 .. "x"` is `"1x"`"),
which is independent of the `+`-on-`String` conflict recorded elsewhere.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0185, SOL-TCK-0186 | REQ-1500 | 12. Enums, Sealed Types, and Exhaustive Match | stdout 'qualified', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: An enum variant is a nested nominal constructor: outside a context that already establishes the enum type it must be qualified as EnumName.Variant, and an unqualified variant name is not visible. The acceptance prints through the qualified spelling, and the rejection uses the bare name in the same position, so the pair pins qualification rather than merely the existence of the variant. The rejection is asserted bare: section 12 states that variants are nested and must be qualified outside an establishing context but names no diagnostic, and the resolver's own unknown-name code belongs to section 7's name-resolution rule, which is already covered separately | No |
| SOL-TCK-0187, SOL-TCK-0188 | REQ-1501 | 12. Enums, Sealed Types, and Exhaustive Match | stdout 'ok42', exit 0; stdout 'errbad', exit 0. LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: Inside a match over a known enum the unqualified variant pattern is permitted, so a match may bind variant payloads without re-qualifying the enum name. This is the counterpart half of REQ-1500, asserted from the same sentence, and it must be a separate program because the same spelling is legal in one context and illegal in the other -- a single program could not distinguish a lexer from a scoping rule. Both branches bind a payload and produce a distinct rendered string, so the test also shows the binding actually carries the payload value rather than the arm merely being accepted | No |
| SOL-TCK-0189, SOL-TCK-0190, SOL-TCK-0191 | REQ-1502 | 12. Enums, Sealed Types, and Exhaustive Match | COMPILE_ERROR (bare rejection; the spec names no code for this rule); stdout 'all2', exit 0; stdout 'wild9', exit 0. LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: A match over a closed variant set must cover every variant unless a wildcard pattern is present, and missing a known variant is a compile-time error. Three arms pin one rule so that 'covered' cannot be read as 'some branch happened to match at runtime': an omitted variant must be rejected, the same program with the variant restored must be accepted, and a wildcard standing in for the missing variant must be accepted because the specification explicitly permits it. Both named diagnostics for this area are unnamed in the specification, so the rejection is bare | No |
| SOL-TCK-0192, SOL-TCK-0193, SOL-TCK-0194 | REQ-1503 | 12. Enums, Sealed Types, and Exhaustive Match | stdout 'first10', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule); COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: Branches are checked in source order and duplicate or unreachable branches are errors, so a branch shadowed by an earlier one is rejected while the same branches ordered specific-first are accepted. The pair is the whole point: a transitive sealed hierarchy is matched with the most specific branch first (accepted, and the specific marker is printed, so first-match-wins is observed rather than assumed) and then with the base branch first (rejected, because the specific branch is unreachable once the base branch covers it). An implementation selecting the last or the most specific matching branch rather than the first would accept the second program and print the other marker in the first. The unreachable-branch diagnostic is not named in the specification, so the rejection is bare | No |
| SOL-TCK-0195, SOL-TCK-0196, SOL-TCK-0197 | REQ-1504 | 12. Enums, Sealed Types, and Exhaustive Match | COMPILE_ERROR (bare rejection; the spec names no code for this rule); stdout 'sealed0', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: A sealed class is abstract and its complete transitive subtype set is closed at compile time, so a match over it must cover every subtype and the sealed class itself is not constructible. Both halves are needed and neither is derivable from the other: a match over a sealed value must cover all direct subtypes (an omitted one is rejected, a wildcard satisfies the requirement), and the sealed class may not be constructed because it is abstract. A sealed hierarchy whose only child is itself open and has further subclasses is covered by REQ-1503, where the base branch legitimately covers the deeper subtype; this requirement is about the direct closed set and about construction | No |
| SOL-TCK-0198 | REQ-1505 | 12. Enums, Sealed Types, and Exhaustive Match | COMPILE_ERROR 'SOLV-SEM-039'. LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: A subclass of a sealed class written in a different physical source file is a compile-time error, because include splices declarations into one program but does not erase the physical file boundary. This is asserted as a genuine two-file fixture, because the rule is ABOUT the physical file boundary and a single-file program cannot test it. The specification's own diagnostic table names SOLV-SEM-039 for exactly this illegal subclass declaration, and section 12 is the section that states the rule, so the code is pinned rather than left bare. A same-file control in the following requirement is what makes the rejection mean 'wrong file' rather than 'subclassing is broken' | No |
| SOL-TCK-0199 | REQ-1506 | 12. Enums, Sealed Types, and Exhaustive Match | stdout 'same4', exit 0. LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: A sealed subclass declared in the same physical file is legal, so the closed-hierarchy rule restricts the file a subclass may appear in rather than forbidding subclassing. The control arm for REQ-1505 and the reason it is a separate requirement: without it, an implementation that rejected all sealed subclassing would satisfy the rejection test and silently make sealed types unusable. The accepted program constructs the subclass and matches on it, so the subclass is shown to be a usable member of the closed hierarchy rather than merely a declaration that parses | No |
| SOL-TCK-0200, SOL-TCK-0201, SOL-TCK-0202 | REQ-1507 | 12. Enums, Sealed Types, and Exhaustive Match | stdout 'both2', exit 0; stdout 'enum1', exit 0; stdout 'under5', exit 0. LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: Initial match patterns are enum variant patterns, sealed-subtype binding patterns of the form name: Type, and wildcard underscore. The three admitted forms are each exercised, with the sealed-subtype binding form observed printing a value reached through the binding rather than a constant, so the binding is shown to carry the matched value. Together the forms are the closed set the specification admits, and no fourth form is asserted absent, because the specification's word 'initial' records a scope boundary rather than a prohibition on future forms | No |
| SOL-TCK-0203, SOL-TCK-0204 | REQ-1508 | 12. Enums, Sealed Types, and Exhaustive Match | stdout 'join1', exit 0; COMPILE_ERROR (bare rejection; the spec names no code for this rule). LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: The result type of a match is the nearest common declared supertype to which every branch result is assignable; if none exists the match is ill-typed. Pinned from both sides of the join rule and deliberately NOT pinned to a diagnostic code: the join produces Number for the Integer and Long case, which is accepted and observed through a declared Number binding, while binding the same construct to Integer is rejected. Following the precedent recorded for SOL-TCK-0135, the specification names SOLV-TYPE-001 for a static declaration initializer that is not assignable to its declared type and not for a local initializer, so the rejection here is bare. The accepted arm is what distinguishes a real least-upper-bound computation from an implementation that rejects every heterogeneous match | No |
| SOL-TCK-0205 | REQ-1509 | 12. Enums, Sealed Types, and Exhaustive Match | stdout 'g2', exit 0. LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: A match branch result is an expression, so a branch may use a brace-delimited block for multiple statements followed by a tail result. Each branch emits a distinct side effect and a distinct tail value, and the expected stream is the side effect followed by the value, which pins both the tail-result rule and branch-local scope at once: both branches declare a binding with the same name, which only compiles if each branch body is its own scope, since a shared scope would make the second declaration a redeclaration error. The expected bytes are derived from section 5's rule that print appends no separator, so the concatenation is computed rather than observed | No |

## Batch: static and strong typing, operators, equality semantics (REQ-1600..REQ-1607)

8 requirements and 31 tests (SOL-TCK-0206..SOL-TCK-0236) from LANGUAGE_SPEC section 3, the
densest normative section in the specification (31 normative markers against 5 prior
requirements). Expectations were written from the quoted text before running the suite; the
one mismatch that produced is described below and is an error in *my* expectation, not in the
implementation.

Code-pinning here was decided per code by checking the specification, not by adopting what the
launcher prints:

* `SOLV-TYPE-039` is named by section 3 itself and is pinned in the identity batch.
* `SOLV-TYPE-001` is named only for a **static** declaration initializer and for an exception
  message argument. The nominal cross-assignment (SOL-TCK-0207) and `Any`-to-`Integer`
  (SOL-TCK-0210) rejections are therefore **bare**, the same reading already applied to
  SOL-TCK-0135 and SOL-TCK-0204 -- applied consistently across three batches rather than
  re-decided each time.
* `SOLV-TYPE-004` and `SOLV-PARS-001` occur ZERO times in the specification, so every
  invalid-operand and parse rejection here is bare.
* `SOLV-RESOL-004` is named, but only as "a member of a `Result` receiver that is not a
  `Result` operation", so an unknown member on an `Any` receiver is bare.
* `SOLV-TYPE-014` is named only as "a bare member read of a `Result` operation", so a bare
  `value.equals` read is bare even though section 3 forbids it.

**The one corrected expectation, and why it mattered.** For `||` short-circuiting I first wrote
the expected stream as `ortruetrue` -- no effect marker at all. That was not a typo but a
substantive oracle bug: the program evaluates `true || f()` (which must short-circuit) and then
`false || f()` (which **cannot** short-circuit, so `f` must run), and an expected string with zero
effect markers is satisfied by an implementation that never evaluates a right operand of `||`
under any condition. The correct stream is `cortruetrue`, and the pair is falsifiable in both
directions: no right-operand evaluation yields `ortruetrue`, eager evaluation yields
`ccortruetrue`, and only genuine conditional short-circuiting yields `cortruetrue`. The
`&&` case (SOL-TCK-0225) is written the same way for the same reason. This is recorded because
the failure was visible only as a *missing* byte, the kind of oracle error that a green suite
hides rather than reveals.

Rules asserted so that a differing implementation fails an arm rather than passing by accident:

* **`??` is pinned as the lowest tier by a rejection.** `a ?? 1 == 2` is ill-typed *only* if
  `??` binds loosest, making an `Integer?` left meet a `Boolean` right; under any tighter
  grouping it typechecks and prints a value. SOL-TCK-0224 is the parenthesized control, so the
  rejection cannot be a blanket refusal of the operator.
* **Nominal typing is pinned with the structural evidence inside the program.** Both classes
  declare member sets that are character-for-character identical and the accepted test reads a
  member from each, so a structural type system has nothing to reject; the rejected test adds
  only the cross-assignment.
* **`Any` does not disable checking** is pinned by a member call that fails on an `Any`
  receiver and succeeds on the same class written as the declared type, plus a checked cast
  that restores it -- so no rejection can be an artifact of the class, the member, or `print`.
* **Assignment is a statement** is refused in four expression positions (nested assignment,
  initializer, controlling condition, call argument), because "not value-producing" quantifies
  over all expression positions and a single position cannot distinguish it from a local
  syntactic restriction.
* **Comparability is pinned with both escape routes the specification itself names** (an
  `Any`-typed operand, an explicit `equals` call) alongside the prohibition, since accepting
  the escapes without the prohibition would be satisfied by untyped equality, and prohibiting
  without them by an over-restrictive checker.
* **Operand order is pinned by running both orderings in one program** (`p() < q()` then
  `q() < p()`), so the marker stream must reverse with the source text; a right-to-left or
  sorted evaluator cannot produce `PQtrueQPfalse`.
* **Single evaluation is pinned separately for `==` and `!=`**, because `!=` negates a
  completed comparison and is the form where a hidden re-evaluation would most plausibly sit.

Two authoring corrections were forced by checking rather than assumption. The first draft
included two concatenation-versus-arithmetic precedence tests; they were deleted, not
relabeled, because that relation is already the single obligation of REQ-0001 and is tested
there by SOL-TCK-0002 (`1 + 2 .. "z"`) -- restating it would give one rule two owners, and the
TCK's per-requirement coverage would then overcount. REQ-1604 as first written also carried
`kind: compile-time` while its primary tests observe runtime evaluation order; it was split
into REQ-1604 (runtime short-circuiting) and REQ-1607 (compile-time Boolean operand checks)
rather than mislabeled, so a profile selecting only compile-time obligations stays sound.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0206, SOL-TCK-0207 | REQ-1600 | 3. Static and Strong Typing | stdout 'nom78', exit 0; COMPILE_ERROR (bare rejection; section 3 names no code for this rule). LANGUAGE_SPEC 3. Static and Strong Typing: Typing is nominal: two unrelated classes declaring identical members are not assignment-compatible, and each is used only through its own type. The accepted program declares two classes whose member sets are character-for-character the same and reads a member from each, so the structural match that a structural type system would accept is present in the program itself; the rejected program adds only the cross-assignment. The rejection is bare because section 3 states the incompatibility without naming a code, and the mismatch code section 7 names is scoped to static declaration initializers (the reading already applied to SOL-TCK-0135 and SOL-TCK-0204) | No |
| SOL-TCK-0208, SOL-TCK-0209, SOL-TCK-0210, SOL-TCK-0211, SOL-TCK-0212 | REQ-1601 | 3. Static and Strong Typing | COMPILE_ERROR (bare rejection; section 3 names no code for this rule); stdout 'own4', exit 0; COMPILE_ERROR (bare rejection; section 3 names no code for this rule); stdout 'cast9', exit 0; COMPILE_ERROR (bare rejection; section 3 names no code for this rule). LANGUAGE_SPEC 3. Static and Strong Typing: Assigning a value to Any does not disable type checking: members and operators are unavailable on an Any receiver and an Any value is not assignable to a narrower type without a checked cast. Three rejections and their controls pin one rule from different angles: a member that exists on the class is unreachable through Any, arithmetic is unavailable, and a narrowing assignment is refused; the controls show the identical member call and value succeed when the declared type is the class itself and after a checked cast, so no rejection can be an artifact of the class, the member, or the operator. All three rejections are bare: the unknown-member code section 8 names is scoped to Result receivers and the mismatch code section 7 names is scoped to static initializers | No |
| SOL-TCK-0213, SOL-TCK-0214, SOL-TCK-0215, SOL-TCK-0216, SOL-TCK-0217 | REQ-1602 | 3. Static and Strong Typing | stdout 'stmt1', exit 0; COMPILE_ERROR (bare rejection; section 3 names no code for this rule); COMPILE_ERROR (bare rejection; section 3 names no code for this rule); COMPILE_ERROR (bare rejection; section 3 names no code for this rule); COMPILE_ERROR (bare rejection; section 3 names no code for this rule). LANGUAGE_SPEC 3. Static and Strong Typing: Assignments are statements, not value-producing expressions, so an assignment is rejected wherever an expression is required while a statement assignment to a mutable local is accepted. Four positions are refused -- nested inside another assignment, as an initializer, as a controlling condition, and as a call argument -- because 'not value-producing' is a statement about every expression position and one position alone cannot distinguish it from a local syntactic restriction. The accepted control assigns as a statement and prints the assigned value. Rejections are bare: the specification names no code for this, and these are parse-level refusals | No |
| SOL-TCK-0218, SOL-TCK-0219, SOL-TCK-0220, SOL-TCK-0221, SOL-TCK-0222, SOL-TCK-0223, SOL-TCK-0224 | REQ-1603 | 3. Static and Strong Typing | stdout 'neg1', exit 0; stdout 'mul26', exit 0; stdout 'ortrue', exit 0; stdout 'notfalse', exit 0; stdout 'istrue', exit 0; COMPILE_ERROR (bare rejection; section 3 names no code for this rule); stdout 'nnfalse', exit 0. LANGUAGE_SPEC 3. Static and Strong Typing: Operator precedence follows the stated tier order, with ?? the lowest tier and arithmetic tighter than the comparison and logical tiers above it. Each tier relation is pinned by a program whose value differs under the neighbouring wrong reading: -2 + 3 yields 1 rather than -5, 2 * 3 + 4 * 5 yields 26 rather than 70, true \|\| false && false is true, !false && false is false. The ?? tier is pinned by a rejection whose ill-typedness exists only if ?? binds loosest -- a ?? (1 == 2) compares an Integer? to a Boolean -- with a parenthesized control proving the operator itself is not being refused. is versus == is pinned because the tighter reading is a parse error rather than a different value, which still distinguishes the two groupings. The relation between concatenation and arithmetic is deliberately absent here: it is already the single obligation of REQ-0001, which SOL-TCK-0002 tests as '1 + 2 .. "z"', and restating it under a second requirement would give one rule two owners | No |
| SOL-TCK-0225, SOL-TCK-0226 | REQ-1604 | 3. Static and Strong Typing | stdout 'candfalsetrue', exit 0; stdout 'cortruetrue', exit 0. LANGUAGE_SPEC 3. Static and Strong Typing: The logical operators short-circuit, so the right operand is not evaluated when the left operand already decides the result. Short-circuiting is pinned inside one program per operator by evaluating the shorting case and then the non-shorting case and printing a single marker afterwards, so the effect marker must occur exactly once in the whole stream. The non-shorting statement is what makes the pair meaningful: the shorting case alone is also satisfied by an implementation that never evaluates a right operand at all, so demanding exactly one occurrence across both cases forces the short-circuit to be conditional rather than permanent. Expected streams are derived from section 5's rule that print appends no separator | No |
| SOL-TCK-0230, SOL-TCK-0231, SOL-TCK-0232 | REQ-1605 | 3. Equality and reference identity | stdout 'PQtrueQPfalse', exit 0; stdout 'hheqtrue', exit 0; stdout 'kknefalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: Every equality or identity expression evaluates its left operand first and its right operand second exactly once each, and the negating forms negate the result without evaluating either operand again. Order is pinned by a program that runs both operand orderings so the marker stream must reverse with them, which a right-to-left or alphabetically-resolved evaluator cannot produce. Single evaluation is pinned separately for == and for !=, since != negates a completed comparison and is the form where a re-evaluation would most plausibly hide; the expected streams are derived from section 5's rule that print appends no separator | No |
| SOL-TCK-0233, SOL-TCK-0234, SOL-TCK-0235, SOL-TCK-0236 | REQ-1606 | 3. Equality and reference identity | COMPILE_ERROR (bare rejection; section 3 names no code for this rule); stdout 'escfalse', exit 0; stdout 'expfalse', exit 0; stdout 'pcptrue', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: Equality is well typed only when one operand type is assignable to the other, so unrelated nominal classes are not directly comparable while an Any-typed operand or an explicit equals call is permitted. The rule and both escape routes the specification itself names are asserted, because accepting the escapes without the prohibition would be satisfied by an untyped equality, and prohibiting the direct comparison without the escapes would be satisfied by an over-restrictive checker. The rejection is bare: section 3 says such operands are 'not directly comparable' and names no code, and the invalid-operand code the implementation prints appears nowhere in the specification | No |
| SOL-TCK-0227, SOL-TCK-0228, SOL-TCK-0229 | REQ-1607 | 3. Static and Strong Typing | COMPILE_ERROR (bare rejection; section 3 names no code for this rule); COMPILE_ERROR (bare rejection; section 3 names no code for this rule); COMPILE_ERROR (bare rejection; section 3 names no code for this rule). LANGUAGE_SPEC 3. Static and Strong Typing: The logical operators require Boolean operands on both sides and unary ! requires a Boolean operand, so a non-Boolean operand on either side of && or \|\| is a compile-time error. The right-operand position is pinned deliberately by 'true \|\| 1', which an implementation that checked only the operand it would actually evaluate might accept because the right operand never runs at runtime; the specification's requirement is static, so the operand must be rejected before evaluation is even considered. Rejections are bare because the invalid-operand code the implementation reports for these cases occurs zero times in the specification | No |

## Batch: reference identity and the semantic equality/hash algorithms (REQ-1700..REQ-1711)

12 requirements and 28 tests (SOL-TCK-0237..SOL-TCK-0264), the second half of LANGUAGE_SPEC
section 3. Expectations were written from the quoted normative text -- including the worked
example section 3 gives for `===`, whose stated outcomes are reproduced directly -- and only
then compared with the implementation.

Section 3 is unusually explicit about identity, listing which static types are identity-bearing,
naming the types that are not, and naming `SOLV-TYPE-039` for "a compatible pair with no
identity-bearing operand", so this batch pins codes far more often than earlier batches: seven
rejections carry `SOLV-TYPE-039`, including `null === null`, which the specification states is a
compile error in prose. The one identity rejection left bare is deliberate and follows the
specification's own routing: "A failure of assignability uses the ordinary invalid-operand
diagnostic" names no code, and the code the implementation prints for that case occurs zero times
in the specification, so SOL-TCK-0249 asserts a bare rejection while SOL-TCK-0248 pins the code
next to it. `SOLV-SEM-037` is likewise named in the specification, but only for `message` and
`getMessage` on an exception class; the reserved-name rule for `equals`/`hashCode` states the same
idea without naming the code, so those rejections (batch 3C) are asserted bare rather than
borrowing a code from a different rule.

**A second self-correction, again failing in the dangerous direction.** For the
no-identity-shortcut test I wrote the expected stream as `scfalsefalse`, omitting the override's
effect markers, on the reasoning that the result value was what mattered. The correct stream is
`uuscfalsefalse`: the override prints, and it runs once for `p == p` -- which is the entire point
of the test -- and once for `p.equals(p)`. An expectation with no effect markers would be satisfied
by an implementation carrying the conventional same-reference shortcut, because such an
implementation skips precisely the dispatch that produces the marker; the value `false` would then
come from the shortcut's own alias answer rather than from user code. The corrected oracle is
falsifiable: a shortcut implementation emits `usctruefalse`.

Because several oracles here depend on when an operand's side effect reaches stdout relative to
the enclosing `print`, the ordering was not assumed. An explicit model of evaluation-versus-print
interleaving was written and checked against the *observed* stream (`uuscfalsefalse`) before the
model was trusted; only then was the same model used to predict what a differing implementation
would emit. That is what makes the alternative named above a derived prediction rather than a
plausible-sounding guess.

Rules asserted so a differing implementation fails an arm rather than agreeing by accident:

* **The null steps of the equality algorithm** use a class whose `equals` override prints and
  returns `true`. `p == null` must be `false` with no output at all. The absent marker is what
  makes this bite: a class whose `equals` returned `false` could not distinguish "the null rule
  applied, no user code ran" from "user code ran and said no", which is exactly the clause under
  test. SOL-TCK-0257 adds the mirrored operand order.
* **No identity shortcut** is pinned by an override returning `false` compared against `p == p`,
  where the conventional optimisation is precisely what would report `true`, with the explicit
  `equals` call printed alongside so a shortcut applied to only one of the two forms is caught.
* **One-directional dispatch** is pinned by giving *both* operand classes a printing override that
  returns `false`. A fall-through to the right operand would add the second marker to the stream.
* **The identity-bearing set** is pinned by rejecting five different non-identity pairs with the
  same code -- numeric, String, Boolean, enum, Regex -- so the rejection cannot be a per-type
  special case, and by accepting `===` on a collection and on an interface-typed value, the two
  positive cases an implementation recognising only plain user classes would omit.
* **`Any` is pinned on both sides of narrowing**: `q === r` on two `Any` values is rejected with
  the named code, and the same identity test on the same runtime value after a checked cast is
  accepted, so the rejection is about `Any` and not about the class behind it.
* **The hash/equality invariant** is pinned where it is actually at risk rather than trivially.
  `0.0 == -0.0` holds under IEEE equality, so a hash that kept boxed Java's separation would give
  equal values unequal hashes -- the exact hazard the specification calls out -- and the String
  arm compares two separately constructed values so it is not one value hashed against itself.
* **Collection equality** is pinned with a same-content control pair, so the `true` arm cannot come
  from a content-based implementation and the `false` arm cannot come from a broken alias test.
* **Enum equality** is pinned across all three conditions the specification lists (same variant and
  equal payloads true, differing payload false, differing variant false) within one enum that has
  both a payload-bearing and a payload-free variant, so the third arm is not a cross-enum compare.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0237, SOL-TCK-0238, SOL-TCK-0239 | REQ-1700 | 3. Equality and reference identity | stdout 'ifalsetruefalse', exit 0; stdout 'ntruefalsefalsetrue', exit 0; stdout 'idfalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: '===' answers whether two values are the same allocation and never invokes 'equals', another guest method, or Java 'Object.equals', and '!==' is its exact logical negation. Expected values are taken from the worked example the specification itself gives for exactly this rule: two separately constructed equal-shaped instances are '===' false, an alias is '===' true, and '!==' against that alias is false. The negation is asserted over both a non-null and a null pair so that '!==' is shown to be the negation of '===' rather than an independently implemented test. A separate test pairs a class whose 'equals' returns true with '===', which stays false, so the guarantee that identity does not delegate to equality is observed and not merely asserted by the shape of the program | No |
| SOL-TCK-0240, SOL-TCK-0241, SOL-TCK-0242, SOL-TCK-0243, SOL-TCK-0244, SOL-TCK-0245, SOL-TCK-0246, SOL-TCK-0247 | REQ-1701 | 3. Equality and reference identity | stdout 'clsfalse', exit 0; stdout 'colfalsetrue', exit 0; stdout 'iffalsetrue', exit 0; COMPILE_ERROR 'SOLV-TYPE-039'; COMPILE_ERROR 'SOLV-TYPE-039'; COMPILE_ERROR 'SOLV-TYPE-039'; COMPILE_ERROR 'SOLV-TYPE-039'; COMPILE_ERROR 'SOLV-TYPE-039'. LANGUAGE_SPEC 3. Equality and reference identity: The identity-bearing static types are exactly user class types, interface types, the four collection types, and nullable forms of those, so '===' on a scalar, enum, or Regex pair is a compile-time error reported with SOLV-TYPE-039. Five different non-identity operand pairs are rejected with the one code section 3 names for a compatible pair with no identity-bearing operand, covering the two named scalar cases a real implementation is most likely to allow through (a numeric scalar and a String) plus a Boolean, an enum value and a Regex, so the rejection cannot be a per-type special case. The accepted arms cover the two positive cases most likely to be omitted by an implementation that only recognises plain user classes, namely a collection and an interface-typed value. Section 3 is unusually explicit here, listing the bearing and non-bearing types and naming the code, so the codes are pinned rather than left bare | No |
| SOL-TCK-0248, SOL-TCK-0249, SOL-TCK-0250 | REQ-1702 | 3. Equality and reference identity | COMPILE_ERROR 'SOLV-TYPE-039'; COMPILE_ERROR (bare rejection; section 3 names no code for this rule); stdout 'okfalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: Identity operands must also satisfy ordinary comparability and a null literal is permitted only against a nullable identity-bearing operand, so 'null === null' and an identity test against a non-nullable operand are compile-time errors. Two distinct failures are separated because the specification routes them to different diagnostics: 'null === null' is a compatible pair with no identity-bearing operand and is pinned to the code section 3 names, while an identity test between a non-nullable class value and a null literal is an assignability failure routed to the ordinary invalid-operand diagnostic, which the specification does not name, so it is asserted bare. The accepted control performs the same test with the operand declared nullable, proving the rejection is about nullability and not about '===' | No |
| SOL-TCK-0251, SOL-TCK-0252 | REQ-1703 | 3. Equality and reference identity | stdout 'ne3', exit 0; stdout 'el4', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: Stable nullable identity tests participate in flow analysis, so 'x !== null' narrows on the true path and 'x === null' narrows on the false path under the same write-invalidation rules as the equality null tests. Both directions are asserted as accepted programs that read a member of the narrowed value inside the branch, because narrowing is only observable by a member access the declaration type would not permit. The '=== null' case places the member read in the else branch, so an implementation that narrowed on the wrong path would reject the program | No |
| SOL-TCK-0253, SOL-TCK-0254 | REQ-1704 | 3. Equality and reference identity | COMPILE_ERROR 'SOLV-TYPE-039'; stdout 'nwtrue', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: A value held in Any must first be narrowed or checked-cast to an identity-bearing type before an identity test, so identity is rejected on Any operands and accepted after a checked cast. The rejection is pinned to the code section 3 names, and the accepted arm is the same identity test on the identical runtime value after casting to the class type. Without the accepted arm the rejection would be satisfied by an implementation that rejects every identity test. The specification states the reason for the restriction, that a JVM representation choice must not become observable, which is why Any rather than the underlying class is the operand under test | No |
| SOL-TCK-0255, SOL-TCK-0256, SOL-TCK-0257 | REQ-1705 | 3. Equality and reference identity | stdout 'onefalse', exit 0; stdout 'bothtrue', exit 0; stdout 'revfalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: The semantic equality algorithm returns true when both values are null and false when exactly one is null with no user code running. Both arms use a class whose 'equals' override prints and returns true. 'p == null' must yield false with no print at all, and two nulls must yield true with no print. The print is what makes the test bite: an implementation that dispatches the left operand before applying the null rule produces the wrong value AND an extra marker, and a class whose equals returned false would be unable to tell those two causes apart | No |
| SOL-TCK-0258 | REQ-1706 | 3. Equality and reference identity | stdout 'uuscfalsefalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: An equals override is invoked even when both operands are the same reference because there is no general identity shortcut before user dispatch, which keeps '==' and an explicit equals call behaviorally aligned. The override returns false and the comparison is 'p == p', so the conventional same-reference optimisation is exactly what would make this report true; the specification states the reason for forbidding it, that '==' and an explicit call stay aligned even for an override with side effects, so the same program prints both forms and the two values must agree | No |
| SOL-TCK-0259 | REQ-1707 | 3. Equality and reference identity | stdout 'Lfalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: The left operand is the dynamic receiver of semantic equality and the right operand never receives a fallback equality call. Both operand classes override 'equals' and print a distinct marker, and both return false. If equality fell through to the right operand when the left reports inequality, the stream would contain the second marker as well as the first, so the expected value witnesses the one-directional dispatch rather than merely the result | No |
| SOL-TCK-0260 | REQ-1708 | 3. Equality and reference identity | stdout 'dffalsetruefalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: When no class in a user class hierarchy overrides equals, the root default is reference identity. Two instances constructed with identical constructor arguments must compare false while an alias of one compares true, which is only reachable through reference identity: a content-based default, or a default that compared constructor arguments, would make the first arm true. The same program therefore contains the positive and negative arms of the default rule | No |
| SOL-TCK-0261, SOL-TCK-0262 | REQ-1709 | 3. Equality and reference identity | stdout 'fztruetrue', exit 0; stdout 'shtruetrue', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: The equals/hashCode invariant holds for the fixed built-in rules, including that floating equality is IEEE so negative zero must fold onto zero in the hash. The invariant is pinned where it is genuinely at risk rather than trivially. '0.0 == -0.0' is true under IEEE equality, so a hash that distinguished negative zero would produce unequal hashes for equal values, which is the exact hazard the specification calls out about boxed Java hashing. String content equality is paired with content hashing in the same way, and both arms compare two separately constructed values rather than one value against itself | No |
| SOL-TCK-0263 | REQ-1710 | 3. Equality and reference identity | stdout 'cefalsetruetrue', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: List, Set, Map and Stack compare by reference identity under both equality and hashing, not by content. Two separately constructed collections with identical elements must compare false while an alias compares true, so the 'true' arm cannot be produced by a content-based implementation and the 'false' arm cannot be produced by a broken alias check. The rule is stated twice in the specification, in the equality table and again in the hash table, which is why both are cited | No |
| SOL-TCK-0264 | REQ-1711 | 3. Equality and reference identity | stdout 'entruefalsefalse', exit 0. LANGUAGE_SPEC 3. Equality and reference identity: Two enum values are equal exactly when they share the enum type, the variant, and semantically equal payloads, and enum equality never delegates to Java array or object equality. Three arms distinguish the three conditions the specification lists: same variant and equal payloads is true, same variant with a differing payload is false, and a differing variant is false. The payload-bearing and payload-free variants of one enum are used so that the third arm cannot be dismissed as comparing values of unrelated enums | No |

## Batch: the universal equals/hashCode members (REQ-1800..REQ-1805)

6 requirements and 21 tests (SOL-TCK-0265..SOL-TCK-0285), completing LANGUAGE_SPEC section 3.
Expectations were derived from the quoted text before the suite was run.

**The one place in the whole corpus that pins a byte offset, and why it is legitimate.** The
oracle matches a diagnostic by finding *any* reported diagnostic satisfying the declared fields,
so a bare `{}` accepts any diagnostic anywhere, and the schema has no way to count diagnostics.
Section 3 states a location and a count as fact: "Each violation is a compile-time error reported
on the single unpaired member, so a class missing one of the two produces **one** diagnostic."
Naming the unpaired member's exact span in a program built so that nothing else can produce a
diagnostic is the only way to express that claim. The span was computed from the program text as
the member declaration *without* its leading indentation, on the reading that the specification's
phrase supports -- the member is reported on, not the whitespace before it. That derivation was
written before checking the launcher and then confirmed by it (246..317 on both sides); the
cross-check was worth having, because an earlier probe of a *near-identical* program had produced
214..285, which matches nothing in the final corpus file and would have poisoned the oracle had it
been copied across. The generator also asserts structurally that the violating class contains
exactly one candidate member, so "the single unpaired member" is a checked property of the program
rather than an assumption about it.

`SOLV-SEM-044`/`SOLV-SEM-045` are named in the specification's diagnostic table with descriptions
matching these rules and are pinned. Everything else in this batch is bare, and two of those are
deliberate refusals to borrow a neighbouring code: `SOLV-SEM-037` is named only for declaring
`message`/`getMessage` on a guest exception class, and `SOLV-TYPE-014` only for a bare member read
of a `Result` operation -- section 3 states both the reserved-name rule and the bare-read rule for
`equals`/`hashCode` in prose without naming a code, so pinning here would encode an implementation's
choice to share one diagnostic across two different specification rules. `SOLV-TYPE-024`,
`SOLV-SEM-011`, `SOLV-SEM-013` and `SOLV-SEM-014` occur zero times in the specification.

Rules asserted so a differing implementation fails an arm rather than agreeing by accident:

* **The declaration shapes are attacked from four and three directions** for `equals` and
  `hashCode` -- missing `override`, a parameter typed `Any` rather than `Any?`, an extra parameter,
  a `Boolean?` return, a parameter on `hashCode`, a `Long` return -- so an implementation doing
  only a name match, or only an arity check, fails at least one arm. The violation programs declare
  the *other* member correctly, which is what keeps a single diagnostic attributable.
* **The pairing rule is pinned against its own opposite**: a subclass of a class overriding both
  members must be accepted with no override of its own. That arm is what stops the rejection from
  being satisfied by an implementation hostile to subclass overrides, and it is precisely the arm an
  implementation enforcing pairing by inheritance rather than per declaration would reject.
* **Reserved names** are refused for all three declaration kinds the specification names --
  property, delegate, and interface member -- since those are stated as separate clauses.
* **Nullable receivers** are pinned on both sides, with the accepted results bound to declarations
  of exactly `Boolean?` and `Integer?` so the result *type* is checked by the type system rather
  than only rendered, and with a null-receiver arm showing the call is genuinely skipped.
* **Bare member reads** are refused for all three universal members, which is what "exactly like a
  bare `value.toString` read" makes testable.

**A third corrected expectation, this one failing the safe way.** For the null-receiver arm I first
wrote `q == null` as false. The correct value follows from two quoted rules rather than from
observation: the safe call is skipped on a null receiver so `q` is null, and "if both values are
`null`, the result is `true`". The error is recorded because of which way it failed: an expectation
that fails a correct implementation is caught immediately by a red test, whereas the two earlier
batch errors -- short-circuit and identity-shortcut oracles missing their effect markers -- would
have *passed* an incorrect implementation silently. The three together are the argument for
deriving every expectation in writing before running anything.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0265, SOL-TCK-0266, SOL-TCK-0267, SOL-TCK-0268, SOL-TCK-0269 | REQ-1800 | 3. Equality and reference identity | stdout 'shapetrue', exit 0; COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule). LANGUAGE_SPEC 3. Equality and reference identity: A user class may declare equals only as exactly 'override func equals(other: Any?): Boolean', so the override keyword, one parameter typed exactly Any?, and a return type of exactly Boolean are each required. Four rejections attack the declaration from four directions -- no override keyword, a parameter typed Any rather than Any?, an extra parameter, and a Boolean? return -- so an implementation performing only a name match, or only an arity check, fails at least one arm; each is paired with the accepted program that declares the shape exactly. All four rejections are bare because section 3 states the requirement without naming a code and the override-conformance codes it reports occur zero times in the specification | No |
| SOL-TCK-0270, SOL-TCK-0271, SOL-TCK-0272, SOL-TCK-0273 | REQ-1801 | 3. Equality and reference identity | stdout 'hash7', exit 0; COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule). LANGUAGE_SPEC 3. Equality and reference identity: A user class may declare hashCode only as exactly 'override func hashCode(): Integer', requiring the override keyword, no parameters, and a return type of exactly Integer. The same attack pattern as the equals shape, from the parameter and return directions plus the missing override keyword, with the accepted exact-shape control shared with the equals arms. Bare rejections for the same reason: the codes reported for these cases appear nowhere in the specification | No |
| SOL-TCK-0274, SOL-TCK-0275 | REQ-1802 | 3. Equality and reference identity | stdout 'inhtrue5', exit 0; COMPILE_ERROR (code SOLV-SEM-045; family SEM; span [246,317)). LANGUAGE_SPEC 3. Equality and reference identity: The equals/hashCode pairing is checked per class declaration and is never satisfied by inheritance, while a subclass of a class overriding both members needs no override of its own. The rule is pinned against its own opposite. A subclass whose parent overrides both members and which declares nothing of its own must be accepted, which is the arm an implementation that enforced pairing by looking at inherited members would reject, and it is also the arm that stops the rejection from being satisfied by any implementation hostile to subclass overrides. The violation arm is a subclass adding only equals over a parent declaring both as open, and because these two requirements are simultaneously true the program carries exactly one diagnostic, so the manifest pins the code and the exact span of the unpaired member rather than a bare expectation -- the alternative of accepting any single diagnostic would not distinguish 'reported on the single unpaired member' from 'reported somewhere' | No |
| SOL-TCK-0276, SOL-TCK-0277, SOL-TCK-0278 | REQ-1803 | 3. Equality and reference identity | COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule). LANGUAGE_SPEC 3. Equality and reference identity: The names equals and hashCode are reserved as members, so a property or delegate may not use either name and an interface may not redeclare either. All three declaration kinds the specification names are refused -- a property, a delegate, and an interface member -- because the property/delegate prohibition and the interface prohibition are stated as separate clauses and an implementation might enforce only one. Rejections are bare: the specification names 'SOLV-SEM-037' only for declaring 'message' or 'getMessage' on a guest exception class, and borrowing that code here would encode an implementation's choice to share one diagnostic across two different specification rules | No |
| SOL-TCK-0279, SOL-TCK-0280, SOL-TCK-0281, SOL-TCK-0282 | REQ-1804 | 3. Equality and reference identity | stdout 'eqtrue', exit 0; stdout 'hctruetrue', exit 0; COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule). LANGUAGE_SPEC 3. Equality and reference identity: A call on a possibly-null receiver must use the safe-call form, which yields a nullable result, so 'value?.equals(other)' is safe with result Boolean?, 'value?.hashCode()' yields Integer?, and a direct member call on a possibly-null receiver is an error. The accepted arms bind the safe-call result to a declaration of exactly the nullable type the specification names, so the result type is checked by the type system rather than only rendered, which a print alone would not establish. The rejected arms use the same possibly-null receiver with a direct member access, differing from the accepted arms in only the call operator, so the pair constrains the receiver rule and nothing else | No |
| SOL-TCK-0283, SOL-TCK-0284, SOL-TCK-0285 | REQ-1805 | 3. Equality and reference identity | COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule); COMPILE_ERROR (bare rejection -- section 3 names no code for this rule). LANGUAGE_SPEC 3. Equality and reference identity: A bare member read of equals or hashCode without a call is invalid, exactly like a bare toString read. All three universal members are refused in the same position, which is what the phrase 'exactly like' makes observable: an implementation that treated the three members alike in the call path but not in the member-read path would accept one of them. Rejections are bare because the code reported for a bare member read is named in the specification only for Result operations | No |

## Batch: lexical semicolon insertion (REQ-1900..REQ-1907)

8 requirements and 23 tests (SOL-TCK-0286..SOL-TCK-0308) for LANGUAGE_SPEC section 16, the most
rule-dense section in the specification and, before this batch, the least covered: it states three
numbered conditions under which a synthetic `SEMI` is emitted, an end-of-file variant, a
no-duplicate-blank-lines clause, a comment-newline clause, a `return`-termination rule, the
member-chain lookahead exceptions, and the prohibition on JavaScript-style heuristics. Before this
batch a single requirement (REQ-0403) touched the section. Expectations were derived from the
quoted insertion conditions before the suite was run.

**No code is pinned anywhere in this batch, and that is forced rather than lazy.** Section 16 names
no diagnostic at all, and the codes the implementation prints for these cases (`SOLV-PARS-001`,
`SOLV-TYPE-010`, `SOLV-SEM-003`) either occur zero times in the specification or are named only for
unrelated rules, so every rejection here asserts a compile-time error and nothing about its code.
Inventing or borrowing a code would transcribe an implementation's choice into the conformance
suite, which is what the oracle-independence rule forbids.

The whole difficulty of a semicolon-insertion suite is that the rule is about a token stream, which
a portable oracle cannot inspect -- only whether a program with a given newline placement parses is
observable. So every rule is isolated by a program pair differing in exactly the property the rule
turns on:

* **The paren-depth condition** is isolated by `f(1\n + 2)` (accepted) against `val v = 1\n + 2`
  (rejected). The tokens around the newline are identical in the two -- a literal before it, `+`
  after it -- and the only difference is the unmatched-paren depth at the newline. That single
  difference is the entire condition, so an implementation emitting `SEMI` on the preceding-token
  rule while ignoring depth rejects the accepted arm.
* **The preceding-token condition** is isolated by `val b = a +\n 1` (accepted, the token before
  the newline is an operator, not a triggering token) against `val b = a\n + 1` (rejected, the
  token before is the identifier `a`, a triggering token, with a non-exception token after). A
  second rejection uses `*` rather than `+` so the rule is shown not to hinge on one operator;
  together these are the only direct evidence for "do not use general JavaScript-style
  heuristics", because an implementation that carried over JS operator-continuation accepts both
  leading-operator programs.
* **The `.`, `?.`, `else` lookahead exceptions** are each accepted only because insertion is
  suppressed for that one next token; drop any suppression and that program gains a stray `SEMI`.
  The dot and safe-call chains are separate programs because the specification lists two
  exceptions, and `else` is exercised both as a statement and as an if-expression tail.
* **Comment newlines are physical.** `val a = 1 /* c\n*/ print(...)` is accepted where the same
  program with the comment-newline removed (`val a = 1 print(...)`, the REQ-1900 rejection) is
  rejected; the two differ only by a newline living inside a comment, so the accepted arm can only
  parse if the token-stream stage treats a newline contained in a comment as a physical newline.
  This is the clause's sole evidence, since no oracle can see the stage.
* **`return` followed by a newline terminates the return.** The strongest arm is a value-less
  function with `return` then `print("after")` on the next line: the expected bytes are `indone`
  with NO `after`, because the return left the function at the newline. A no-ASI reading that
  swallowed the print as the return value emits an extra `after`, so this arm fails in the
  direction that matters -- it is satisfied only by an implementation that terminated the return,
  and it is the literal embodiment of "do not copy JavaScript's automatic semicolon insertion".
* **Blank lines and end of file.** Consecutive blank lines between declarations are accepted
  (a `SEMI` per newline would leave empty statements a parser rejects), and a final statement with
  no trailing newline is accepted, exercising the "at end of file, apply the same rule without a
  next-token exception" clause. The generator asserts structurally that the end-of-file arm really
  ends without a newline and that the comment arm's only newline really is inside the comment, so
  neither can rot into a program that no longer tests its clause.
* **`include` termination.** Newline-terminated and raw-string-path includes are accepted, each
  observable by calling through the included helper; an include with no separator is rejected. The
  section states explicitly that no include-specific termination rule exists, so the rejection is
  an ordinary insertion failure asserted bare like the others.

Two construction errors, both caught by guards rather than by the implementation, and worth
recording because neither would have been visible in a green run:

* The include tests failed with `SOLV-RESOL-008` (include not found) even though every program ran
  correctly when launched directly. The cause was mine: the isolation layer stages only a
  manifest's declared `fixtureRoot`, or the bare entry file when it is absent, so `lib/m.sol` was
  never copied into the workspace and the include genuinely could not be found. The fix is the
  `fixtureRoot: "."` field the 14 existing include/module tests already carry. Had I "fixed" the
  test by loosening its expectation to accept a RESOL-008 rejection, I would have turned a harness
  bug into an oracle that certifies the absence of the file it is supposed to include.
* The one-obligation guard rejected two pairs for sharing an exact `stdout` oracle across
  different programs (`a1` for the block- and line-comment arms, `v2` for the newline- and
  raw-string include arms). That is a real hazard rather than a technicality: two distinct programs
  forced to the same expected byte string means at least one expectation was not independently
  derived from its own rule. Each arm now carries a prefix naming the clause it tests, so each
  oracle is a function of its own program and rule.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0286, SOL-TCK-0287, SOL-TCK-0288 | REQ-1900 | 16. Statement Termination | stdout 'n12', exit 0; stdout 'x12', exit 0; COMPILE_ERROR (bare rejection -- section 16 names no code). LANGUAGE_SPEC 16. Statement Termination: A top-level statement must end with an explicit or inserted SEMI: newline-separated and explicitly-semicolon-terminated declarations are both accepted, and a statement with no terminator at all is a compile-time error. REQ-0403 already owns the equivalence of the two terminators (an explicit ';' and an inserted one introduce bindings the same way). This requirement owns the distinct necessity obligation the specification's insertion conditions force: with no physical newline and no explicit ';', no SEMI is emitted and the following token is orphaned, so the program is a compile-time error. The two accepted arms are the controls that keep the rejection from being satisfied by an implementation that rejects every multi-statement program. Bare rejection: section 16 names no diagnostic code, and the parse code the implementation prints occurs zero times in the specification | No |
| SOL-TCK-0289, SOL-TCK-0290 | REQ-1901 | 16. Statement Termination | stdout 'v3', exit 0; COMPILE_ERROR (bare rejection -- section 16 names no code). LANGUAGE_SPEC 16. Statement Termination: Semicolon insertion is suppressed while the unmatched '(' and '[' nesting depth is nonzero, so a newline inside an unclosed parenthesis does not terminate the expression. The pair 'f(1\n + 2)' (accepted) and 'val v = 1\n + 2' (rejected) has identical tokens around the newline -- a literal before it, '+' after it, at the start of a declaration -- and differs only in the unmatched-paren depth at the newline. That isolates condition 1 exactly: an implementation that emitted a SEMI on the preceding-token rule while ignoring depth would reject the accepted arm, and one that never inserted after a literal would accept the rejected arm. Bare rejection | No |
| SOL-TCK-0291, SOL-TCK-0292, SOL-TCK-0293, SOL-TCK-0294 | REQ-1902 | 16. Statement Termination | stdout 'b2', exit 0; stdout 'r3', exit 0; COMPILE_ERROR (bare rejection -- section 16 names no code); COMPILE_ERROR (bare rejection -- section 16 names no code). LANGUAGE_SPEC 16. Statement Termination: Insertion happens only when the token before the newline is a terminator-triggering token, so an expression continues after a trailing operator or comma, while a newline before an operator (identifier before it, operator after) inserts a SEMI and is a compile-time error. 'val b = a +\n 1' (accepted) and 'val b = a\n + 1' (rejected) are both at depth zero and differ only in which side of the newline the '+' sits: on the accepted side the preceding token is an operator (not a triggering token) so no SEMI is emitted; on the rejected side it is the identifier 'a' so a SEMI is emitted and '+ 1' is orphaned. A second rejection uses '*' rather than '+' to show the rule is not about any one operator. Together these are the direct evidence for 'do not use general JavaScript-style heuristics': an implementation that carried over JS operator-continuation would accept both leading-operator programs. Bare rejections | No |
| SOL-TCK-0295, SOL-TCK-0296, SOL-TCK-0297, SOL-TCK-0298 | REQ-1903 | 16. Statement Termination | stdout 'r42', exit 0; stdout 'vtrue', exit 0; stdout 'n', exit 0; stdout 'r1', exit 0. LANGUAGE_SPEC 16. Statement Termination: Insertion is suppressed when the token after the newline is '.', '?.', or 'else' -- the only member-chain lookahead exceptions -- so a leading-dot chain, a leading-safe-call chain, and an 'else' on its own line all parse. Each accepted program is accepted ONLY because insertion is suppressed for one specific next-token; removing the suppression for '.', for '?.', or for 'else' respectively makes that program gain a stray SEMI and fail. The dot and safe-call chains are separate programs because the specification lists them as two exceptions, and the else is tested both as a statement and as the tail of an if-expression. Bare expectations are not needed here: every arm is an acceptance whose stdout is the oracle | No |
| SOL-TCK-0299, SOL-TCK-0300 | REQ-1904 | 16. Statement Termination | stdout 'bc1', exit 0; stdout 'lc1', exit 0. LANGUAGE_SPEC 16. Statement Termination: A newline contained in a line comment or a block comment is treated as a physical newline, so a statement can be terminated by a newline that appears only inside a comment. 'val a = 1 /* c\n*/ print(...)' is accepted while the identical program with the comment-newline removed, 'val a = 1 print(...)', is rejected (that rejection is REQ-1900's arm and the contrast is documented in the plan). The accepted arm can only parse if the token-stream stage treats the newline inside the block comment as the physical newline that terminates 'val a = 1'; a stage that ignored comment interiors would see no terminator and reject it. A line-comment newline is accepted on the same principle. This is the only direct evidence for the clause, since a portable oracle cannot inspect the token stream | No |
| SOL-TCK-0301, SOL-TCK-0302, SOL-TCK-0303 | REQ-1905 | 16. Statement Termination | COMPILE_ERROR (bare rejection -- section 16 names no code); stdout 'v7', exit 0; stdout 'indone', exit 0. LANGUAGE_SPEC 16. Statement Termination: 'return' followed by a newline terminates the return statement, so a value placed on the next line is not the returned value and a bare return leaves the function before any following statement. The rejection is a value-returning function whose value sits on the line after 'return', so the return is terminated empty and the function returns no value -- the specification's own worked example. The control 'return 7' on one line is accepted. The strongest arm is a value-less function with 'return' then 'print("after")' on the next line: the observed bytes are 'in' then 'done' with NO 'after', which proves the return terminated the function at the newline (a no-ASI reading would swallow the print as the return value and emit 'after'). That expected output is the direct embodiment of 'do not copy JavaScript's automatic semicolon insertion behavior', and it fails in the safe direction -- an implementation that did copy JS would be caught by the extra 'after'. Bare rejection | No |
| SOL-TCK-0304, SOL-TCK-0305 | REQ-1906 | 16. Statement Termination | stdout 'z12', exit 0; stdout 'e12', exit 0. LANGUAGE_SPEC 16. Statement Termination: Consecutive blank lines between statements must not emit duplicate semicolons, and at end of file the same insertion rule applies without the next-token exception. The blank-lines program has two consecutive blank lines between two declarations and a trailing blank line before the final statement; if a SEMI were emitted per newline the parser would see empty statements it rejects, so acceptance is the evidence for the no-duplicate clause. The end-of-file program ends after a call with no trailing newline at all, exercising the EOF rule (the final statement is terminated by end of file, not by a following token). Both are accepted and the observable is the printed bytes, so no code assertion is needed | No |
| SOL-TCK-0306, SOL-TCK-0307, SOL-TCK-0308 | REQ-1907 | 16. Statement Termination | stdout 'nl2', exit 0; stdout 'rw2', exit 0; COMPILE_ERROR (bare rejection -- section 16 names no code). LANGUAGE_SPEC 16. Statement Termination: A top-level include directive ends with an explicit or inserted SEMI exactly like a statement -- its path is a string or raw-string literal so a following physical newline terminates it -- and an include with no separator is a compile-time error. Two accepted arms terminate an include by a plain newline, one with a normal string path and one with a raw-string path, each observable by calling a function declared in the included file -- if the directive were not terminated, the following call statement could not parse. The rejected arm omits any terminator so the include and the next statement collide. The section states explicitly that no include-specific termination rule exists, so the rejection is an ordinary insertion failure and is asserted bare like the other termination rejections; the included helper is the same for every arm so only the terminator differs | No |

## Batch: `?` propagation (REQ-2000..REQ-2006)

7 requirements and 13 tests (SOL-TCK-0309..SOL-TCK-0321) for LANGUAGE_SPEC section 23.3, the postfix
propagation operator. Earlier batches covered `Result` must-consume, wrong-variant faults, the
program boundary, and result-type joining, but left propagation entirely unrepresented even though
TCK.md section 11 item 13 requires "propagation typing, early return, and interaction with
`finally`". Every expectation was derived from the quoted section 23.3 text before the suite ran.

**The semicolon interaction is avoided, not decided.** Section 16's condition-2 terminator list
does not include the postfix `?`, so whether a synthetic `SEMI` is emitted after a `?` at a line
boundary is not settled by that list -- even though section 23.3's own example
(`val config = readConfig()?`) reads as if one is. Every program here writes a token after the `?`
on the same line (a `;`, or a continuing `+`), so the outcomes depend only on propagation semantics.
The inconsistency is a specification question to resolve; it is not an observable the TCK may
choose, so no test pins either reading.

**The three specification-named diagnostics are asserted exactly.** Unlike sections 1 and 16, section
23.3 names `SOLV-SEM-049`, `SOLV-SEM-050`, and `SOLV-SEM-051` verbatim in its required-diagnostics
table, so the negative oracles pin the code rather than the family. Each negative program keeps a
genuine `Result` operand or a valid boundary on one side, so a rejection isolates the one violated
clause and cannot be satisfied by an implementation that rejects `Result` programs wholesale.

The discriminating construction is the one TCK.md section 10 asks for -- a negative program with a
sentinel that would be observable if it executed, and an accepted sibling that differs only in the
tested rule:

* **The success payload is a value of `T`.** `get(ok)? + 1` uses the unwrapped `Integer` as an
  arithmetic operand and prints `42`; an implementation yielding `Unit`, the whole `Result`, or the
  wrong variant cannot produce those bytes.
* **Exactly once.** A `print("p")` side effect inside the operand function appears once on both the
  `Ok` and `Err` arms; a double evaluation would print `p` twice.
* **`Err` is a return, not a fault.** The caller observes the same `Err` through `isErr`/`unwrapErr`
  and the process exits 0 with SUCCESS, where `unwrap` on an `Err` is the RUNTIME_ERROR covered by
  REQ-0206. Statements after the `?` do not run (the `AFTER` marker is absent), and the return
  composes through two call frames.
* **Not a guest throw.** A `catch (e: Exception)` -- the broadest handler -- surrounds the
  propagation, and its `CAUGHT` marker is absent from the expected bytes. Had `?` been implemented
  as a guest throw the handler would run.
* **`finally` still runs.** The `finally` block prints `fin` on the propagation exit path, before
  the caller sees the `Err`, so the expected bytes are `finerr=true e=x`.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0309, SOL-TCK-0310, SOL-TCK-0311 | REQ-2000 | 23.3 Propagation | stdout 'ok=true v=42', exit 0; stdout 'pv=7', exit 0; stdout 'perr=true', exit 0. LANGUAGE_SPEC 23.3 Propagation: The postfix operator 'expression?' is the propagation form for 'Result' values. Its operand must have a 'Result<T, E>' type; the value of the expression is the unwrapped success payload of type 'T', and the operand is evaluated exactly once. The payload is used as an arithmetic operand of its own type T, and a side-effecting probe observes single evaluation on both the Ok and Err arms. The '?' is followed on the same line by '+ 1' or ';' so no oracle depends on the unsettled semicolon-insertion question | No |
| SOL-TCK-0312, SOL-TCK-0313, SOL-TCK-0314 | REQ-2001 | 23.3 Propagation | stdout 'err=true e=bad', exit 0; stdout 'err=true', exit 0; stdout 'err=true e=deep', exit 0. LANGUAGE_SPEC 23.3 Propagation: On 'Ok(value)' the operand's success payload becomes the value of 'expression?'. On 'Err(error)' the current function returns 'Err(error)' immediately, without evaluating the rest of its body. The caller sees the same Err (SUCCESS, not the wrong-variant fault of REQ-0206), a marker after the '?' is absent, and the return composes through two frames | No |
| SOL-TCK-0315 | REQ-2002 | 23.3 Propagation | COMPILE_ERROR 'SOLV-SEM-049'. LANGUAGE_SPEC 23.3 Propagation: the operand must have a 'Result<T, E>' type, and the required-diagnostics table names 'SOLV-SEM-049' as the stable code for 'the ? operand is not a Result<T, E>'. An Integer literal under '?' inside an otherwise well-formed Result-returning function isolates the operand rule; the code is specification-named rather than adopted from the implementation | No |
| SOL-TCK-0316 | REQ-2003 | 23.3 Propagation | COMPILE_ERROR 'SOLV-SEM-050'. LANGUAGE_SPEC 23.3 Propagation: the operation is permitted only inside a function declared to return a 'Result<T2, E2>', and the required-diagnostics table names 'SOLV-SEM-050' for 'no enclosing function returns a Result'. A top-level 'get()?' has the implicit main as its enclosing function; the operand is a genuine Result, so only the boundary rule is violated | No |
| SOL-TCK-0317, SOL-TCK-0318 | REQ-2004 | 23.3 Propagation | COMPILE_ERROR 'SOLV-SEM-051'; COMPILE_ERROR 'SOLV-SEM-051'. LANGUAGE_SPEC 23.3 Propagation: the unwrapped success type T must be assignable to T2, the propagated error type E must be assignable to E2, and the required-diagnostics table names 'SOLV-SEM-051' for a non-assignable T/E. One arm mismatches the success type (String into an Integer boundary) and one the error type (String into an Integer error boundary), so the two clauses are isolated and the tested failures are genuine non-assignabilities | No |
| SOL-TCK-0319, SOL-TCK-0320 | REQ-2005 | 23.3 Propagation / 22.3 try, catch, and finally | stdout 'err=true e=x', exit 0; stdout 'finerr=true e=x', exit 0. LANGUAGE_SPEC 23.3 Propagation: propagation is a control-flow transition, not a wrong-variant fault, and an Err carried through '?' returns normally rather than raising a fault; LANGUAGE_SPEC 22.3 try, catch, and finally: the finally block runs on every exit path of the try. A broad catch (e: Exception) must not intercept the return (its 'CAUGHT' marker is absent), and the finally block's 'fin' must precede the caller's observation of the Err | No |
| SOL-TCK-0321 | REQ-2006 | 23.3 Propagation | stdout 'a=true,42 b=true,bad', exit 0. LANGUAGE_SPEC 23.3 Propagation: the '?' operator and the operations of section 23 are independent, and a program may use either or both. Both variants propagate and each returned Result is then read through isOk/unwrap and isErr/unwrapErr; an implementation defining '?' in terms of unwrap would fault on the Err arm | No |

## Batch: section 6/7 core gaps (REQ-2200..REQ-2217)

18 requirements and 19 tests (SOL-TCK-0322..SOL-TCK-0340) for LANGUAGE_SPEC sections 6 and 7. The
earlier batches covered override/virtual dispatch, `extends Any`, member resolution, `this`, arity,
display, and `SOLV-SEM-047`, but left a large set of declaration rules unrepresented: explicit
parameter types, the reserved implicit `main`, no overloading, same-scope redeclaration,
statement-position expression rules, constructor and member-name rules, the implicit-constructor and
`super` rules, the `toString` override contract, property definite initialization, static zero
values, and lazy class initialization.

All expectations were derived from the quoted specification text before any program ran. Section 6
and section 7 name codes only for the static rules (`SOLV-SEM-046`, `SOLV-SEM-047`, `SOLV-SEM-048`),
so every other rejection here is asserted bare -- a compile-time error and nothing about its code --
and each negative program carries an `EXECUTED-INVALID` sentinel that would be observable if the
invalid program executed. The two tabulated static codes are pinned exactly.

The three positive arms are discriminating rather than decorative:

* **Definite initialization** is a matched pair: the rejection assigns the property only on the true
  branch, and the accepted arm differs by exactly the `else` branch that assigns on the other path.
  An implementation that accepted both, or rejected both, fails one arm.
* **Static zero values** reads an uninitialized cell of each named type and prints the specification
  list's own defaults (`0`, `false`, `0.0`, `null`), so the oracle is derived from the sentence and
  not captured from the runtime.
* **Lazy initialization** declares two classes with observable initializer blocks; the unused one's
  block must not run and the used one's must run once at its first active use. The companion test
  constructs a subclass and observes the superclass's marker before the subclass's, which is the
  section's own ordering rule.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0322 | REQ-2200 | syntax: 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: Parameter types must be explicit in the initial implementation. A parameter written without a type is the negated clause; the spec names no code, so the oracle is bare. The sentinel proves non-execution | No |
| SOL-TCK-0323 | REQ-2201 | 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: The entry point is always implicit: declaring a function named 'main' explicitly, in the root or in any included file, is a compile-time error. The declaration is exactly the forbidden shape | No |
| SOL-TCK-0324 | REQ-2202 | 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: Functions are not overloaded in the initial language: two functions with the same name in one scope are a compile-time error. Two same-named functions with different parameter types are an overload pair and must not be selected by argument type | No |
| SOL-TCK-0325 | REQ-2203 | 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: Names use lexical scope. Redeclaration in the same scope is an error. The negative arm redeclares a top-level binding in the same scope | No |
| SOL-TCK-0326 | REQ-2204 | evaluation: 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: A call may be used as a statement. Other value-producing expressions cannot stand alone as statements. A bare arithmetic expression in statement position is the forbidden non-call | No |
| SOL-TCK-0327 | REQ-2205 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: A class member declaration other than the constructor cannot have the same name as its class. A property named after the class is the forbidden declaration | No |
| SOL-TCK-0328 | REQ-2206 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: A class has at most one constructor declaration. Two constructors differing only in parameters are the negated clause | No |
| SOL-TCK-0329 | REQ-2207 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: a constructor is not declared by an interface, is not forwarded by a delegate, and cannot be invoked as 'this.User(...)'. The constructor body invokes itself in exactly that form | No |
| SOL-TCK-0330 | REQ-2208 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: A class with no explicit constructor has an implicit zero-argument initializer only when all properties have declaration initializers. An uninitialized property plus a no-argument construction is the negated clause | No |
| SOL-TCK-0331 | REQ-2209 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: A subclass constructor must invoke 'super(arguments)' as its first statement when the superclass has no zero-argument initializer. The subclass constructor omits it | No |
| SOL-TCK-0332 | REQ-2210 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: declaring 'toString' without 'override', changing its parameter list, or returning a type other than 'String' is a compile-time error. The method omits 'override' | No |
| SOL-TCK-0333, SOL-TCK-0334 | REQ-2211 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code); stdout 'a', exit 0. LANGUAGE_SPEC 7. Classes: Every property without a declaration initializer must be assigned exactly once on every successful constructor path before it is read. The rejection assigns only on the true branch; the acceptance differs by exactly the else-assignment and reads the value | No |
| SOL-TCK-0335 | REQ-2212 | 7. Static members and class initialization | stdout '0false0.0null', exit 0. LANGUAGE_SPEC 7. Static members and class initialization: Each cell begins at its declared type's zero value -- 0 for every integer type, 0.0 for Float/Double, false for Boolean, the NUL character for Character, and null for every reference type -- whether or not the declaration supplies an initializer. Four named zero values are read without assignment | No |
| SOL-TCK-0336 | REQ-2213 | 7. Static members and class initialization | COMPILE_ERROR 'SOLV-SEM-046'. LANGUAGE_SPEC 7. Static members and class initialization: A class declares at most one class initializer block, and a second block is 'SOLV-SEM-046'. Two static blocks pin the specification-named code | No |
| SOL-TCK-0337 | REQ-2214 | 7. Static members and class initialization | COMPILE_ERROR 'SOLV-SEM-048'. LANGUAGE_SPEC 7. Static members and class initialization: A static member may not mention a type parameter of its enclosing class, which is 'SOLV-SEM-048'. A static method whose parameter and return type use the class type parameter is the exact shape | No |
| SOL-TCK-0338 | REQ-2215 | 7. Static members and class initialization | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Static members and class initialization: A static member is not inherited and is not overridable; it is reached only through the name of the class that declares it, so a subclass does not expose its superclass's static members. Reading the superclass static through the subclass name is the forbidden access | No |
| SOL-TCK-0339 | REQ-2216 | 7. Static members and class initialization | stdout 'startinitA5middone', exit 0. LANGUAGE_SPEC 7. Static members and class initialization: A class that is never actively used is never initialized, and its first active use runs its initializer once. The unused class's block is absent and the used class's runs once at the first static read | No |
| SOL-TCK-0340 | REQ-2217 | 7. Static members and class initialization | stdout 'startABend', exit 0. LANGUAGE_SPEC 7. Static members and class initialization: its direct superclass is initialized first, transitively up to the root. Constructing the subclass is the first active use, so the superclass marker precedes the subclass marker | No |

## Batch: interface contract, generic exceptions, and Result diagnostics (REQ-2300..REQ-2309)

10 requirements and 12 tests (SOL-TCK-0341..SOL-TCK-0352) closing the three remaining documented
coverage gaps: section 8's interface method contract, section 22.1's generic-exception rules, and
section 23.4's `Result` required diagnostics. Section 8 names no diagnostic code, so its three
rejections are bare; sections 22.1/22.6 and 23.4 name their codes in tables, so those oracles pin
the exact code.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0341 | REQ-2300 | 8. Interfaces | COMPILE_ERROR (bare rejection -- section 8 names no code). LANGUAGE_SPEC 8. Interfaces: Interfaces contain methods, not stored properties. A val property in an interface is the negated sentence | No |
| SOL-TCK-0342 | REQ-2301 | 8. Interfaces | COMPILE_ERROR (bare rejection -- section 8 names no code). LANGUAGE_SPEC 8. Interfaces: An implementing method must use the same parameter types and a covariant return type. The class changes the interface parameter type from String to Integer; treating it as an overload would leave the contract unimplemented | No |
| SOL-TCK-0343, SOL-TCK-0344 | REQ-2302 | 8. Interfaces | stdout 'covok', exit 0; COMPILE_ERROR (bare rejection -- section 8 names no code). LANGUAGE_SPEC 8. Interfaces: An implementing method must use the same parameter types and a covariant return type. The accepted arm returns the subtype Dog for an Animal-returning interface method; the rejected arm returns an unrelated Rock, so the pair differs only in the return type's relation to Animal | No |
| SOL-TCK-0345, SOL-TCK-0346 | REQ-2303 | 8. Interfaces | COMPILE_ERROR (bare rejection -- section 8 names no code); stdout 'confc', exit 0. LANGUAGE_SPEC 8. Interfaces: If multiple interfaces provide an otherwise unresolved default for the same method, the class must explicitly override it. Two interfaces supply the same default; the rejection omits the override and the acceptance declares it and runs the override | No |
| SOL-TCK-0347 | REQ-2304 | 22.1 Exception types | COMPILE_ERROR 'SOLV-SEM-053'. LANGUAGE_SPEC 22.1 Exception types: A generic class cannot be thrown or caught at all today, and the 22.6 table names 'SOLV-SEM-053' for the throw operand expression. Throwing a parameterized generic instance is the non-exception operand shape | No |
| SOL-TCK-0348 | REQ-2305 | 22.1 Exception types | COMPILE_ERROR 'SOLV-SEM-054'. LANGUAGE_SPEC 22.1 Exception types: A generic class cannot be thrown or caught at all today, and the 22.6 table names 'SOLV-SEM-054' for the catch clause's type reference. The handler names a parameterized generic while the thrown value is an ordinary user exception | No |
| SOL-TCK-0349 | REQ-2306 | 22.1 Exception types | COMPILE_ERROR 'SOLV-TYPE-003'. LANGUAGE_SPEC 22.1 Exception types: for a generic one, supplying more arguments than its declared constructor has remains an ordinary arity error rather than a message; section 22.1 names 'SOLV-TYPE-003' as that arity error. Two arguments for a zero-argument generic constructor must not become a message | No |
| SOL-TCK-0350 | REQ-2307 | 23.4 Required diagnostics | COMPILE_ERROR 'SOLV-RESOL-004'. LANGUAGE_SPEC 23.4 Required diagnostics: the table names 'RESOL_UNKNOWN_MEMBER' / 'SOLV-RESOL-004' for a member of a Result receiver that is not a Result operation. An unknown member on a Result receiver pins the code | No |
| SOL-TCK-0351 | REQ-2308 | 23.4 Required diagnostics | COMPILE_ERROR 'SOLV-TYPE-003'. LANGUAGE_SPEC 23.4 Required diagnostics: the table names 'TYPE_ARITY_MISMATCH' / 'SOLV-TYPE-003' for a Result operation call with the wrong argument count. isOk takes no arguments, so supplying one pins the code | No |
| SOL-TCK-0352 | REQ-2309 | 23.4 Required diagnostics | COMPILE_ERROR 'SOLV-TYPE-014'. LANGUAGE_SPEC 23.4 Required diagnostics: the table names 'TYPE_FUNCTION_AS_VALUE' / 'SOLV-TYPE-014' for a bare member read of a Result operation without a call. Reading r.isOk without calling it is exactly that shape | No |

## Batch: control flow, returns, arity, Unit, and switch labels (REQ-2400..REQ-2407)

8 requirements and 9 tests (SOL-TCK-0353..SOL-TCK-0361) closing further section 4/6/13/17
obligations: the pre-test `while` loop, the `return`/return-type rules, the trailing comma in call
argument lists, a program with no entry point, the rendering of `Unit`, the compile-time-constant
requirement for constant switch cases, and the `String` requirement for regex cases.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0353 | REQ-2400 | 17. Control Flow | stdout '012', exit 0. LANGUAGE_SPEC 17. Control Flow: 'while' is a pre-test loop. The body prints the counter and increments it, so the output is exactly the three iterations the pre-test condition admits; a post-test loop would print an extra zero | No |
| SOL-TCK-0354 | REQ-2401 | 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: 'return;' is valid only in a function declared without a return type. A bare return in an Integer-returning function is the negated form | No |
| SOL-TCK-0355 | REQ-2402 | 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: 'return value' requires the value to be assignable to the declared return type. A value-less function returning 1 is not assignable to its Unit result | No |
| SOL-TCK-0356, SOL-TCK-0357 | REQ-2403 | 6. Functions | stdout '3', exit 0; COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: A call's argument list may end with a trailing comma and it contributes no argument, but the list still requires at least one argument, so 'add(,)' is a parse error while 'add()' is the ordinary empty argument list. The accepted arm's sum proves the comma added no argument | No |
| SOL-TCK-0358 | REQ-2404 | process: 6. Functions | stdout empty, exit 0. LANGUAGE_SPEC 6. Functions: A program with no executable top-level statements has no entry point and does nothing. The program declares one function and no top-level statement | No |
| SOL-TCK-0359 | REQ-2405 | 4. Root Type Hierarchy | stdout 'xUnit', exit 0. LANGUAGE_SPEC 4. Root Type Hierarchy: built-in scalars have fixed, non-overridable implementations and 'Unit renders Unit', and Unit has one value and is the result of a function that returns normally without a value. A value-less function's result is bound to a Unit local and printed | No |
| SOL-TCK-0360 | REQ-2406 | 13. switch | COMPILE_ERROR (bare rejection -- section 13 names no code). LANGUAGE_SPEC 13. switch: Constant case expressions must be compile-time constants assignable to the switched value's type. A case label naming a runtime binding is not a compile-time constant | No |
| SOL-TCK-0361 | REQ-2407 | 13. switch | COMPILE_ERROR (bare rejection -- section 13 names no code). LANGUAGE_SPEC 13. switch: Regex cases require a String switch value. The switch value is an Integer and one case is a regex pattern | No |

## Batch: built-in rendering and static-member rules (REQ-2500..REQ-2507)

8 requirements and 8 tests (SOL-TCK-0362..SOL-TCK-0369) for section 4's fixed built-in rendering and
extensibility rule and for the section 7 static-member rules that earlier batches did not enumerate:
the class name as a receiver rather than a value, access through an instance, the shared static
member namespace, `this`/`super` inside static members, and the instance-only scope of the
`toString`/`equals`/`hashCode` reservation.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0362 | REQ-2500 | 4. Root Type Hierarchy | stdout '42|42|1.5|1.5|A|hi|true', exit 0. LANGUAGE_SPEC 4. Root Type Hierarchy: built-in scalars provide fixed, non-overridable implementations, with Integer/Long/Byte/Short in decimal, Float/Double in Java-style text, Boolean as true or false, Character as its character, and String as its contents. One value per type is printed with separators, so each token is derived from the sentence's own list | No |
| SOL-TCK-0363 | REQ-2501 | 4. Root Type Hierarchy | COMPILE_ERROR (bare rejection -- section 4 names no code). LANGUAGE_SPEC 4. Root Type Hierarchy: A built-in scalar cannot be extended and its toString cannot be overridden. A class declaring 'extends Integer' is the forbidden extension | No |
| SOL-TCK-0364 | REQ-2502 | 7. Static members and class initialization | COMPILE_ERROR 'SOLV-TYPE-016'. LANGUAGE_SPEC 7. Static members and class initialization: the class name in a static reference is a receiver, not a value, and a class name used anywhere else remains 'SOLV-TYPE-016'; 'val c = Counter' is the named rejection. The code is specification-named | No |
| SOL-TCK-0365 | REQ-2503 | 7. Static members and class initialization | COMPILE_ERROR (bare rejection -- section 7 names no code for the instance form). LANGUAGE_SPEC 7. Static members and class initialization: a read through an instance such as 'instance.limit' is rejected. The program reads the static property through an instance | No |
| SOL-TCK-0366 | REQ-2504 | 7. Static members and class initialization | COMPILE_ERROR (bare rejection -- the quoted rule states the namespace sharing). LANGUAGE_SPEC 7. Static members and class initialization: a static member shares the class's member namespace, so a static property and an instance property may not reuse a name. The program declares both as x | No |
| SOL-TCK-0367 | REQ-2505 | 7. Static members and class initialization | COMPILE_ERROR 'SOLV-RESOL-005'. LANGUAGE_SPEC 7. Static members and class initialization: a static member has no receiver, and this inside a static method is 'SOLV-RESOL-005'. The code is specification-named | No |
| SOL-TCK-0368 | REQ-2506 | 7. Static members and class initialization | COMPILE_ERROR 'SOLV-RESOL-006'. LANGUAGE_SPEC 7. Static members and class initialization: a static member has no receiver, and super inside a static method is 'SOLV-RESOL-006'. The code is specification-named | No |
| SOL-TCK-0369 | REQ-2507 | 7. Static members and class initialization | stdout '7', exit 0. LANGUAGE_SPEC 7. Static members and class initialization: the toString/equals/hashCode reserved-name rules apply to instance members only, so a static member may use those names. The class-name read observes the static cell's value | No |

## Batch: include diagnostics, static return, delegates, and Any (REQ-2600..REQ-2606)

8 tests (SOL-TCK-0370..SOL-TCK-0377) closing the remaining specification-named diagnostics that no
earlier batch addressed (`SOLV-RESOL-007`, `SOLV-RESOL-009`, `SOLV-RESOL-015`, `SOLV-TYPE-011`) plus
the section 9 delegate declaration rules and the section 4 `Any` top-type clause. The only
specification-named code still untested is `SOLV-RESOL-010` (include I/O failure), which requires
denying filesystem access; the inventory records it as `untested-platform` rather than inventing a
host-dependent fixture.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0370, SOL-TCK-0371 | REQ-2600 | 20. File Inclusion | COMPILE_ERROR 'SOLV-RESOL-007'; COMPILE_ERROR 'SOLV-RESOL-007'. LANGUAGE_SPEC 20. File Inclusion: the path must be non-empty and its final file name must end in .sol, otherwise 'SOLV-RESOL-007' at the path literal; the required-diagnostics table names the code. One arm uses a .txt suffix and one the empty path | No |
| SOL-TCK-0372 | REQ-2601 | 20. File Inclusion | COMPILE_ERROR 'SOLV-RESOL-009'. LANGUAGE_SPEC 20. File Inclusion: the required-diagnostics table names 'RESOL_INCLUDE_NOT_FILE' / 'SOLV-RESOL-009' for the include directive, and the file is normalized and canonicalized before use. The fixture path has the required .sol spelling but resolves to a directory, so it is not a regular file | No |
| SOL-TCK-0373 | REQ-2602 | 20. File Inclusion | COMPILE_ERROR 'SOLV-RESOL-015'. LANGUAGE_SPEC 20. File Inclusion: the required-diagnostics table names 'RESOL_UNKNOWN_MODULE' / 'SOLV-RESOL-015' for a qualified reference. No included file declares the referenced module | No |
| SOL-TCK-0374 | REQ-2603 | 7. Static members and class initialization | COMPILE_ERROR 'SOLV-TYPE-011'. LANGUAGE_SPEC 7. Static members and class initialization: a class initializer block holds statements and returns nothing, and a return with a value is 'SOLV-TYPE-011'. The block contains 'return 1' | No |
| SOL-TCK-0375 | REQ-2604 | 9. Composition and Delegation | COMPILE_ERROR (bare rejection -- section 9 names no code). LANGUAGE_SPEC 9. Composition and Delegation: A delegate is an immutable, explicitly typed property. The declaration omits the type annotation | No |
| SOL-TCK-0376 | REQ-2605 | 9. Composition and Delegation | COMPILE_ERROR (bare rejection -- section 9 names no code). LANGUAGE_SPEC 9. Composition and Delegation: a delegate must be initialized under the normal constructor rules. The empty constructor leaves it unassigned | No |
| SOL-TCK-0377 | REQ-2606 | 4. Root Type Hierarchy | stdout '42|hi|true', exit 0. LANGUAGE_SPEC 4. Root Type Hierarchy: Any is the sole top type for every non-null Solvik value. A scalar and a String bind to Any and read back, and the is test shows the dynamic type is still inspectable | No |

## Batch: explicit conversion and remaining core rules (REQ-2700..REQ-2710)

11 requirements and 12 tests (SOL-TCK-0378..SOL-TCK-0389) for explicit numeric conversion
(section 4), nominal assignability (section 3), multiple-inheritance prohibition and `super.member`
(section 7), delegate immutability (section 9), sealed-class abstractness and enum payload typing
(section 12), `is`-narrowing invalidation (section 18), and the explicit `: Unit` return type
(section 6). Section 4 names no code for the constant-conversion error, so that rejection is bare;
the runtime conversion fault uses the protocol's `ARITHMETIC_ERROR` category.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0378 | REQ-2700 | 4. Root Type Hierarchy | stdout '1|2|3|4', exit 0. LANGUAGE_SPEC 4. Root Type Hierarchy: every narrowing and precision-losing conversion uses an explicit built-in type call such as Long(value), and Byte/Short values use explicit conversion. One in-range value per target type converts and prints | No |
| SOL-TCK-0379, SOL-TCK-0380 | REQ-2701 | 4. Root Type Hierarchy | COMPILE_ERROR (bare rejection -- section 4 names no code). LANGUAGE_SPEC 4. Root Type Hierarchy: an out-of-range constant conversion is a compile-time error. One arm is one past the Integer maximum; the other is outside the Byte range | No |
| SOL-TCK-0381 | REQ-2702 | 4. Root Type Hierarchy | RUNTIME_ERROR 'ARITHMETIC_ERROR', stdout empty. LANGUAGE_SPEC 4. Root Type Hierarchy: an out-of-range integral conversion raises a Solvik runtime arithmetic error. The value is held in a mutable binding, so the fault is a run-time event and a compile rejection cannot satisfy it | No |
| SOL-TCK-0382 | REQ-2703 | 3. Static and Strong Typing | COMPILE_ERROR (bare rejection -- section 3 names no code). LANGUAGE_SPEC 3. Static and Strong Typing: Two unrelated classes with identical members are not assignment-compatible. A and B share a property but no inheritance relation | No |
| SOL-TCK-0383 | REQ-2704 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: Multiple class inheritance is forbidden. The declaration writes two superclass names | No |
| SOL-TCK-0384 | REQ-2705 | 9. Composition and Delegation | COMPILE_ERROR (bare rejection -- section 9 names no code). LANGUAGE_SPEC 9. Composition and Delegation: A delegate is an immutable, explicitly typed property. A method assigns to it after the constructor initialized it | No |
| SOL-TCK-0385 | REQ-2706 | 12. Enums, Sealed Types, and Exhaustive Match | COMPILE_ERROR (bare rejection -- section 12 names no code). LANGUAGE_SPEC 12. Enums, Sealed Types, and Exhaustive Match: A sealed class is abstract and may be extended only by declarations in the same physical source file. The program constructs the sealed class directly | No |
| SOL-TCK-0386 | REQ-2707 | 12. Enums, Sealed Types, and Exhaustive Match | COMPILE_ERROR (bare rejection -- no code named for the payload mismatch). LANGUAGE_SPEC 12/3: enum variants are nested nominal constructors, and each call argument must be assignable to its declared parameter type. Variant A carries an Integer and receives a String | No |
| SOL-TCK-0387 | REQ-2708 | 7. Classes | stdout 'BC', exit 0. LANGUAGE_SPEC 7. Classes: super.member accesses the immediate superclass implementation, and an open member may be overridden while all other members are final. A three-level hierarchy re-marks each override open; the most derived method concatenates after super.label(), so BC proves super reached the immediate parent | No |
| SOL-TCK-0388 | REQ-2709 | 18. Type Tests and Casts | COMPILE_ERROR (bare rejection -- section 18 names no code). LANGUAGE_SPEC 18. Type Tests and Casts: The compiler must narrow the type where the checked value is stable and no intervening write can invalidate the refinement. A write to the narrowed var occurs before a read requiring the narrowed type | No |
| SOL-TCK-0389 | REQ-2710 | 6. Functions | stdout 'unitok', exit 0. LANGUAGE_SPEC 6. Functions: Writing ': Unit' explicitly is permitted but redundant, and a declaration that omits the return type returns no value and has type Unit. The explicit spelling is accepted and its side effect runs | No |

## Unexercised obligations (REQ-2800..REQ-2805)

Six normative obligations the TCK records but cannot exercise, each with an explicit state rather
than silent omission (TCK.md section 5.1). They are listed in the full-language profile with an
empty `tests` list, so `validate` reports them as gaps that block full-profile certification.

| Requirement | Section | State | Normative source (quoted) and rationale | Capture-from-IUT? |
|---|---|---|---|---|
| REQ-2800 | 10. Built-in Types and Runtime Representation | untested-ambiguous | LANGUAGE_SPEC 10: language-level primitive types are class types conceptually but the runtime may specialize them, and the object model must not force unnecessary boxing. A representation/performance choice with no language observable, so asserting either representation would invent an observable. | No |
| REQ-2801 | 19. Semantic Priorities | untested-ambiguous | LANGUAGE_SPEC 19: when language features conflict, prefer compile-time correctness through syntactic convenience. A design-time tie-break with no guest-observable behavior; both designs may satisfy the specification. | No |
| REQ-2802 | 20. File Inclusion | untested-platform | LANGUAGE_SPEC 20: denied access and other I/O failures become 'SOLV-RESOL-010' and never escape as host errors. Producing an I/O failure portably requires denying filesystem access, a host facility the portable corpus cannot assume. | No |
| REQ-2803 | 20. File Inclusion | untested-platform | LANGUAGE_SPEC 20: a path beginning with the exact prefix '~/' is expanded against the host user's home directory. Depends on the host user's home directory, which the portable corpus cannot declare consistently. | No |
| REQ-2804 | 20. File Inclusion | untested-platform | LANGUAGE_SPEC 20: an absolute expanded include path is used directly. Names a host-specific workspace location the program cannot know portably. | No |
| REQ-2805 | 6. Functions | untested-portable | LANGUAGE_SPEC 6: a local variable must be definitely initialized before it is read. Portable in principle, but the current grammar requires an initializer on every local declaration, so the control-flow half of the rule is not expressible; the property form is covered by REQ-2211. | No |

## Batch: variables, scope blocks, switch default, range loop, include identity (REQ-2900..REQ-2908)

9 requirements and 9 tests (SOL-TCK-0390..SOL-TCK-0398) for `var` mutability (section 2), top-level
bindings as implicit-main locals and scope-block semantics (section 6), the optional statement
`switch` default (section 13), the range loop variable's immutability and scope (section 17), literal
include paths, and canonical include identity (section 20).

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0390 | REQ-2900 | 2. Variables and Mutability | stdout 'var2', exit 0. LANGUAGE_SPEC 2. Variables and Mutability: var declares a mutable binding/property. The local is reassigned and the new value printed | No |
| SOL-TCK-0391 | REQ-2901 | 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: a top-level val/var is a local of the implicit main, not a global. A declared function cannot resolve it | No |
| SOL-TCK-0392 | REQ-2902 | 6. Functions | stdout 's1s2', exit 0. LANGUAGE_SPEC 6. Functions: sibling blocks are independent scopes, so the same local name may be declared in each. Both declarations are accepted and both values print | No |
| SOL-TCK-0393 | REQ-2903 | 6. Functions | stdout 'sbdone', exit 0. LANGUAGE_SPEC 6. Functions: a scope block is neither a loop nor a function boundary, so break inside it applies to the enclosing loop. The break exits the range loop | No |
| SOL-TCK-0394 | REQ-2904 | 13. switch | stdout 'swafter', exit 0. LANGUAGE_SPEC 13. switch: a statement switch may omit default and do nothing when no label matches. The non-matching switch runs nothing and the program continues | No |
| SOL-TCK-0395 | REQ-2905 | 17. Control Flow | COMPILE_ERROR (bare rejection -- section 17 names no code). LANGUAGE_SPEC 17. Control Flow: the loop variable is an implicitly declared immutable Integer binding. Assigning to it is rejected | No |
| SOL-TCK-0396 | REQ-2906 | 17. Control Flow | COMPILE_ERROR (bare rejection -- section 17 names no code). LANGUAGE_SPEC 17. Control Flow: the loop variable is scoped to the loop body. Reading it after the loop is rejected | No |
| SOL-TCK-0397 | REQ-2907 | 20. File Inclusion | COMPILE_ERROR 'SOLV-RESOL-008'. LANGUAGE_SPEC 20. File Inclusion: environment-variable expansion is not part of the language, and the required-diagnostics table names SOLV-RESOL-008 for a missing include. The $ in the literal path is not expanded, so the file is not found | No |
| SOL-TCK-0398 | REQ-2908 | 20. File Inclusion | stdout 'cacz', exit 0. LANGUAGE_SPEC 20. File Inclusion: two paths that resolve to the same file are the same include, and a canonical file is expanded at most once. `sub/../a.sol` canonicalizes to `a.sol`, so the included print runs once | No |

## Batch: remaining `Result` operations (REQ-3000..REQ-3005)

6 requirements and 6 tests (SOL-TCK-0399..SOL-TCK-0404) for the section 23 operation-table rows not
covered by the wrong-variant, must-consume, or propagation batches: `expect` on either variant and
its exactly-once message evaluation, `ignore`'s single receiver evaluation and `Unit` result,
`isOk`/`isErr` complementarity, and the successful `unwrapErr` path.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0399 | REQ-3000 | 23. Result Operations | stdout 'exp5', exit 0. LANGUAGE_SPEC 23. Result Operations: expect(message) returns the success payload of an Ok, ignoring the message. The receiver is an Ok and its payload is printed | No |
| SOL-TCK-0400 | REQ-3001 | 23. Result Operations | RUNTIME_ERROR 'RESULT_WRONG_VARIANT', stdout empty. LANGUAGE_SPEC 23. Result Operations: on an Err, expect raises a runtime fault reporting the message together with the carried error. The fault category is the protocol's normative classification of a Result wrong-variant fault | No |
| SOL-TCK-0401 | REQ-3002 | 23. Result Operations | stdout 'm1', exit 0. LANGUAGE_SPEC 23. Result Operations: the message is evaluated exactly once whenever the call runs, on either variant. A printing message function runs once before the returned payload | No |
| SOL-TCK-0402 | REQ-3003 | 23. Result Operations | stdout 'pdone', exit 0. LANGUAGE_SPEC 23. Result Operations: ignore evaluates its receiver exactly once, discards the value, and yields Unit. The receiver prints once and the result binds to a Unit local | No |
| SOL-TCK-0403 | REQ-3004 | 23. Result Operations | stdout 'truefalse', exit 0. LANGUAGE_SPEC 23. Result Operations: isOk and isErr are complementary tests over the variant and never fault. Both are read on one Ok value | No |
| SOL-TCK-0404 | REQ-3005 | 23. Result Operations | stdout 'e', exit 0. LANGUAGE_SPEC 23. Result Operations: unwrapErr returns the error payload of an Err. The Ok-fault half is covered by REQ-0206 | No |

## Batch: include placement, built-in shadowing, finality, switch default, unwrap (REQ-3100..REQ-3107)

8 requirements and 10 tests (SOL-TCK-0405..SOL-TCK-0414) surfaced by a sentence-level audit:
include placement inside a compilation unit, built-in redeclaration and shadowing, final-by-default
class extension, static-initializer typing (`SOLV-TYPE-001`), the single trailing switch default,
the successful `unwrap` path, built-in call arity, and nested relative include resolution against
the including file.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0405 | REQ-3100 | 20. File Inclusion | COMPILE_ERROR (bare rejection -- section 20 names no code). LANGUAGE_SPEC 20. File Inclusion: an include may appear only as an item of a compilation unit and cannot appear in a function, method, constructor, block, loop, switch, or match branch. The include is inside a function body | No |
| SOL-TCK-0406, SOL-TCK-0407 | REQ-3101 | 20. File Inclusion | COMPILE_ERROR (bare rejection -- section 20 names no code); COMPILE_ERROR (bare rejection). LANGUAGE_SPEC 20. File Inclusion: redeclaring a built-in function or type is rejected, and built-in names cannot be shadowed. One arm declares a function print, the other a class Byte | No |
| SOL-TCK-0408 | REQ-3102 | 7. Classes | COMPILE_ERROR (bare rejection -- section 7 names no code). LANGUAGE_SPEC 7. Classes: classes are final by default and a class must explicitly opt into inheritance. A class without open is extended | No |
| SOL-TCK-0409 | REQ-3103 | 7. Classes | COMPILE_ERROR 'SOLV-TYPE-001'. LANGUAGE_SPEC 7. Classes: a static declaration initializer that is not assignable to the declared type is SOLV-TYPE-001. A String initializes an Integer static property; the code is specification-named | No |
| SOL-TCK-0410, SOL-TCK-0411 | REQ-3104 | 13. switch | COMPILE_ERROR (bare rejection -- section 13 names no code); COMPILE_ERROR (bare rejection). LANGUAGE_SPEC 13. switch: A switch contains at most one default, and it must be last. One arm has a second default and one has a default followed by a case | No |
| SOL-TCK-0412 | REQ-3105 | 23. Result Operations | stdout 'uw9', exit 0. LANGUAGE_SPEC 23. Result Operations: unwrap returns the success payload of an Ok. The Ok-payload path complements the Err-fault path of REQ-0206 | No |
| SOL-TCK-0413 | REQ-3106 | 6. Functions | COMPILE_ERROR (bare rejection -- section 6 names no code). LANGUAGE_SPEC 6. Functions: the predeclared print, println, and exit functions each declare exactly one parameter, so a call with a different argument count is a compile-time error. print() supplies zero arguments | No |
| SOL-TCK-0414 | REQ-3107 | 20. File Inclusion | stdout 'nm7', exit 0. LANGUAGE_SPEC 20. File Inclusion: every nested relative include is resolved against its including file, never the root directory. The nested n.sol exists only beside m.sol in lib/, so success depends on resolving against the including file | No |

## Batch: static-toString dispatch and deferred safe cast (REQ-3200..REQ-3201)

2 requirements and 2 tests (SOL-TCK-0415..SOL-TCK-0416) for the last two guest-observable rules
surfaced by the sentence-level audit.

| Tests | Requirement | Section | Expected | Normative source (quoted), requirement summary, and rationale | Capture-from-IUT? |
|---|---|---|---|---|---|
| SOL-TCK-0415 | REQ-3200 | 7. Static members and class initialization | stdout 'stC', exit 0. LANGUAGE_SPEC 7. Static members and class initialization: a static member never enters the dispatch table, so a static func toString() cannot replace Any.toString(), and instance formatting still calls the universal member. The instance call prints the class name C, not the static method's text | No |
| SOL-TCK-0416 | REQ-3201 | 18. Type Tests and Casts | COMPILE_ERROR (bare rejection -- section 18 names no code). LANGUAGE_SPEC 18. Type Tests and Casts: safe-cast syntax is deferred. The `as?` spelling is not defined and is a parse error | No |

## Additional unexercised obligations (REQ-2806..REQ-2808)

Three further specification statements recorded with explicit states rather than omitted.

| Requirement | Section | State | Normative source (quoted) and rationale | Capture-from-IUT? |
|---|---|---|---|---|
| REQ-2806 | 1. Design Goals (Versioning) | untested-ambiguous | LANGUAGE_SPEC Versioning: a new specification revision is declared only when the normative semantics change, released revisions are immutable, and pre-1.0 conformance reports must state that certification is withheld. A conformance-process rule enforced by the TCK itself (independent version identifiers, immutable-input digest binding, and the empty certifiable-version set), not a guest-language observable. | No |
| REQ-2807 | 1. Design Goals | untested-ambiguous | LANGUAGE_SPEC Design Goals: features explicitly marked deferred are not part of the language, and an implementation must not invent semantics for a deferred or unspecified feature. A blanket policy over many deferred features; each deferred feature with a definite spelling is tested where the specification gives one (REQ-0402, REQ-3201), and the residual policy has no single program. | No |
| REQ-2808 | 3. Equality and reference identity | untested-ambiguous | LANGUAGE_SPEC 3. Equality and reference identity: a user equals implementation must be reflexive, symmetric, transitive, consistent, and false for null, but the compiler and runtime do not prove or repair those properties. An obligation on user implementations that the specification explicitly says is unenforced; the accepted non-reflexive override is observed by the REQ-1706 dispatch test, and the contract itself is not machine-checkable from one program. | No |

## Additional unexercised obligations (REQ-2809..REQ-2810)

| Requirement | Section | State | Normative source (quoted) and rationale | Capture-from-IUT? |
|---|---|---|---|---|
| REQ-2809 | 21.7 Result types | untested-ambiguous | LANGUAGE_SPEC 21.7 Result types: a set of branches whose only shared supertypes are incomparable has no single nearest result and is a compile-time error. Unreachable under the Any top type: every class/interface has Any as a comparable declared supertype, so any branch set joins at Any (the spec's own `if (flag) { 1 } else { "text" }` -- type Any example confirms the accept side) and no program reaches the error; asserting a guess at 'incomparable under a top type' would fabricate a rule the document does not define. | No |
| REQ-2810 | 15. Strings (Rust-style raw strings) | untested-ambiguous | LANGUAGE_SPEC 15. Strings (Rust-style raw strings): an unterminated raw string is a lexical error at its opening delimiter (covered by REQ-0404) and the diagnostic must show the exact closing delimiter that was expected. The residual diagnostic-wording clause is not assertable by a portable oracle: the oracle contract compares exit status and stdout only, and stderr wording is deliberately unconstrained (reported as an unconstrained observable, never a disagreement), so pinning it would adopt implementation-specific text. | No |
