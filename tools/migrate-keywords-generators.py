#!/usr/bin/env python3
r"""Migrate Solvik keyword usage inside TCK generator string literals.

A generator holds Solvik programs in Python string literals, and those programs need the same
rewrite the corpus received. The hazard is that a generator also holds prose: requirement summaries
and normative quotations that name the removed keywords as words. Rewriting prose silently changes
an oracle's stated justification, so a rule may fire only where a line inside a literal begins with
the removed keyword in declaration position -- the shape only source code has. Prose that merely
mentions a keyword never begins that way.

Because the rewrite is applied to the literal's contents, it must find "line start" in both forms a
literal can encode a line break: a real newline in a triple-quoted literal, or the two-character
escape `\n` in a single-quoted fragment.

Run with --apply, then regenerate the corpus and let verify_regen.py confirm the result matches the
committed corpus byte for byte. That end-to-end check is the point: a too-broad rule that corrupts a
program shows up there rather than in review.

Usage: tools/migrate-keywords-generators.py [--apply]
"""

import argparse
import os
import re
import tokenize

GENERATOR_DIR = os.path.join("tck", "tools")

# Anchored at a line start inside the literal. `val` needs no rule; it is unchanged.
RULES = [
    (re.compile(r"^(\s*)static\s+open(\s+func\b)"), r"\1static mutable\2"),
    (re.compile(r"^(\s*)static\s+var(\s+[A-Za-z_])"), r"\1static mutable val\2"),
    (re.compile(r"^(\s*)override\s+open(\s+func\b)"), r"\1override mutable\2"),
    (re.compile(r"^(\s*)open\s+override(\s+func\b)"), r"\1mutable override\2"),
    (re.compile(r"^(\s*)open\s+func(\s+[A-Za-z_])"), r"\1mutable func\2"),
    (re.compile(r"^(\s*)open\s+class\b"), r"\1mutable class"),
    (re.compile(r"^(\s*)sealed\s+class\b"), r"\1abstract class"),
    (re.compile(r"^(\s*)var(\s+[A-Za-z_])"), r"\1mutable val\2"),
]

# Solvik lines inside a Python literal are separated either by a real newline (a triple-quoted
# literal) or by the two-character escape `\n` (a fragment built by concatenation). Both must read as
# a line start, and each must be restored as whatever it was, so the body is split into segments while
# remembering which separator produced each boundary.


def split_lines(body: str):
    """Split a literal body into (segments, separators), where separators keeps the boundary text."""
    segments = []
    separators = []
    current = []
    index = 0
    length = len(body)
    while index < length:
        char = body[index]
        if char == "\\" and index + 1 < length:
            following = body[index + 1]
            if following == "n":
                # An odd run of backslashes before `n` is the escape; an even run means the `n` is
                # literal text, so `\\n` in source is a backslash followed by a letter, not a break.
                backslashes = 0
                probe = len(current) - 1
                while probe >= 0 and current[probe] == "\\":
                    backslashes += 1
                    probe -= 1
                if backslashes % 2 == 0:
                    segments.append("".join(current))
                    separators.append("\\n")
                    current = []
                    index += 2
                    continue
            current.append(char)
            current.append(following)
            index += 2
            continue
        if char == "\n":
            segments.append("".join(current))
            separators.append("\n")
            current = []
            index += 1
            continue
        current.append(char)
        index += 1
    segments.append("".join(current))
    return segments, separators


def rejoin(segments, separators) -> str:
    out = segments[0]
    for separator, segment in zip(separators, segments[1:]):
        out += separator + segment
    return out


def rewrite_literal(raw: str):
    """Migrate the body of a source-level string literal, keeping its prefix and quotes."""
    match = re.match(r'([bBfFrRuU]{0,3})(\'\'\'|"""|\'|")(.*)\2$', raw, re.S)
    if match is None:
        return raw, False
    prefix, quote, body = match.groups()
    segments, separators = split_lines(body)
    out = []
    changed = False
    for segment in segments:
        piece = segment
        for pattern, repl in RULES:
            piece = pattern.sub(repl, piece)
        changed = changed or piece != segment
        out.append(piece)
    if not changed:
        return raw, False
    return prefix + quote + rejoin(out, separators) + quote, True


def migrate_file(path, apply):
    with open(path, "rb") as handle:
        source = handle.read()
    text = source.decode("utf-8")
    edits = []
    findings = []
    lines = text.split("\n")
    with open(path, "rb") as handle:
        for tok in tokenize.tokenize(handle.readline):
            if tok.type != tokenize.STRING:
                continue
            raw = tok.string
            new, changed = rewrite_literal(raw)
            if not changed:
                continue
            edits.append((tok.start[0], tok.end[0], raw, new))
            findings.append((tok.start[0], raw[:60].replace("\n", "\\n"), new[:60].replace("\n", "\\n")))
    if not edits:
        return 0, findings
    # Rewrite from the end so earlier offsets stay valid. Literals are reported one per token, and
    # the whole logical line of the token is replaced by its migrated form.
    out = list(lines)
    for start, end, raw, new in sorted(edits, key=lambda e: -e[0]):
        block = "\n".join(out[start - 1:end])
        replaced = block.replace(raw, new, 1)
        assert replaced != block, "literal not found on its own line range at %s:%d" % (path, start)
        out[start - 1:end] = replaced.split("\n")
    result = "\n".join(out)
    if apply:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(result)
    return len(edits), findings


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    total = 0
    for name in sorted(os.listdir(GENERATOR_DIR)):
        if not (name.startswith("gen") and name.endswith(".py")):
            continue
        path = os.path.join(GENERATOR_DIR, name)
        count, findings = migrate_file(path, args.apply)
        if count:
            print("%-10s %d literals" % (name, count))
            total += count
    print(("applied: %d literals" if args.apply else "would rewrite %d literals") % total)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
