#!/usr/bin/env python3
"""Generate the section 16 TCK batch: Go-style lexical semicolon insertion.

Section 16 is the most rule-dense section in the specification and, before this
batch, the least covered. Every rule here is a statement about when a synthetic
SEMI is or is not emitted, and each is observable only through whether a program
with a particular newline placement parses. Oracles are DERIVED FROM
LANGUAGE_SPEC.md's insertion conditions and only then compared with the
implementation.

Code-pinning. NO diagnostic code is named anywhere in section 16, and the codes
the implementation prints for these cases (`SOLV-PARS-001`, `SOLV-TYPE-010`,
`SOLV-SEM-003`) either occur zero times in the specification or are named only for
unrelated rules. Every rejection in this batch is therefore asserted BARE: the
oracle requires a compile-time error and asserts nothing about its code, because
the specification forces no code and inventing one would transcribe an
implementation's choice into the conformance suite.

Discriminating designs. Each rule is isolated by a program pair that differs in
exactly the one property the rule turns on, so a differing implementation fails an
arm rather than agreeing by accident:

  * Condition 1 (paren/bracket depth). `f(1\n + 2)` is accepted while `val v = 1\n
     + 2` is rejected. The two have identical tokens around the newline (a literal
     precedes it, `+` follows it); the ONLY difference is that in the first the
     newline sits at unmatched-paren depth one. That single-token difference is the
     whole depth condition, so an implementation that inserted on the preceding
     token alone, ignoring depth, rejects the accepted arm.

  * Condition 2 (the preceding token must be a terminator-triggering token).
     `val b = a +\n 1` is accepted while `val b = a\n + 1` is rejected. Both are at
     depth zero; they differ only in which side of the newline the `+` sits. On the
     accepted side the token before the newline is an operator (not in the
     triggering list), so no SEMI is emitted and the expression continues; on the
     rejected side it is the identifier `a` (a triggering token) with a non-exception
     token after it, so a SEMI is emitted and `+ 1` is orphaned. A leading `*` is
     rejected for the same reason, which is what "do not use general
     JavaScript-style heuristics" makes testable: an implementation carrying over
     JS's operator-continuation intuition would accept both leading-operator forms.

  * Condition 3 (the next token may not be `.`, `?.`, or `else`). A leading-dot
     member chain, a leading-`?.` chain, and an `else` on its own line are each
     accepted. Each is accepted ONLY because insertion is suppressed for that one
     lookahead token; drop any one exception and that program gains a stray SEMI.
     A leading-dot chain and a leading-`?.` chain are tested separately because the
     specification lists them as two distinct exceptions.

  * Comment newlines are physical. `val a = 1 /* c\n*/ print(...)` is accepted
     where `val a = 1 print(...)` (no newline anywhere) is rejected. The two are
     identical except for a newline inside a block comment, so the accepted arm can
     only parse if the token-stream stage treats a newline contained in a comment as
     a physical newline -- the specification states this and the pair is its only
     direct evidence. A line-comment newline is accepted on the same principle.

  * `return` followed by a newline terminates the return. A value-returning function
     that puts the value on the next line is rejected, while `return 7` is accepted.
     Stronger, a void function with a bare `return` followed by `print("after")` on
     the next line runs the print only if the return terminated -- and it does not
     run it, because the return already left the function. The expected bytes are
     therefore "in" "done" with no "after", which is the exact opposite of a no-ASI
     reading that would swallow the print as the return value. This arm is the
     specification's "do not copy JavaScript's automatic semicolon insertion".

  * Blank lines and end of file. Consecutive blank lines between statements are
     accepted without duplicate-SEMI failure, and a final statement with no trailing
     newline is accepted, exercising the "at end of file apply the same rule without
     a next-token exception" clause.

  * `include` termination. A top-level include is terminated by an inserted SEMI
     exactly like a statement -- newline-terminated and raw-string-path forms are
     accepted, and an include with no separator at all is rejected. Section 16 says
     explicitly that no include-specific termination rule exists, so these are
     ordinary-insertion results and the rejection's bare expectation is shared with
     the other termination rejections.
"""
import base64, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.10-draft")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

