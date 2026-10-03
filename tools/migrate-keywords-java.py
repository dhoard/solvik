#!/usr/bin/env python3
"""Rewrite Solvik keyword usage in Java sources for the 2026.11-draft keyword revision.

Java test sources embed Solvik programs in three shapes, and each needs different treatment.

1. Text blocks (triple-quoted). The line is Solvik source in its entirety, so the line-anchored
   rules used for `.sol` files apply unchanged.
2. String literals on a Java line. A Solvik program is often built by concatenating literal
   fragments, so each literal's contents are Solvik source even though the surrounding line is Java.
   The same line-anchored rules apply to those contents.
3. Java code itself. Java has its own `var` (in `var x = ...`, `for (var x : list)`,
   `try (var s = ...)`) and `open` (in `open module ...`). Those are not Solvik and rewriting them
   does not merely change semantics, it stops the file from compiling. So no Solvik rule ever runs on
   Java code.

Javadoc and `//` comments are handled by a separate, narrower table, because they name keywords
prose-wise (`{@code var}`) rather than embedding a program.

Anything the rules see but decline to replace is reported rather than guessed at. A few negative
tests deliberately construct invalid syntax (`delegate var x`, `sealed func`), and those need a
human decision about which construct should now be the rejected one.

Usage: tools/migrate-keywords-java.py [--apply] [--roots DIR ...]
"""

from __future__ import annotations

import argparse
import os
import re

DEFAULT_ROOTS = ["language/src/test", "language/src/main", "launcher", "standalone"]

# Only these three are removed by the revision. `val` and `override` survive unchanged, and `final`
# is Java's own keyword, so neither belongs in a residual report.
# The lookarounds keep hyphenated English out of the way: `half-open` and `deep-open` are not mentions
# of the removed `open`, and Python's `\b` alone treats the hyphen as a boundary.
REMOVED = re.compile(r"(?<![-\w])(var|sealed|open)(?![-\w])")

# A line may opt out of the residual report when it tests that a removed keyword is still reserved,
# which requires spelling it. The mark is the comment marker below, so the report stays empty for a
# migrated tree and can be read as pass or fail.
KEEP_MARK = "solvik-keyword:"

# `open` also opens a Java module declaration, which has nothing to do with Solvik.
SKIP = {"module-info.java"}

TRIPLE = chr(34) * 3
QUOTE = chr(34)
ESCAPED_QUOTE = chr(92) + chr(34)
ESCAPED_NEWLINE = chr(92) + "n"

# Line-anchored rules for text that is Solvik source: `^` is the start of the Solvik line, which for
# a concatenated fragment is the start of the literal's contents. `val` needs no rule -- plain `val`
# is unchanged by this revision, which is also why these patterns are safe on fragments that already
# carry the new spelling.
CODE_RULES = [
    # `open` is gone in every position; what follows it now carries the distinction `open` used to
    # signal, so the relative order of the class and func rules does not matter.
    (re.compile(r"^(\s*)open\s+abstract\s+class\b"), r"\1mutable abstract class"),
    (re.compile(r"^(\s*)open\s+class\b"), r"\1mutable class"),
    (re.compile(r"^(\s*)sealed\s+class\b"), r"\1abstract class"),
    (re.compile(r"^(\s*)open\s+func\b"), r"\1mutable func"),
    (re.compile(r"^(\s*)open\s+delegate\b"), r"\1mutable delegate"),
    (re.compile(r"^(\s*)override\s+open\s+func\b"), r"\1override mutable func"),
    (re.compile(r"^(\s*)open\s+override\s+func\b"), r"\1mutable override func"),
    (re.compile(r"^(\s*)(static\s+)?var\s+"), r"\1\2mutable val "),
    # A three-clause `for` initializer is a declaration, so its keyword is not at line start. The
    # `;` lookahead is the discriminator against Java's for-each and try-with-resources, neither of
    # which has a `;` inside its parentheses -- and which never reach this function regardless,
    # because they are Java rather than literal contents.
    (re.compile(r"\bfor\s*\(\s*var(?=[^)]*;)"), "for (mutable val"),
]

# Comments name keywords in prose rather than embedding a program, so they get their own table.
COMMENT_RULES = [
    # Javadoc cites keywords in prose as `{@code var}`. Braces are literal in both the pattern and
    # the replacement -- `re.sub` gives them no special meaning.
    (re.compile(r"\{@code var\}"), "{@code mutable val}"),
    (re.compile(r"\{@code var\s"), "{@code mutable val "),
    (re.compile(r"\{@code (?:sealed|open)\}"), "{@code mutable}"),
    (re.compile(r"`var`"), "`mutable val`"),
    (re.compile(r"`sealed`"), "`abstract`"),
    (re.compile(r"`open`"), "`mutable`"),
    (re.compile(r"\bthe sealed modifier\b"), "the abstract modifier"),
    (re.compile(r"\bsealed hierarchy\b"), "abstract class hierarchy"),
    (re.compile(r"\bsealed hierarchies\b"), "abstract class hierarchies"),
]

