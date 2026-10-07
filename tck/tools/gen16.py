#!/usr/bin/env python3
"""Generate the section 16 TCK batch: physical-line statement termination.

Section 16 is the most rule-dense section in the specification and, before this
batch, the least covered. Every rule here is a statement about where a physical
line ends and whether a construct may continue across one, and each is observable
only through whether a program with a particular newline placement parses.
Oracles are DERIVED FROM LANGUAGE_SPEC.md's termination and continuation rules and
only then compared with the specification's own examples.

Code-pinning. Section 16 names exactly one diagnostic code, `SOLV-PARS-012`,
and names it for the semicolon rule: a `;` followed by another physical line,
end of file, or a stand-alone closing brace is rejected at the semicolon. Those
rejections are asserted CODED. NO other code is named in section 16 for
termination or continuation, and the codes the implementation prints for the
other cases (`SOLV-PARS-001`, `SOLV-TYPE-010`, `SOLV-SEM-003`) either occur zero
times in the specification or are named only for unrelated rules, so every other
rejection in this batch stays asserted BARE: the oracle requires a compile-time
error and asserts nothing about its code, because the specification forces no
code and inventing one would transcribe an implementation's choice into the
conformance suite. The brace-placement rules (SOLV-PARS-007 through
SOLV-PARS-010) are asserted by the later brace-layout batch, not here; this
batch owns termination itself.

Discriminating designs. Each rule is isolated by a program pair that differs in
exactly the one property the rule turns on, so a differing implementation fails an
arm rather than agreeing by accident:

  * The semicolon separates, it does not terminate. Newline-separated and
    same-line-semicolon-separated programs run, while two constructs sharing a
    line with NO separator between them are a compile-time error and a `;` that
    ends a physical line, ends the file, or precedes a stand-alone closing brace
    is rejected at the semicolon with the code section 16 names, `SOLV-PARS-012`.
    The three rejections are the direct evidence that `;` is only ever the
    separator between two constructs of one physical line: each arm removes what
    the semicolon should have separated while keeping the rest of the program
    legal.

  * Continuation is grammar, not lookahead. A call whose arguments sit on their
    own lines (`f(
 1,
 2,
)`) and `var b = a +
 1` are accepted while
    `var v = 1
 + 2`, `var b = a
 + 1` and `var b = a
 * 3` are rejected. The
    accepted programs break only at positions the grammar admits (after `(`,
    after a comma, after a binary operator); the rejected programs break a line
    that had already ended, so the operator line is orphaned. Nesting depth is
    irrelevant: a newline before an operator is an error inside parentheses
    exactly as it is at depth zero. A leading `*` is rejected for the same reason
    as a leading `+`: an implementation carrying over JavaScript's
    operator-continuation intuition would accept both leading-operator forms.

  * Member chains continue. A leading-dot chain and a leading-`?.` chain are
    separate accepted programs because the specification lists two member
    suffixes, and each is accepted ONLY because a line ending in that suffix
    cannot end; drop the grammar rule for one suffix and that program breaks.

  * `else` on its own line. A statement `if`/`else` and an expression `if` whose
    `else` begins a physical line both parse -- the canonical clause layout of
    section 16, and the exact opposite of the pre-revision one-line `
    }
    else {
        `.

  * Comment newlines are physical. `var a = 1 /* c
*/ print(...)` is accepted
    where the same program with the comment-newline removed, `var a = 1
    print(...)`, is rejected by the no-separator rule; the accepted arm can only
    parse if the newline inside the block comment ended the first statement's
    line. A line-comment newline is accepted on the same principle.

  * `return` followed by a newline terminates the return. A value-returning
    function that puts the value on the next line is rejected, while `return 7`
    is accepted. Stronger, a void function with a bare `return` followed by
    `print("after")` on the next line: "after" must NOT appear, because the bare
    return already left the function. Those expected bytes are the exact opposite
    of a JavaScript-ASI reading, which would swallow the print as the return
    value; the specification forbids exactly that.

  * Blank lines and end of file. Consecutive blank lines between statements are
    accepted -- they never add a second boundary -- and a final statement with no
    trailing newline is accepted, exercising end-of-file termination.

  * `include` termination. A top-level include ends where its physical line ends
    -- newline-terminated and raw-string-path forms are accepted -- and an
    include with no line boundary before the following statement is rejected.
    Section 20 states the directive has no include-specific termination rule, so
    the rejection is an ordinary termination failure asserted bare.
"""
import base64, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