# The included helper, shared by every include-termination test. It declares one
# function so a terminated include is observable by calling through it: if the
# directive were not terminated, the following statement would not parse at all.
LIB = "func two(): Integer {\n    return 2\n}\n"

REQS = {
 "REQ-1900": dict(
  section="16. Statement Termination",
  summary="A top-level statement must end with an explicit or inserted SEMI: newline-separated and explicitly-semicolon-terminated declarations are both accepted, and a statement with no terminator at all is a compile-time error",
  kind="compile-time",
  notes="REQ-0403 already owns the equivalence of the two terminators (an explicit `;` and an inserted one introduce bindings the same way). This requirement owns the distinct necessity obligation the specification's insertion conditions force: with no physical newline and no explicit `;`, no SEMI is emitted and the following token is orphaned, so the program is a compile-time error. The two accepted arms are the controls that keep the rejection from being satisfied by an implementation that rejects every multi-statement program. Bare rejection: section 16 names no diagnostic code, and the parse code the implementation prints occurs zero times in the specification.",
  quotes=["Programmers may explicitly write `;`, but normal style uses newlines.",
          "Explicit `;` and synthesized semicolons must both become the parser's `SEMI` token."]),
 "REQ-1901": dict(
  section="16. Statement Termination",
  summary="Semicolon insertion is suppressed while the unmatched `(` and `[` nesting depth is nonzero, so a newline inside an unclosed parenthesis does not terminate the expression",
  kind="compile-time",
  notes="The pair `f(1\\n + 2)` (accepted) and `val v = 1\\n + 2` (rejected) has identical tokens around the newline -- a literal before it, `+` after it, at the start of a declaration -- and differs only in the unmatched-paren depth at the newline. That isolates condition 1 exactly: an implementation that emitted a SEMI on the preceding-token rule while ignoring depth would reject the accepted arm, and one that never inserted after a literal would accept the rejected arm. Bare rejection.",
  quotes=["the unmatched `(` and `[` nesting depths are both zero;",
          "Expressions continue naturally after operators and commas:"]),
 "REQ-1902": dict(
  section="16. Statement Termination",
  summary="Insertion happens only when the token before the newline is a terminator-triggering token, so an expression continues after a trailing operator or comma, while a newline before an operator (identifier before it, operator after) inserts a SEMI and is a compile-time error",
  kind="compile-time",
  notes="`val b = a +\\n 1` (accepted) and `val b = a\\n + 1` (rejected) are both at depth zero and differ only in which side of the newline the `+` sits: on the accepted side the preceding token is an operator (not a triggering token) so no SEMI is emitted; on the rejected side it is the identifier `a` so a SEMI is emitted and `+ 1` is orphaned. A second rejection uses `*` rather than `+` to show the rule is not about any one operator. Together these are the direct evidence for 'do not use general JavaScript-style heuristics': an implementation that carried over JS operator-continuation would accept both leading-operator programs. Bare rejections.",
  quotes=["the preceding significant token is an identifier, a literal, `break`, `continue`, `return`, `)`, `]`, or `}`;",
          "do not use general JavaScript-style heuristics."]),
 "REQ-1903": dict(
  section="16. Statement Termination",
  summary="Insertion is suppressed when the token after the newline is `.`, `?.`, or `else` -- the only member-chain lookahead exceptions -- so a leading-dot chain, a leading-safe-call chain, and an `else` on its own line all parse",
  kind="compile-time",
  notes="Each accepted program is accepted ONLY because insertion is suppressed for one specific next-token; removing the suppression for `.`, for `?.`, or for `else` respectively makes that program gain a stray SEMI and fail. The dot and safe-call chains are separate programs because the specification lists them as two exceptions, and the else is tested both as a statement and as the tail of an if-expression. Bare expectations are not needed here: every arm is an acceptance whose stdout is the oracle.",
  quotes=["the next significant token is not `.`, `?.`, or `else`.",
          "The semicolon-inserting token stream must suppress insertion when the next significant token is `.` or `?.`. This is the only member-chain lookahead exception; do not use general JavaScript-style heuristics.",
          "Solvik supports TypeScript/Kotlin-style leading-dot chains:"]),
 "REQ-1904": dict(
  section="16. Statement Termination",
  summary="A newline contained in a line comment or a block comment is treated as a physical newline, so a statement can be terminated by a newline that appears only inside a comment",
  kind="compile-time",
  notes="`val a = 1 /* c\\n*/ print(...)` is accepted while the identical program with the comment-newline removed, `val a = 1 print(...)`, is rejected (that rejection is REQ-1900's arm and the contrast is documented in the plan). The accepted arm can only parse if the token-stream stage treats the newline inside the block comment as the physical newline that terminates `val a = 1`; a stage that ignored comment interiors would see no terminator and reject it. A line-comment newline is accepted on the same principle. This is the only direct evidence for the clause, since a portable oracle cannot inspect the token stream.",
  quotes=["The lexer must preserve physical newline information. The token-stream stage ignores spaces and comments but treats a newline contained in a line comment or block comment as a physical newline."]),
 "REQ-1905": dict(
  section="16. Statement Termination",
  summary="`return` followed by a newline terminates the return statement, so a value placed on the next line is not the returned value and a bare return leaves the function before any following statement",
  kind="compile-time",
  notes="The rejection is a value-returning function whose value sits on the line after `return`, so the return is terminated empty and the function returns no value -- the specification's own worked example. The control `return 7` on one line is accepted. The strongest arm is a value-less function with `return` then `print(\"after\")` on the next line: the observed bytes are 'in' then 'done' with NO 'after', which proves the return terminated the function at the newline (a no-ASI reading would swallow the print as the return value and emit 'after'). That expected output is the direct embodiment of 'do not copy JavaScript's automatic semicolon insertion behavior', and it fails in the safe direction -- an implementation that did copy JS would be caught by the extra 'after'. Bare rejection.",
  quotes=["`return` followed by a newline terminates the return statement:",
          "Do not copy TypeScript's unsound `any` behavior or JavaScript's automatic semicolon insertion behavior."]),
 "REQ-1906": dict(
  section="16. Statement Termination",
  summary="Consecutive blank lines between statements must not emit duplicate semicolons, and at end of file the same insertion rule applies without the next-token exception",
  kind="compile-time",
  notes="The blank-lines program has two consecutive blank lines between two declarations and a trailing blank line before the final statement; if a SEMI were emitted per newline the parser would see empty statements it rejects, so acceptance is the evidence for the no-duplicate clause. The end-of-file program ends after a call with no trailing newline at all, exercising the EOF rule (the final statement is terminated by end of file, not by a following token). Both are accepted and the observable is the printed bytes, so no code assertion is needed.",
  quotes=["Consecutive blank lines must not emit duplicate semicolons.",
          "At end of file, apply the same rule without a next-token exception."]),
 "REQ-1907": dict(
  section="16. Statement Termination",
  summary="A top-level include directive ends with an explicit or inserted SEMI exactly like a statement -- its path is a string or raw-string literal so a following physical newline terminates it -- and an include with no separator is a compile-time error",
  kind="compile-time",
  notes="Two accepted arms terminate an include by a plain newline, one with a normal string path and one with a raw-string path, each observable by calling a function declared in the included file -- if the directive were not terminated, the following call statement could not parse. The rejected arm omits any terminator so the include and the next statement collide. The section states explicitly that no include-specific termination rule exists, so the rejection is an ordinary insertion failure and is asserted bare like the other termination rejections; the included helper is the same for every arm so only the terminator differs.",
  quotes=["A top-level `include` directive (section 20) ends with an explicit or inserted `SEMI` exactly like a statement.",
          "Solvik uses Go-style lexical semicolon insertion."]),
}

