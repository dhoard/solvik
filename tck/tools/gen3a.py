#!/usr/bin/env python3
"""Generate the section 3A TCK batch: nominal typing, Any, assignment, precedence,
short-circuit, evaluation order, and semantic comparability.

Oracles are DERIVED FROM LANGUAGE_SPEC.md and only then compared with the implementation.
Every expectation below is written from the quoted normative text; the probe results are
recorded as confirmation, and none was used to produce an expectation.

Code-pinning policy for this batch, established by checking each code against the
specification rather than by adopting what the launcher prints:

  * `SOLV-TYPE-039` is named by section 3 itself, so identity-operand failures are pinned.
  * `SOLV-TYPE-001` is named only for a *static* declaration initializer and for an
    exception message argument, so the nominal cross-assignment and `Any`-to-`Integer`
    rejections here are asserted BARE. This is the same reading already applied to
    SOL-TCK-0135 and SOL-TCK-0204, applied consistently rather than re-decided.
  * `SOLV-TYPE-004` and `SOLV-PARS-001` occur ZERO times in the specification, so every
    invalid-operand and every parse rejection in this batch is a bare `{
    }
    `.
  * `SOLV-RESOL-004` is named, but only as "a member of a `Result` receiver that is not a
    `Result` operation", so an unknown member on an `Any` receiver is asserted bare.
  * `SOLV-TYPE-014` is named only as "a bare member read of a `Result` operation", so a
    bare `value.equals` read is asserted bare here even though section 3 forbids it.

Several tests are built as discriminating PAIRS, so that a plausible alternative
implementation fails one arm rather than satisfying the suite by accident:

  * `??` is pinned as the LOWEST tier by a rejection. `a ?? 1 == 2` is ill-typed only if the
    operator binds loosest, making the right operand `Boolean` against an `Integer?` left;
    under any tighter reading it typechecks and would print a value. An acceptance with the
    same operands parenthesized is the control, so the rejection cannot be a blanket
    refusal of `??`.
  * unary minus versus `+` is pinned by `-2 + 3` printing `1`, which a right-associative or
    prefix-greedy lexer renders as `-5`; `2 * 3 + 4 * 5` printing `26` fails any
    left-to-right-no-precedence parser at `70`.
  * short-circuiting is pinned inside a single program per operator, by evaluating the
    shorting case and then the non-shorting case and printing one marker afterwards, so the
    effect marker must occur exactly once in the whole stream. The point of combining them is
    that the shorting case alone is satisfied by an implementation that simply never evaluates
    a right operand -- which would print the marker zero times and still pass that program.
    Requiring exactly one occurrence in a stream that contains both cases forces the
    short-circuit to be conditional rather than permanent.
  * "evaluates its left operand first and its right operand second, exactly once each" is
    pinned by a two-marker order program that runs both operand orderings, and separately
    by `!=` and `==` on counting operands, since `!=` negates a completed comparison and
    could plausibly re-run it.
  * `Any` does not disable type checking is pinned by a member call that fails on `Any` and
    succeeds when the same class is written as the declared type, plus a checked cast that
    restores the value -- so the rejection cannot be an artifact of the class or the member.
  * semantic comparability is pinned from both directions: unrelated nominal classes are not
    directly comparable, while the two escape routes the specification itself names (an
    `Any`-typed operand, or an explicit `equals` call) are both accepted.

`..` is used rather than `+` on `String`; that choice rests on section 3's own worked
example for `..`, not on the `+`-on-`String` examples in sections 8 and 12 that section 3
normatively forbids.
"""
import base64, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