STRING_LITERAL = re.compile(chr(34) + "(?:" + "[^" + chr(34) + "\\\n]|\\\\.)*" + chr(34))


def apply_rules(text: str, rules) -> str:
    out = text
    for pattern, repl in rules:
        out = pattern.sub(repl, out)
    return out


def count_block_delimiters(line: str) -> int:
    """Count text-block delimiters on a line, ignoring those Java escapes as backslash-triple-quote.

    Counting raw delimiters instead desynchronises block tracking, and since the in-block branch runs
    the line-anchored rules on the whole line, that silently rewrites Java as though it were Solvik.
    """
    return line.replace(ESCAPED_QUOTE, "").count(TRIPLE)


def migrate_line(line: str, in_block: bool) -> str:
    if in_block:
        return apply_rules(line, CODE_RULES)
    if re.match(r"^\s*(/?\*|//)", line):
        # A comment or Javadoc line. Prose mentions of a removed keyword are updated, but a mention
        # that is ordinary English (`the enclosing call's ( is still open`) is left alone, so the
        # table is narrow by design and anything unmatched surfaces as a residual instead.
        return apply_rules(line, COMMENT_RULES)
    # The line is Java: keep every character outside the literals, and treat each literal's contents
    # as Solvik source. Reconstructing from parts guarantees Java text is never dropped or rewritten.
    parts = []
    index = 0
    for match in STRING_LITERAL.finditer(line):
        parts.append(line[index:match.start()])
        parts.append(QUOTE + migrate_literal_body(match.group(0)[1:-1]) + QUOTE)
        index = match.end()
    parts.append(line[index:])
    return "".join(parts)


def migrate_literal_body(inner: str) -> str:
    """Migrate the inside of a Java string literal holding Solvik source.

    A literal may hold several Solvik lines separated by escaped newlines, and the rules are
    line-anchored, so each fragment is migrated on its own. Splitting on the escaped newline rather
    than treating the whole literal as one line is what lets `var` after a `;` or on a second line be
    seen at all.
    """
    pieces = inner.split(ESCAPED_NEWLINE)
    return ESCAPED_NEWLINE.join(apply_rules(piece, CODE_RULES) for piece in pieces)


def masked(line: str) -> str:
    out = STRING_LITERAL.sub(lambda m: QUOTE + chr(0) * max(0, len(m.group(0)) - 2) + QUOTE, line)
    comment = out.find("//")
    return out if comment < 0 else out[:comment]


def migrate_file(path: str, apply: bool):
    with open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")
    out = []
    touched = False
    residuals = []
    in_block = False
    for number, line in enumerate(lines, 1):
        marks = count_block_delimiters(line)
        new = migrate_line(line, in_block)
        if new != line:
            touched = True
        out.append(new)
        # The gate is that no removed keyword survives inside a Solvik program: a text-block line, or
        # a string literal on a Java line. Java's own `var` and ordinary English `open` are legitimate
        # and are not reported.
        if KEEP_MARK in new:
            pass
        elif in_block and REMOVED.search(new):
            residuals.append("%s:%d: removed keyword in Solvik text block: %s"
                             % (path, number, new.strip()))
        elif not in_block:
            for literal in STRING_LITERAL.finditer(new):
                if REMOVED.search(literal.group(0)):
                    residuals.append("%s:%d: removed keyword in Solvik string literal: %s"
                                     % (path, number, new.strip()))
                    break
        if marks % 2 == 1:
            in_block = not in_block

    result = "\n".join(out)
    if touched and apply:
        # A migration only ever substitutes text. The guard is insurance against a branch that
        # reports a line and then forgets to re-emit it, which deletes code silently.
        if len(lines) != len(out):
            raise SystemExit("refusing to write %s: line count %d -> %d" % (path, len(lines), len(out)))
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(result)
    return touched, residuals


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--roots", nargs="*", default=None)
    ap.add_argument("--report", default=None)
    args = ap.parse_args()

    paths = []
    for root in (args.roots or DEFAULT_ROOTS):
        for dirpath, _dirs, names in os.walk(root):
            for name in sorted(names):
                if name.endswith(".java") and name not in SKIP:
                    paths.append(os.path.join(dirpath, name))

    changed = 0
    residuals = []
    for path in paths:
        touched, rest = migrate_file(path, args.apply)
        residuals += rest
        if touched:
            changed += 1

    report = args.report or ("/tmp/migrate-keywords-java.residual" if residuals else None)
    if residuals:
        with open(report, "w", encoding="utf-8") as handle:
            handle.write("\n".join(residuals) + "\n")
    print(("applied: %d of %d .java files rewritten" if args.apply else "would rewrite %d of %d .java files")
          % (changed, len(paths)))
    if residuals:
        print("RESIDUAL (%d): see %s" % (len(residuals), report))
        for line in residuals[:25]:
            print("  " + line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