NEG = '\nprint("EXECUTED-INVALID")\n'
S, EXPECT, CATEGORY, REQ_FOR, EXTRA = {}, {}, {}, {}, {}


def add(tid, req, category, src, outcome, libs=None, **exp):
    S[tid] = src
    CATEGORY[tid] = category
    REQ_FOR[tid] = req
    EXPECT[tid] = {"outcome": outcome, **exp}
    if libs:
        EXTRA[tid] = libs


def succ(tid, req, src, out):
    add(tid, req, "syntax", src, "SUCCESS", stdout=out)


def rej(tid, req, src, libs=None):
    add(tid, req, "syntax", src, "COMPILE_ERROR", libs=libs, diag={})


# --- REQ-1900: a statement must end with an explicit or inserted SEMI.
succ("SOL-TCK-0286", "REQ-1900",
     'val a = 1\nval b = 2\nprint("n" .. a .. b)\n', "n12")
succ("SOL-TCK-0287", "REQ-1900",
     'val a = 1;\nval b = 2;\nprint("x" .. a .. b)\n', "x12")
rej("SOL-TCK-0288", "REQ-1900",
    'val a = 1 print("a" .. a)\n' + NEG)

# --- REQ-1901: insertion is suppressed at nonzero paren depth.
succ("SOL-TCK-0289", "REQ-1901",
     'func f(x: Integer): Integer {\n    return x\n}\nval v = f(1\n    + 2)\nprint("v" .. v)\n', "v3")