# ---------------------------------------------------------------- requirements
REQS = {
 "REQ-1600": dict(
  section="3. Static and Strong Typing",
  summary="Typing is nominal: two unrelated classes declaring identical members are not assignment-compatible, and each is used only through its own type",
  kind="compile-time",
  notes="The accepted program declares two classes whose member sets are character-for-character the same and reads a member from each, so the structural match that a structural type system would accept is present in the program itself; the rejected program adds only the cross-assignment. The rejection is bare because section 3 states the incompatibility without naming a code, and the mismatch code section 7 names is scoped to static declaration initializers (the reading already applied to SOL-TCK-0135 and SOL-TCK-0204).",
  quotes=["Solvik uses nominal static typing.",
          "Two unrelated classes with identical members are not assignment-compatible."]),
 "REQ-1601": dict(
  section="3. Static and Strong Typing",
  summary="Assigning a value to Any does not disable type checking: members and operators are unavailable on an Any receiver and an Any value is not assignable to a narrower type without a checked cast",
  kind="compile-time",
  notes="Three rejections and their controls pin one rule from different angles: a member that exists on the class is unreachable through Any, arithmetic is unavailable, and a narrowing assignment is refused; the controls show the identical member call and value succeed when the declared type is the class itself and after a checked cast, so no rejection can be an artifact of the class, the member, or the operator. All three rejections are bare: the unknown-member code section 8 names is scoped to Result receivers and the mismatch code section 7 names is scoped to static initializers.",
  quotes=["`Any` must never behave like TypeScript's `any`. Assigning a value to `Any` does not disable type checking.",
          "A checked cast or type refinement is required."]),
 "REQ-1602": dict(
  section="3. Static and Strong Typing",
  summary="Assignments are statements, not value-producing expressions, so an assignment is rejected wherever an expression is required while a statement assignment to a mutable local is accepted",
  kind="syntax",
  notes="Four positions are refused -- nested inside another assignment, as an initializer, as a controlling condition, and as a call argument -- because 'not value-producing' is a statement about every expression position and one position alone cannot distinguish it from a local syntactic restriction. The accepted control assigns as a statement and prints the assigned value. Rejections are bare: the specification names no code for this, and these are parse-level refusals.",
  quotes=["Assignments are statements, not value-producing expressions. The target must be a `var mutable` local or a `var mutable` property."]),
 "REQ-1603": dict(
  section="3. Static and Strong Typing",
  summary="Operator precedence follows the stated tier order, with ?? the lowest tier and arithmetic tighter than the comparison and logical tiers above it",
  kind="compile-time",
  notes="Each tier relation is pinned by a program whose value differs under the neighbouring wrong reading: -2 + 3 yields 1 rather than -5, 2 * 3 + 4 * 5 yields 26 rather than 70, true || false && false is true, !false && false is false. The ?? tier is pinned by a rejection whose ill-typedness exists only if ?? binds loosest -- a ?? (1 == 2) compares an Integer? to a Boolean -- with a parenthesized control proving the operator itself is not being refused. is versus == is pinned because the tighter reading is a parse error rather than a different value, which still distinguishes the two groupings. The relation between concatenation and arithmetic is deliberately absent here: it is already the single obligation of REQ-0001, which SOL-TCK-0002 tests as `1 + 2 .. \"z\"`, and restating it under a second requirement would give one rule two owners.",
  quotes=["Operator precedence, from lowest to highest, is:"]),
 "REQ-1604": dict(
  section="3. Static and Strong Typing",
  summary="The logical operators short-circuit, so the right operand is not evaluated when the left operand already decides the result",
  kind="runtime",
  notes="Short-circuiting is pinned inside one program per operator by evaluating the shorting case and then the non-shorting case and printing a single marker afterwards, so the effect marker must occur exactly once in the whole stream. The non-shorting statement is what makes the pair meaningful: the shorting case alone is also satisfied by an implementation that never evaluates a right operand at all, so demanding exactly one occurrence across both cases forces the short-circuit to be conditional rather than permanent. Expected streams are derived from section 5's rule that print appends no separator.",
  quotes=["`&&` and `||` short-circuit"]),
 "REQ-1607": dict(
  section="3. Static and Strong Typing",
  summary="The logical operators require Boolean operands on both sides and unary ! requires a Boolean operand, so a non-Boolean operand on either side of && or || is a compile-time error",
  kind="compile-time",
  notes="The right-operand position is pinned deliberately by `true || 1`, which an implementation that checked only the operand it would actually evaluate might accept because the right operand never runs at runtime; the specification's requirement is static, so the operand must be rejected before evaluation is even considered. Rejections are bare because the invalid-operand code the implementation reports for these cases occurs zero times in the specification.",
  quotes=["`&&` and `||` short-circuit and require `Boolean` operands. Unary `!` requires `Boolean`."]),
 "REQ-1605": dict(
  section="3. Equality and reference identity",
  summary="Every equality or identity expression evaluates its left operand first and its right operand second exactly once each, and the negating forms negate the result without evaluating either operand again",
  kind="runtime",
  notes="Order is pinned by a program that runs both operand orderings so the marker stream must reverse with them, which a right-to-left or alphabetically-resolved evaluator cannot produce. Single evaluation is pinned separately for == and for !=, since != negates a completed comparison and is the form where a re-evaluation would most plausibly hide; the expected streams are derived from section 5's rule that print appends no separator.",
  quotes=["Every equality or identity expression evaluates its left operand first and its right operand second, exactly once each. `!=` and `!==` negate the corresponding positive operation without evaluating either operand again."]),
 "REQ-1606": dict(
  section="3. Equality and reference identity",
  summary="Equality is well typed only when one operand type is assignable to the other, so unrelated nominal classes are not directly comparable while an Any-typed operand or an explicit equals call is permitted",
  kind="compile-time",
  notes="The rule and both escape routes the specification itself names are asserted, because accepting the escapes without the prohibition would be satisfied by an untyped equality, and prohibiting the direct comparison without the escapes would be satisfied by an over-restrictive checker. The rejection is bare: section 3 says such operands are 'not directly comparable' and names no code, and the invalid-operand code the implementation prints appears nowhere in the specification.",
  quotes=["`left == right` and `left != right` are well typed only when one operand type is assignable to the other; the result type is `Boolean`.",
          "unrelated nominal classes are not directly comparable even though `equals` accepts `Any?`.",
          "A caller that intentionally wants an arbitrary comparison may use an `Any`-typed value or call `equals` explicitly."]),
}

