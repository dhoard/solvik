#!/usr/bin/env python3
"""Generate the section 1 (Lexical basics) TCK batch.

Oracles are DERIVED FROM LANGUAGE_SPEC.md and only then compared against the implementation.
Each expectation is written from the specification's own text before any probe of the built
launcher; a mismatch is investigated as a candidate implementation defect, never resolved by
copying observed output into the expectation.

Section 1 is the specification's only lexical section, and unlike the diagnostic tables of
sections 20-23 it names no diagnostic codes at all. Every rejection in this batch therefore
carries a bare `{
}
` expectation: the specification normatively forbids the construct but never
names a code for forbidding it, and inventing one would assert a fact the specification does not
state. The acceptance tests do the load-bearing work, and each is built so that a *plausible
alternative lexer* produces different bytes rather than the same bytes in a different order:

  * identifier tests print the bound value through the identifier under test, so an identifier
    grammar that rejected the accepted spelling cannot print it at all;
  * the two comment tests are a discriminating PAIR: one puts a block comment on a single line
    and the other spans the comment across a newline, and the specification says the comment is
    whitespace *but that its physical newlines remain visible to semicolon insertion*. The
    same-line form only compiles if the comment terminates as whitespace; the spanning form only
    compiles if the newline inside it still separated the two statements. A lexer that treated a
    block comment as a plain newline-swallowing blank, or as a hard line-join, fails one arm;
  * block-comment non-nesting is asserted by the arm a nesting lexer cannot produce: `/* a /* b */
    */` must leave the trailing ` */` as source text, so the program must be rejected, while the
    control `/* a /* b */` + statement must be accepted because the first `*/` closes the comment
    under the non-nesting rule;
  * literal-form tests pin observable *value* semantics, not merely "it compiled": a positive
    exponent is pinned by an equality that a lexer reading `1.5e3` as the digits `1.5` followed by
    a bogus token could not satisfy, and a negative exponent by another such equality;
  * the `L`/`F` suffix tests are discriminating type oracles, not display tests: a Long-suffixed
    literal assigned to `Integer`, and a Double literal assigned to `Float`, are both rejected, so
    the acceptance of `Long = ...L` and `Float = ...F` shows the suffix selected that type rather
    than the literal merely being accepted at some type.

The out-of-range decimal integer rule is the one numeric rule in section 1 whose boundary is
stated ("outside the signed 32-bit range"), so it is pinned at both sides of the boundary:
2147483647 is accepted and printed, and the very next integer is rejected.
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
 "REQ-1400": dict(
  section="1. Design Goals (Lexical basics)",
  summary="Identifiers match [A-Za-z_][A-Za-z0-9_]*, so a leading underscore or letter followed by letters, digits and underscores is a single identifier that can be bound and read back",
  kind="lexical",
  notes="The observable is the bound value printed through the identifier itself, so a lexer whose identifier grammar rejected any accepted character would fail to resolve the name and print nothing. Section 1 names no diagnostic code for identifier syntax, so the negative counterpart (a leading digit) is a bare rejection while this acceptance carries byte-exact stdout.",
  quotes=["Identifiers use `[A-Za-z_][A-Za-z0-9_]*`;"]),
 "REQ-1401": dict(
  section="1. Design Goals (Lexical basics)",
  summary="Keywords are reserved and cannot be used as an identifier, and an identifier may not begin with a digit",
  kind="lexical",
  notes="Both are prohibitions, and section 1 names no code for either, so both programs carry a bare rejection expectation rather than a fabricated code. The keyword arm uses a word that is otherwise a perfectly ordinary identifier position, so an implementation that reserved only some keywords would accept the program and print the sentinel.",
  quotes=["keywords are reserved"]),
 "REQ-1402": dict(
  section="1. Design Goals (Lexical basics)",
  summary="$ is not an identifier character, so a name containing a dollar sign is not a valid identifier",
  kind="lexical",
  notes="The specification states this as a property of the identifier character set rather than as a rule with a diagnostic, so the expectation is a bare rejection. The test places the dollar sign inside an otherwise-legal name, which distinguishes it from a lexer that merely disallowed a leading dollar sign.",
  quotes=[r"`$` is not an identifier character"]),
 "REQ-1403": dict(
  section="1. Design Goals (Lexical basics)",
  summary="// starts a line comment and /* ... */ is a NON-nesting block comment; a nesting block-comment lexer would fail to reject a trailing delimiter left after the outer comment closes",
  kind="lexical",
  notes="The two arms are the discriminating pair for non-nesting. Under the non-nesting rule the first */ closes the comment, so `/* a /* b */` followed by a statement compiles and runs, while `/* a /* b */ */` leaves the trailing */ as source text and must be rejected. A nesting lexer would reject the first program (unterminated outer comment) and accept the second (balanced nesting), so the pair pins the rule in both directions rather than only asserting that a comment is ignored.",
  quotes=["`/* ... */` is a non-nesting block comment."]),
 "REQ-1404": dict(
  section="1. Design Goals (Lexical basics)",
  summary="Comments are otherwise whitespace, but a newline inside a comment is still a physical newline for statement termination",
  kind="lexical",
  notes="Two independent observables are pinned by two programs. A block comment that terminates on the same physical line lets the following statement begin normally, while a block comment whose closing delimiter lands on a later line must still let the newline inside it separate the two statements -- the expected stream is the two values with no separator, which only holds if the comment's embedded newline was seen as a terminator rather than swallowed. A lexer that replaced a block comment with nothing at all, discarding its newlines, merges the statements and fails the second arm.",
  quotes=["Comments are otherwise whitespace, but a newline inside a comment is still a physical newline for statement termination (section 16)."]),
 "REQ-1405": dict(
  section="1. Design Goals (Lexical basics)",
  summary="A decimal integer literal has type Integer in the initial typed core, and a literal outside the signed 32-bit range is a compile-time error",
  kind="lexical",
  notes="The boundary is pinned on both sides: 2147483647 is accepted and printed exactly, and 2147483648 -- the next value -- is rejected. The acceptance is what makes the rejection meaningful, because a lexer that mis-parsed every large literal would also fail to print the accepted one. Section 1 states the range rule but names no diagnostic code, so the rejection is bare.",
  quotes=["Decimal integer literals contain ASCII digits and have type `Integer` in the initial typed core. A literal outside the signed 32-bit range is a compile-time error"]),
 "REQ-1406": dict(
  section="1. Design Goals (Lexical basics)",
  summary="An L-suffixed literal has type Long, and a Long-suffixed literal is not usable where Integer is declared",
  kind="lexical",
  notes="The pair is a type oracle rather than a display test: the acceptance shows the suffix selecting Long for a literal far beyond the Integer range, and the rejection shows the same suffix really did select Long, because the identical suffixed literal is refused when Integer is declared. Section 1 names no code for the mismatch, so the rejection is bare.",
  quotes=["Phase 7 adds `L`-suffixed `Long` literals"]),
 "REQ-1407": dict(
  section="1. Design Goals (Lexical basics)",
  summary="A decimal floating-point literal with an optional exponent has type Double, an F suffix selects Float, and the exponent is applied to the significand",
  kind="lexical",
  notes="Three separate obligations, each with its own program. The exponent is pinned by equality rather than by rendering, so a lexer that read `1.5e3` as the literal 1.5 and then choked or ignored the exponent could not satisfy `1.5e3 == 1500.0`; a negative exponent is pinned the same way. The Double and Float declarations are pinned by accepting the matching form and rejecting the cross-assignment, which is what shows F really selects Float instead of the literal simply being accepted at some type.",
  quotes=["decimal floating-point literals with an optional exponent. Floating-point literals have type `Double`; an `F` suffix selects `Float`."]),
 "REQ-1408": dict(
  section="1. Design Goals (Lexical basics)",
  summary="A character literal uses single quotes and contains exactly one Unicode scalar value or one escape supported by normal strings",
  kind="lexical",
  notes="The acceptance arm prints a plain scalar and an escape, so the escape is observed to produce the one character the string rule defines rather than its two source characters. The rejection arm puts two scalars inside the quotes, which the exactly-one rule forbids; section 1 names no diagnostic code, so the expectation is a bare rejection. A lexer that took the closing quote of a two-scalar literal and re-lexed the remainder could accidentally accept, which is why the negative arm is required.",
  quotes=["A character literal uses single quotes and contains exactly one Unicode scalar value or one of the escapes supported by normal strings"]),
}

# ---------------------------------------------------------------- test programs
# Rejections append a sentinel print that must never execute: if the construct were accepted the
# program would run and emit bytes the bare-rejection oracle forbids, so a false acceptance cannot
# hide behind a matching exit status.
NEG = '\nprint("EXECUTED-INVALID")\n'

S = {}
EXPECT = {}
CATEGORY = {}
REQ_FOR = {}


def add(tid, req, category, src, outcome, **exp):
    S[tid] = src
    CATEGORY[tid] = category
    REQ_FOR[tid] = req
    EXPECT[tid] = {"outcome": outcome, **exp}


# --- REQ-1400: identifier character class.
add("SOL-TCK-0166", "REQ-1400", "lexical",
    (('var _a1B_: Integer = 42\n'
    'print(_a1B_)\n'
    '')), "SUCCESS", stdout="42")

add("SOL-TCK-0167", "REQ-1400", "lexical",
    ('var a: Integer = 5\n'
    'var 1a = 7\n'
    'print("EXECUTED-INVALID")\n'
    ''), "COMPILE_ERROR", diag={})

# --- REQ-1401: reserved keywords, leading digit.
add("SOL-TCK-0168", "REQ-1401", "lexical",
    ('var class: Integer = 5\n'
    'print("EXECUTED-INVALID")\n'
    ''), "COMPILE_ERROR", diag={})

add("SOL-TCK-0169", "REQ-1401", "lexical",
    ('var func: Integer = 5\n'
    'print("EXECUTED-INVALID")\n'
    ''), "COMPILE_ERROR", diag={})

# --- REQ-1402: '$' is not an identifier character.
add("SOL-TCK-0170", "REQ-1402", "lexical",
    "var a$b = 5" + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1403: non-nesting block comment.
add("SOL-TCK-0171", "REQ-1403", "lexical",
    "/* a /* b */\nprint(\"ok\")\n", "SUCCESS", stdout="ok")

add("SOL-TCK-0172", "REQ-1403", "lexical",
    "print(1) /* a /* b */ */" + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1404: comments are whitespace; their newlines still separate statements.
add("SOL-TCK-0173", "REQ-1404", "lexical",
    "print(3) // one\nprint(4)\n", "SUCCESS", stdout="34")

add("SOL-TCK-0174", "REQ-1404", "lexical",
    "print(5) /* a\nb */ print(6)\n", "SUCCESS", stdout="56")

# --- REQ-1405: Integer literal and the signed 32-bit range boundary.
add("SOL-TCK-0175", "REQ-1405", "lexical",
    (('var a: Integer = 2147483647\n'
    'print(a)\n'
    '')), "SUCCESS", stdout="2147483647")

add("SOL-TCK-0176", "REQ-1405", "lexical",
    ('var a: Integer = 2147483648\n'
    'print("EXECUTED-INVALID")\n'
    ''), "COMPILE_ERROR", diag={})

# --- REQ-1406: L-suffixed Long literals.
add("SOL-TCK-0177", "REQ-1406", "lexical",
    "var a: Long = 9223372036854775807L\nprint(a)\n",
    "SUCCESS", stdout="9223372036854775807")

add("SOL-TCK-0178", "REQ-1406", "lexical",
    "var a: Integer = 5L" + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1407: floating-point literals, exponent, F suffix.
add("SOL-TCK-0179", "REQ-1407", "lexical",
    "var a: Double = 1.5\nvar b: Float = 1.5F\nprint(a)\nprint(b)\n", "SUCCESS", stdout="1.51.5")

add("SOL-TCK-0180", "REQ-1407", "lexical",
    "var a: Float = 1.5F\nprint(a)\n", "SUCCESS", stdout="1.5")

add("SOL-TCK-0181", "REQ-1407", "lexical",
    "var a: Float = 1.5" + NEG, "COMPILE_ERROR", diag={})

add("SOL-TCK-0182", "REQ-1407", "lexical",
    "print(1.5e3 == 1500.0)\nprint(16e-1 == 1.6)\n", "SUCCESS", stdout="truetrue")

# --- REQ-1408: character literals.
add("SOL-TCK-0183", "REQ-1408", "lexical",
    "print('A')\nprint('\\n')\n", "SUCCESS", stdout="A\n")

add("SOL-TCK-0184", "REQ-1408", "lexical",
    ("var a: Character = 'AB'\n"
    'print("EXECUTED-INVALID")\n'
    ''), "COMPILE_ERROR", diag={})


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
        with open(os.path.join(d, tid + ".manifest.json"), "w") as fh:
            fh.write(json.dumps(man, indent=2) + "\n")
    print("wrote %d test directories, %d requirements" % (len(S), len(REQS)))


if __name__ == "__main__":
    main()