rej("SOL-TCK-0290", "REQ-1901",
    'val v = 1\n    + 2\nprint("v" .. v)\n' + NEG)

# --- REQ-1902: insertion only after a triggering token; operators/commas continue.
succ("SOL-TCK-0291", "REQ-1902",
     'val a = 1\nval b = a +\n    1\nprint("b" .. b)\n', "b2")
succ("SOL-TCK-0292", "REQ-1902",
     'class C {\n    C(x: Integer, y: Integer) {\n        this.sum = x + y\n    }\n\n    val sum: Integer\n}\n'
     'val c = C(1,\n    2)\nprint("r" .. c.sum)\n', "r3")
rej("SOL-TCK-0293", "REQ-1902",
    'val a = 1\nval b = a\n    + 1\nprint("b" .. b)\n' + NEG)
rej("SOL-TCK-0294", "REQ-1902",
    'val a = 2\nval b = a\n    * 3\nprint("b" .. b)\n' + NEG)

# --- REQ-1903: the `.`, `?.`, `else` lookahead exceptions.
succ("SOL-TCK-0295", "REQ-1903",
     'class S { S() {\n    }\n\n    func load(): S {\n        return this\n    }\n\n'
     '    func value(): Integer {\n        return 42\n    }\n}\n'
     'val s = S()\nval r = s\n    .load()\n    .value()\nprint("r" .. r)\n', "r42")
succ("SOL-TCK-0296", "REQ-1903",
     'class W { W() {\n    }\n\n    func opt(): String? {\n        return "z"\n    }\n}\n'
     'val w = W()\nval v = w.opt()\n    ?.hashCode()\nprint("v" .. (v != null))\n', "vtrue")
succ("SOL-TCK-0297", "REQ-1903",
     'val c = false\nif (c) {\n    print("y")\n}\nelse {\n    print("n")\n}\n', "n")
succ("SOL-TCK-0298", "REQ-1903",
     'val c = true\nval r = if (c) { 1 }\nelse { 2 }\nprint("r" .. r)\n', "r1")

# --- REQ-1904: a newline inside a comment is a physical newline.
succ("SOL-TCK-0299", "REQ-1904",
     'val a = 1 /* c\n*/ print("bc" .. a)\n', "bc1")
succ("SOL-TCK-0300", "REQ-1904",
     'val a = 1 // note\nprint("lc" .. a)\n', "lc1")

# --- REQ-1905: `return` followed by a newline terminates the return.
rej("SOL-TCK-0301", "REQ-1905",
    'func f(): Integer {\n    return\n    7\n}\nprint("v" .. f())\n' + NEG)
succ("SOL-TCK-0302", "REQ-1905",
     'func f(): Integer {\n    return 7\n}\nprint("v" .. f())\n', "v7")
# The bare-return-in-void function: "in" prints, the return leaves the function at the
# newline, so "after" is unreachable and must NOT appear. A JS-ASI reading emits "after".
succ("SOL-TCK-0303", "REQ-1905",
     'func f() {\n    print("in")\n    return\n    print("after")\n}\nf()\nprint("done")\n',
     "indone")

# --- REQ-1906: no duplicate semicolons across blank lines; EOF applies the rule.
succ("SOL-TCK-0304", "REQ-1906",
     'val a = 1\n\n\nval b = 2\n\nprint("z" .. a .. b)\n', "z12")