# ---------------------------------------------------------------- programs
NEG = '\nprint("EXECUTED-INVALID")\n'

# Two classes with character-identical member declarations: a structural type system
# would consider them interchangeable, Solvik must not.
NOM = ('class A {\n'
       '    var v: Integer\n'
       '\n'
       '    A(n: Integer) {\n'
       '        this.v = n\n'
       '    }\n'
       '\n'
       '    func get(): Integer {\n'
       '        return this.v\n'
       '    }\n'
       '}\n'
       '\n'
       'class B {\n'
       '    var v: Integer\n'
       '\n'
       '    B(n: Integer) {\n'
       '        this.v = n\n'
       '    }\n'
       '\n'
       '    func get(): Integer {\n'
       '        return this.v\n'
       '    }\n'
       '}\n')
ASINGLE = 'class A {\n    var v: Integer\n\n    A(n: Integer) {\n        this.v = n\n    }\n\n' \
          '    func get(): Integer {\n        return this.v\n    }\n}\n'

S, EXPECT, CATEGORY, REQ_FOR = {}, {}, {}, {}


def add(tid, req, category, src, outcome, **exp):
    S[tid] = src
    CATEGORY[tid] = category
    REQ_FOR[tid] = req
    EXPECT[tid] = {"outcome": outcome, **exp}


# --- REQ-1600 nominal typing.
add("SOL-TCK-0206", "REQ-1600", "types",
    NOM + 'var a = A(7)\nvar b = B(8)\nprint("nom" .. a.get() .. b.get())\n',
    "SUCCESS", stdout="nom78")
