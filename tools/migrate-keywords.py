#!/usr/bin/env python3
r"""Migrate .sol programs from the 2026.10-draft keywords to 2026.11-draft.

Rewrites are anchored to declaration position, never to the bare word, because a keyword that
appears inside a string literal is program *data* and rewriting it corrupts an oracle. The same is
true of a line comment: comments are rewritten only by the narrower comment rules below, and only
when they state a rule about the keyword rather than merely mentioning it.

Rules, all applied to the region of a line before any `//`:

    ^(\s*)var\s+            -> \1mutable val
    ^(\s*)static\s+var\s+   -> \1static mutable val
    ^(\s*)open\s+class\b    -> \1mutable class
    ^(\s*)open\s+func\b     -> \1mutable func
    ^(\s*)sealed\s+class\b  -> \1abstract class

The `static var` rule runs first so `static` is not stranded by the plain `var` rule. A `var` that
appears mid-expression (not at declaration position) is reported rather than rewritten, because the
grammar admits no other form and an unanchored rewrite would be a guess.

Usage:
    python3 tools/migrate-keywords.py [--apply] [--roots DIR ...]
"""

import argparse
import os
import re
import sys

DEFAULT_ROOTS = ["language/tests", "language/src/test", "benchmarks", "tck/corpus"]

CODE_RULES = [
    (re.compile(r"^(\s*)static\s+var(\s+[A-Za-z_])"), r"\1static mutable val\2"),
    (re.compile(r"^(\s*)var(\s+[A-Za-z_])"), r"\1mutable val\2"),
    (re.compile(r"^(\s*)open(\s+class\b)"), r"\1mutable\2"),
    (re.compile(r"^(\s*)open(\s+func\b)"), r"\1mutable\2"),
    (re.compile(r"^(\s*)sealed(\s+class\b)"), r"\1abstract\2"),
    # `static` always leads, but a method modifier may follow it, and `methodModifier*` admits either
    # order of `mutable` and `override`, so all four spellings occur in the corpus.
    (re.compile(r"^(\s*)static\s+open(\s+func\b)"), r"\1static mutable\2"),
    (re.compile(r"^(\s*)override\s+open(\s+func\b)"), r"\1override mutable\2"),
    (re.compile(r"^(\s*)open\s+override(\s+func\b)"), r"\1mutable override\2"),
    # A three-clause `for` initializer is a declaration, so its binding keyword is not at line start.
    (re.compile(r"\bfor\s*\(\s*var(\s)"), r"for (mutable val\1"),
]

KEYWORD = re.compile(r"\b(var|open|sealed)\b")


def split_code(line):
    """Return (code, trailing, masked) for a line.

    `code` is the text before any `//` comment, `trailing` is that comment or empty, and `masked` is
    `code` with every string-literal body replaced by spaces of the same width. A keyword inside a
    string is program data, so `masked` is what the residual check scans: rewriting `print("var" .. x)`
    would change an oracle's expected bytes, and flagging it would bury the real residuals.

    A `//` inside a string literal does not start a comment. Solvik has no block-comment nesting and
    no multi-line strings, so a quote-parity scan of the prefix is sufficient here. Raw strings
    (`r#"..."#`) use plain quote characters for their ends, so treating every quote as a delimiter is
    the conservative reading: an odd count leaves the remainder treated as code, which reports rather
    than rewrites.
    """
    i = 0
    in_string = False
    comment = None
    while i < len(line):
        c = line[i]
        if c == '"':
            in_string = not in_string
        elif c == "/" and not in_string and line.startswith("//", i):
            comment = i
            break
        i += 1
    code = line if comment is None else line[:comment]
    trailing = "" if comment is None else line[comment:]
    masked = []
    in_string = False
    for c in code:
        if c == '"':
            in_string = not in_string
        masked.append(" " if in_string else c)
    return code, trailing, "".join(masked)


def migrate_code(code):
    out = code
    for pattern, repl in CODE_RULES:
        out = pattern.sub(repl, out)
    return out


def residual(code):
    """Keywords left in a code region after rewriting: never expected, always reported."""
    return [m.group(0) for m in KEYWORD.finditer(code)]


def comment_residue(text):
    """Keywords a comment still mentions. Reported, never rewritten.

    A comment in this corpus quotes the specification, names an obligation, or identifies a program by
    an output string. Rewriting that prose would silently change a test's stated rationale, so the
    migration refuses to guess and reports instead.
    """
    return [m.group(0) for m in KEYWORD.finditer(text)]


def files_under(roots):
    for root in roots:
        if os.path.isfile(root):
            yield root
            continue
        for dirpath, _dirs, names in os.walk(root):
            for name in sorted(names):
                if name.endswith(".sol"):
                    yield os.path.join(dirpath, name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--roots", nargs="*", default=DEFAULT_ROOTS)
    args = ap.parse_args()

    changed = 0
    total = 0
    residual_lines = []
    comment_lines = []
    for path in sorted(set(files_under(args.roots))):
        with open(path, encoding="utf-8") as handle:
            original = handle.read()
        lines = original.split("\n")
        out = []
        touched = False
        for number, line in enumerate(lines, 1):
            code, trailing, masked = split_code(line)
            new_code = migrate_code(code)
            _old_masked, _rest, new_masked = split_code(new_code)
            for word in residual(new_masked):
                residual_lines.append("%s:%d: unreplaced %s: %s" % (path, number, word, line.strip()))
            for word in comment_residue(trailing):
                comment_lines.append("%s:%d: comment mentions %s" % (path, number, word))
            if new_code != code:
                touched = True
            out.append(new_code + trailing)
        total += 1
        if touched:
            changed += 1
            if args.apply:
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write("\n".join(out))

    print("%s: %d of %d .sol files %s" % ("applied" if args.apply else "would change", changed, total,
                    "rewritten" if args.apply else "rewritten"))
    if residual_lines:
        print("RESIDUAL (unreplaced keyword in code position, %d):" % len(residual_lines))
        for line in residual_lines[:60]:
            print("  " + line)
    else:
        print("residual: none")
    if comment_lines:
        print("comments still naming a removed keyword (%d, not rewritten):" % len(comment_lines))
        for line in comment_lines[:10]:
            print("  " + line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