# The included helper, shared by every include-termination test. It declares one
# function so a terminated include is observable by calling through it: if the
# directive were not terminated, the following statement would not parse at all.
LIB = "func two(): Integer {\n    return 2\n}\n"

REQS = {
 "REQ-1900": dict(
  section="16. Statement Termination and Brace Placement",
  summary="A physical newline ends the construct that precedes it and a `;` only separates two constructs written on the same physical line: newline-separated and same-line-`;`-separated declarations both run, while a `;` followed by another physical line, end of file, or a stand-alone closing brace is rejected at the semicolon as SOLV-PARS-012, and two constructs sharing a line with no separator between them are a compile-time error",
  kind="compile-time",
  notes="The two accepted arms are the controls that keep the rejection from being satisfied by an implementation that rejects every multi-statement program; the same-line arm is the specification's own separator example. The three `;`-rejection arms each remove what the semicolon should have separated -- the next physical line, end of file, the stand-alone closing brace of the enclosing block -- and pin the code the specification names, SOLV-PARS-012. The no-separator rejection puts a second statement on the first statement's line with no separator at all, which the specification's 'the grammar requires a separator between every two constructs' forces; it stays bare because the specification names no code for it.",
  quotes=["The semicolon is a separator, not a terminator. It may separate two constructs written on the same physical line:",
          "A `;` that is followed by another physical line, by end of file, or by a stand-alone closing brace did not separate two constructs on its line and is rejected as",
          "the grammar requires a separator between every two constructs"]),
 "REQ-1901": dict(
  section="16. Statement Termination and Brace Placement",
  summary="A call may spread its arguments across physical lines because the grammar absorbs the boundary after `(`, after a comma, and before `)`, while a complete statement followed by an operator line cannot continue even inside parentheses, because nesting depth grants no continuation",
  kind="compile-time",
  notes="The accepted arm breaks only at admitted positions -- after the opening paren, after each comma, and before the closing paren -- and remains a single call whose printed result is the oracle. The rejected arm `var v = 1\\n + 2` puts the newline BEFORE the operator: the first line ended of its own accord after the literal, so `+ 2` is orphaned, and it stays an error inside no parentheses at all because depth grants nothing. An implementation that joined a newline before an operator whenever brackets enclose the expression -- the removed depth-suppression design -- accepts the rejected arm and fails. Bare rejection.",
  quotes=["the grammar itself absorbs the boundary tokens at the positions where a construct may spread across lines - after a binary operator, after a comma, before a `.`, `?.`, or `::`, around argument, type-argument, and pattern lists, and before a closing delimiter.",
          "a line break the grammar does not admit is an error at the break."]),
 "REQ-1902": dict(
  section="16. Statement Termination and Brace Placement",
  summary="An expression continues across a line whose last token is a binary operator or a comma, while a newline placed before an operator - a complete line followed by an operator line - is a compile-time error, so continuation follows the grammar and not JavaScript's operator-continuation intuition",
  kind="compile-time",
  notes="`var b = a +\\n 1` (accepted) and `var b = a\\n + 1` (rejected) differ only in which side of the newline the `+` sits: on the accepted side the line cannot end after an operator, so the expression continues; on the rejected side the line ended after the identifier, so `+ 1` is orphaned. A second rejection uses `*` rather than `+` to show the rule is not about any one operator, and the multi-line call-argument arm shows the comma position. Together these are the direct evidence against JavaScript-style heuristics: an implementation that carried over JS operator-continuation would accept both leading-operator programs. Bare rejections.",
  quotes=["a line break the grammar does not admit is an error at the break.",
          "There is no lookahead exception table and no heuristic join: a line break the grammar does not admit is an error at the break."]),
 "REQ-1903": dict(
  section="16. Statement Termination and Brace Placement",
  summary="A line ending in `.` or `?.` cannot end, so a leading-dot member chain and a leading-safe-call chain continue across lines, and a clause keyword `else` beginning its own physical line is the canonical layout for both statement and expression `if`",
  kind="compile-time",
  notes="Each accepted program is accepted ONLY because the grammar absorbs the boundary before the member suffix; dropping the continuation for `.`, for `?.`, or for the standalone clause line makes that program break at its first chain newline or its `else`. The dot and safe-call chains are separate programs because the specification lists them as two suffixes, and the `else` is tested both as a statement and as the arm of an if-expression written in the canonical brace layout. Every arm is an acceptance whose stdout is the oracle, so no code assertion is needed.",
  quotes=["A line ending in `.` or `?.` cannot end, so the chain continues.",
          "Solvik supports TypeScript/Kotlin-style leading-dot chains:",
          "a clause is written `}` newline `else {`"]),
 "REQ-1904": dict(
  section="16. Statement Termination and Brace Placement",
  summary="A newline contained in a line comment or a block comment is still a physical newline, so a statement can be terminated by a newline that appears only inside a comment",
  kind="compile-time",
  notes="`var a = 1 /* c\\n*/ print(...)` is accepted while the identical program with the comment-newline removed, `var a = 1 print(...)`, is rejected (that rejection is REQ-1900's arm and the contrast is documented in the plan). The accepted arm can only parse if the stage treats the newline contained in the block comment as the physical boundary that ended `var a = 1`; a stage that ignored comment interiors would see no boundary and reject it. A line-comment newline is accepted on the same principle. This is the only direct evidence for the clause, since a portable oracle cannot inspect the token stream.",
  quotes=["The lexer preserves every physical newline (including a newline inside a comment, because it is still a physical newline)"]),
 "REQ-1905": dict(
  section="16. Statement Termination and Brace Placement",
  summary="`return` followed by a newline is a complete bare return, so a value placed on the next line is not the returned value and a bare return leaves the function before any following statement",
  kind="compile-time",
  notes="The rejection is a value-returning function whose value sits on the line after `return`, so the return is terminated bare and the function returns no value -- the specification's own worked example. The control `return 7` on one line is accepted. The strongest arm is a value-less function with `return` then `print(\"after\")` on the next line: the expected bytes are 'in' then 'done' with NO 'after', which proves the bare return left the function at the newline (a JavaScript-ASI reading would swallow the print as the return value and emit 'after'). That expected output is the direct embodiment of 'do not copy ... JavaScript's automatic semicolon insertion behavior', and it fails in the safe direction -- an implementation that did copy JS would be caught by the extra 'after'. Bare rejection.",
  quotes=["`return` followed by a newline is a complete bare return; the next line begins a new statement.",
          "Do not copy TypeScript's unsound `any` behavior or JavaScript's automatic semicolon insertion behavior."]),
 "REQ-1906": dict(
  section="16. Statement Termination and Brace Placement",
  summary="Blank lines between statements never add a second boundary, and the final physical line of a file is terminated by end of file even without a trailing newline",
  kind="compile-time",
  notes="The blank-lines program has two consecutive blank lines between two declarations and a trailing blank line before the final statement; if a second boundary were emitted per blank line the parser would see empty statements it rejects, so acceptance is the evidence for the single-boundary clause. The end-of-file program ends after a call with no trailing newline at all, exercising end-of-file termination (the final statement is terminated by the end of the file, not by a following token). Both are accepted and the observable is the printed bytes, so no code assertion is needed.",
  quotes=["Blank lines and comment lines never add a second boundary, and the final physical line of a file is terminated by end of file, to which the same rule applies without a following token."]),
 "REQ-1907": dict(
  section="20. File Inclusion / 16. Statement Termination and Brace Placement",
  summary="A top-level include directive ends where its physical line ends -- its path is a string or raw-string literal so a following physical newline terminates it -- and an include followed by a statement on the same line is a compile-time error",
  kind="compile-time",
  notes="Two accepted arms terminate an include by a plain newline, one with a normal string path and one with a raw-string path, each observable by calling a function declared in the included file -- if the directive were not terminated, the following call statement could not parse. The rejected arm places the call on the include's own line with no separator, so the directive and the statement collide on one physical line. The section states explicitly that no include-specific termination rule exists, so the rejection is an ordinary termination failure and is asserted bare like the other termination rejections; the included helper is the same for every arm so only the termination differs.",
  quotes=["The path is a normal or raw string literal, and the directive ends where its physical line ends, like any construct (section 16)."]),
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


def rej(tid, req, src, libs=None, code=None):
    diag = {"family": code.split("-")[1], "code": code} if code else {}
    add(tid, req, "syntax", src, "COMPILE_ERROR", libs=libs, diag=diag)


# --- REQ-1900: a newline ends a construct; `;` only separates one line.
succ("SOL-TCK-0286", "REQ-1900",
     (('var a: Integer = 1\n'
    'var b: Integer = 2\n'
    'print("n" .. a .. b)\n'
    '')), "n12")
succ("SOL-TCK-0287", "REQ-1900",
     (('var a: Integer = 1; var b: Integer = 2\n'
    'print("x" .. a .. b)\n'
    '')), "x12")
rej("SOL-TCK-0288", "REQ-1900",
    ('var a: Integer = 1 print("a" .. a)\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''))
# The three SOLV-PARS-012 arms: the `;` ends its line, ends the file, and
# precedes the stand-alone closing brace of the block that encloses it.
rej("SOL-TCK-0496", "REQ-1900",
    ('var a: Integer = 1;\n'
    'print("s" .. a)\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''), code="SOLV-PARS-012")
rej("SOL-TCK-0497", "REQ-1900",
    ('var v: Integer = {\n'
    '    42;\n'
    '}\n'
    'print("v" .. v)\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''), code="SOLV-PARS-012")
rej("SOL-TCK-0498", "REQ-1900",
    (('var a: Integer = 1\n'
    'print("e" .. a);')), code="SOLV-PARS-012")

# --- REQ-1901: bracketed continuations are grammar, not lookahead.
succ("SOL-TCK-0289", "REQ-1901",
     (('func add(x: Integer, y: Integer): Integer {\n'
    '    return x + y\n'
    '}\n'
    'var v: Integer = add(\n'
    '    1,\n'
    '    2,\n'
    ')\n'
    'print("v" .. v)\n'
    '')), "v3")
rej("SOL-TCK-0290", "REQ-1901",
    ('var v: Integer = 1\n'
    '    + 2\n'
    'print("v" .. v)\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''))

# --- REQ-1902: operators and commas continue; operator lines do not join.
succ("SOL-TCK-0291", "REQ-1902",
     (('var a: Integer = 1\n'
    'var b: Integer = a +\n'
    '    1\n'
    'print("b" .. b)\n'
    '')), "b2")
succ("SOL-TCK-0292", "REQ-1902",
     (('class C {\n'
    '    C(x: Integer, y: Integer) {\n'
    '        this.sum = x + y\n'
    '    }\n'
    '\n'
    '    var sum: Integer\n'
    '}\n'
    'var c: C = C(1,\n'
    '    2)\n'
    'print("r" .. c.sum)\n'
    '')), "r3")
rej("SOL-TCK-0293", "REQ-1902",
    ('var a: Integer = 1\n'
    'var b: Integer = a\n'
    '    + 1\n'
    'print("b" .. b)\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''))
rej("SOL-TCK-0294", "REQ-1902",
    ('var a: Integer = 2\n'
    'var b: Integer = a\n'
    '    * 3\n'
    'print("b" .. b)\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''))

# --- REQ-1903: member-suffix continuation and the canonical standalone `else`.
succ("SOL-TCK-0295", "REQ-1903",
     (('class S {\n'
    '    S() {\n'
    '    }\n'
    '\n'
    '    method load(): S {\n'
    '        return this\n'
    '    }\n'
    '\n'
    '    method value(): Integer {\n'
    '        return 42\n'
    '    }\n'
    '}\n'
    'var s: S = S()\n'
    'var r: Integer = s\n'
    '    .load()\n'
    '    .value()\n'
    'print("r" .. r)\n'
    '')), "r42")
succ("SOL-TCK-0296", "REQ-1903",
     (('class W {\n'
    '    W() {\n'
    '    }\n'
    '\n'
    '    method opt(): String? {\n'
    '        return "z"\n'
    '    }\n'
    '}\n'
    'var w: W = W()\n'
    'var v: Integer? = w.opt()\n'
    '    ?.hashCode()\n'
    'print("v" .. (v != null))\n'
    '')), "vtrue")
succ("SOL-TCK-0297", "REQ-1903",
     (('var c: Boolean = false\n'
    'if (c) {\n'
    '    print("y")\n'
    '}\n'
    'else {\n'
    '    print("n")\n'
    '}\n'
    '')), "n")
succ("SOL-TCK-0298", "REQ-1903",
     (('var c: Boolean = true\n'
    'var r: Integer = if (c) {\n'
    '    1\n'
    '}\n'
    'else {\n'
    '    2\n'
    '}\n'
    'print("r" .. r)\n'
    '')), "r1")

# --- REQ-1904: a newline inside a comment is a physical newline.
succ("SOL-TCK-0299", "REQ-1904",
     (('var a: Integer = 1 /* c\n'
    '*/ print("bc" .. a)\n'
    '')), "bc1")
succ("SOL-TCK-0300", "REQ-1904",
     (('var a: Integer = 1 // note\n'
    'print("lc" .. a)\n'
    '')), "lc1")

# --- REQ-1905: `return` followed by a newline is a complete bare return.
rej("SOL-TCK-0301", "REQ-1905",
    'func f(): Integer {\n    return\n    7\n}\nprint("v" .. f())\n' + NEG)
succ("SOL-TCK-0302", "REQ-1905",
     'func f(): Integer {\n    return 7\n}\nprint("v" .. f())\n', "v7")
# The bare-return-in-void function: "in" prints, the bare return leaves the function at the
# newline, so "after" is unreachable and must NOT appear. A JavaScript-ASI reading emits "after".
succ("SOL-TCK-0303", "REQ-1905",
     'func f() {\n    print("in")\n    return\n    print("after")\n}\nf()\nprint("done")\n',
     "indone")

# --- REQ-1906: blank lines add no second boundary; EOF closes the final line.
succ("SOL-TCK-0304", "REQ-1906",
     (('var a: Integer = 1\n'
    '\n'
    '\n'
    'var b: Integer = 2\n'
    '\n'
    'print("z" .. a .. b)\n'
    '')), "z12")
add("SOL-TCK-0305", "REQ-1906", "syntax",
    (('var a: Integer = 1\n'
    'var b: Integer = 2\n'
    'print("e" .. a .. b)')), "SUCCESS", stdout="e12")

# --- REQ-1907: an include ends where its physical line ends.
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
    # The REQ-1900 separator arm must genuinely put both declarations on ONE physical
    # line, or it stops testing the separator role and just looks like 0286.
    if "\n" in S["SOL-TCK-0287"].split("\n")[0].split("print")[0] or ";" not in S["SOL-TCK-0287"].split("\n")[0]:
        bad.append(("SHAPE", "REQ-1900", "separator arm no longer shares one physical line"))
    # The end-of-file `;` arm must genuinely end the file with the semicolon.
    if S["SOL-TCK-0498"].endswith("\n") or not S["SOL-TCK-0498"].endswith(";"):
        bad.append(("SHAPE", "REQ-1900", "end-of-file semicolon arm does not end the file at the `;`"))
    # The end-of-file arm must genuinely lack a trailing newline, or it stops testing
    # the end-of-file clause while still looking like the same test.
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
    # The REQ-1901 accepted arm must genuinely break only at admitted positions:
    # after the open paren, after each comma, before the close.
    if "add(\n    1,\n    2,\n)" not in S["SOL-TCK-0289"]:
        bad.append(("SHAPE", "REQ-1901", "accepted arm no longer breaks only at admitted positions"))
    # The REQ-1902 pair must differ only by which side of the newline the `+` sits.
    if "a +\n" not in S["SOL-TCK-0291"] or "a\n    + 1" not in S["SOL-TCK-0293"]:
        bad.append(("SHAPE", "REQ-1902", "operator pair no longer differs only by operator side"))
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
        man = {"manifestSchemaVersion": 1, "specVersion": "2026.11-draft", "testId": tid,
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