add("SOL-TCK-0305", "REQ-1906", "syntax",
    'val a = 1\nval b = 2\nprint("e" .. a .. b)', "SUCCESS", stdout="e12")

# --- REQ-1907: an include is terminated by an inserted SEMI like any statement.
LIBDIR = {"lib/m.sol": LIB}
add("SOL-TCK-0306", "REQ-1907", "syntax",
    'include "lib/m.sol"\nprint("nl" .. two())\n', "SUCCESS", libs=LIBDIR, stdout="nl2")
add("SOL-TCK-0307", "REQ-1907", "syntax",
    'include r"lib/m.sol"\nprint("rw" .. two())\n', "SUCCESS", libs=LIBDIR, stdout="rw2")
add("SOL-TCK-0308", "REQ-1907", "syntax",
    'include "lib/m.sol" print("v" .. two())\n' + NEG,
    "COMPILE_ERROR", libs=LIBDIR, diag={})


def verify():
    bad = []
    for rid, r in REQS.items():
        for q in r["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append(("QUOTE", rid, q))
    # The end-of-file arm must genuinely lack a trailing newline, or it stops testing
    # the EOF clause while still looking like the same test.
    if S["SOL-TCK-0305"].endswith("\n"):
        bad.append(("SHAPE", "REQ-1906", "end-of-file arm ends with a newline"))
    # The comment arm must genuinely have its ONLY newline inside the comment, else it
    # no longer isolates the physical-newline-in-comment rule.
    s = S["SOL-TCK-0299"]
    body = s.replace("/* c\n*/", "/*c*/")
    if "\n" in body.rstrip("\n"):
        bad.append(("SHAPE", "REQ-1904", "comment arm has a newline outside the comment"))
    # The include rejections must fail on termination, not on a missing file: the same
    # helper must be present so the only difference from the accepted arms is the
    # terminator.
    if not EXTRA.get("SOL-TCK-0308"):
        bad.append(("SHAPE", "REQ-1907", "include rejection has no lib/ to include"))
    # The REQ-1901 pair must differ only by the parentheses, which is what isolates the
    # depth condition; assert the two programs share their insertion-relevant tokens.
    if "+ 2" not in S["SOL-TCK-0289"] or "+ 2" not in S["SOL-TCK-0290"]:
        bad.append(("SHAPE", "REQ-1901", "depth pair no longer shares its operand"))
    return bad


def main():
    bad = verify()
    if bad:
        for kind, rid, detail in bad:
            print("%s %s: %r" % (kind, rid, detail))
        sys.exit(1)
    print("all %d normative quotes verified verbatim"
          % sum(len(r["quotes"]) for r in REQS.values()))
    for tid, src in sorted(S.items()):
        d = os.path.join(CORPUS, tid)
        os.makedirs(d, exist_ok=True)
        open(os.path.join(d, "main.sol"), "w").write(src)
        for rel, text in EXTRA.get(tid, {}).items():
            sub = os.path.join(d, os.path.dirname(rel))
            if sub:
                os.makedirs(sub, exist_ok=True)
            open(os.path.join(d, rel), "w").write(text)
        e = EXPECT[tid]
        man = {"manifestSchemaVersion": 1, "specVersion": "2026.10-draft", "testId": tid,
               "category": CATEGORY[tid], "profile": "full-language", "status": "required",
               "requirements": [REQ_FOR[tid]], "entryPoint": "main.sol",
               # A test with an included helper must declare the whole directory as its
               # fixture root: the isolation layer stages only `fixtureRoot` (or the bare
               # entry file when it is absent), so an include of lib/m.sol resolves unless
               # the sibling is staged too. This is the same field the existing 14
               # include/module tests set.
               **({"fixtureRoot": "."} if EXTRA.get(tid) else {}),
               "outcome": e["outcome"],
               "expectation": ({"languageExit": 0, "stdoutBase64":
                                base64.b64encode(e["stdout"].encode()).decode()}
                               if e["outcome"] == "SUCCESS" else {"diagnostic": e["diag"]})}
        open(os.path.join(d, tid + ".manifest.json"), "w").write(
            json.dumps(man, indent=2) + "\n")
    print("wrote %d test directories, %d requirements" % (len(S), len(REQS)))


if __name__ == "__main__":
    main()