add("SOL-TCK-0207", "REQ-1600", "types",
    NOM + 'var b = B(8)\nvar a: A = b\nprint(1)\n' + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1601 Any does not disable checking.
add("SOL-TCK-0208", "REQ-1601", "types",
    ASINGLE + 'var x: Any = A(1)\nprint(x.get())\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0209", "REQ-1601", "types",
    ASINGLE + 'var x: A = A(4)\nprint("own" .. x.get())\n', "SUCCESS", stdout="own4")
add("SOL-TCK-0210", "REQ-1601", "types",
    'var x: Any = "abc"\nvar n: Integer = x\nprint(n)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0211", "REQ-1601", "types",
    ASINGLE + 'var x: Any = A(9)\nvar a: A = x as A\nprint("cast" .. a.get())\n',
    "SUCCESS", stdout="cast9")
add("SOL-TCK-0212", "REQ-1601", "types",
    'var x: Any = 1\nprint(x + 1)\n' + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1602 assignment is a statement.
add("SOL-TCK-0213", "REQ-1602", "syntax",
    'var mutable a = 1\nvar mutable b = 2\nb = a\nprint("stmt" .. b)\n', "SUCCESS", stdout="stmt1")
add("SOL-TCK-0214", "REQ-1602", "syntax",
    'var mutable a = 1\nvar mutable b = 2\nb = (a = 3)\nprint(b)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0215", "REQ-1602", "syntax",
    'var mutable a = 1\nvar z = (a = 5)\nprint(z)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0216", "REQ-1602", "syntax",
    'var mutable a = 1\nif (a = 2) {\n    print(1)\n}\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0217", "REQ-1602", "syntax",
    'var mutable a = 1\nprint(a = 5)\n' + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1603 precedence tiers.
add("SOL-TCK-0218", "REQ-1603", "syntax",
    'print("neg" .. (-2 + 3))\n', "SUCCESS", stdout="neg1")
add("SOL-TCK-0219", "REQ-1603", "syntax",
    'print("mul" .. (2 * 3 + 4 * 5))\n', "SUCCESS", stdout="mul26")
add("SOL-TCK-0220", "REQ-1603", "syntax",
    'print("or" .. (true || false && false))\n', "SUCCESS", stdout="ortrue")
add("SOL-TCK-0221", "REQ-1603", "syntax",
    'print("not" .. (!false && false))\n', "SUCCESS", stdout="notfalse")
add("SOL-TCK-0222", "REQ-1603", "syntax",
    'print("is" .. (1 is Any == true))\n', "SUCCESS", stdout="istrue")
add("SOL-TCK-0223", "REQ-1603", "syntax",
    'var a: Integer? = 5\nprint(a ?? 1 == 2)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0224", "REQ-1603", "syntax",
    'var a: Integer? = 5\nprint("nn" .. ((a ?? 1) == 1))\n', "SUCCESS", stdout="nnfalse")

# --- REQ-1604 short-circuit; REQ-1607 Boolean operand requirements.
CF = 'func f(): Boolean {\n    print("c")\n    return true\n}\n'
add("SOL-TCK-0225", "REQ-1604", "evaluation",
    CF + 'var r = false && f()\nvar s = true && f()\nprint("and" .. r .. s)\n',
    # `false && f()` short-circuits (no call); `true && f()` must evaluate f, so exactly one
    # "c" appears. Zero occurrences would mean the right operand is never evaluated at all,
    # which the second statement is present to rule out.
    "SUCCESS", stdout="candfalsetrue")
add("SOL-TCK-0226", "REQ-1604", "evaluation",
    CF + 'var r = true || f()\nvar s = false || f()\nprint("or" .. r .. s)\n',
    # `true || f()` short-circuits and must not call f; `false || f()` cannot short-circuit
    # and must call it, so exactly one "c" is emitted and it precedes the final marker.
    "SUCCESS", stdout="cortruetrue")
add("SOL-TCK-0227", "REQ-1607", "evaluation",
    'print(1 && true)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0228", "REQ-1607", "evaluation",
    'print(true || 1)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0229", "REQ-1607", "evaluation",
    'print(!1)\n' + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1605 evaluation order and single evaluation.
add("SOL-TCK-0230", "REQ-1605", "evaluation",
    'func p(): Integer {\n    print("P")\n    return 1\n}\n\n'
    'func q(): Integer {\n    print("Q")\n    return 2\n}\n'
    'print(p() < q())\nprint(q() < p())\n',
    "SUCCESS", stdout="PQtrueQPfalse")
add("SOL-TCK-0231", "REQ-1605", "evaluation",
    'func h(): Integer {\n    print("h")\n    return 1\n}\nprint("eq" .. (h() == h()))\n',
    "SUCCESS", stdout="hheqtrue")
add("SOL-TCK-0232", "REQ-1605", "evaluation",
    'func k(): Integer {\n    print("k")\n    return 1\n}\nprint("ne" .. (k() != k()))\n',
    "SUCCESS", stdout="kknefalse")

# --- REQ-1606 semantic comparability.
add("SOL-TCK-0233", "REQ-1606", "equality",
    NOM + 'var a = A(1)\nvar b = B(1)\nprint(a == b)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0234", "REQ-1606", "equality",
    NOM + 'var a = A(1)\nvar b = B(1)\nvar q: Any = a\nprint("esc" .. (q == b))\n',
    "SUCCESS", stdout="escfalse")
add("SOL-TCK-0235", "REQ-1606", "equality",
    NOM + 'var a = A(1)\nvar b = B(1)\nprint("exp" .. a.equals(b))\n',
    "SUCCESS", stdout="expfalse")
add("SOL-TCK-0236", "REQ-1606", "equality",
    ASINGLE + 'var a = A(1)\nvar q: Any = a\nprint("pcp" .. (a == q))\n',
    "SUCCESS", stdout="pcptrue")


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
        man = {"manifestSchemaVersion": 1, "specVersion": "2026.11-draft", "testId": tid,
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
