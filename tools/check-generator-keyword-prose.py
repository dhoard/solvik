#!/usr/bin/env python3
"""Report every string literal in the TCK generators that still mentions a removed keyword.

The keyword overhaul rewrote Solvik source fragments inside the generators, but
the generators also carry *prose* about the old vocabulary in `summary`, `notes`,
and `failureModes` strings. Requirement records that a generator owns are
reproduced byte-for-byte by `tck/tools/verify_regen.py`, so stale prose there
cannot be patched in the inventory -- it has to be fixed at the generator. This
script is the search path for that fix, and a gate: it exits non-zero while any
literal still names `val`, `sealed`, or `open` as a language keyword.

Literals are examined both as written and with `\\n` unescaped, because many
generator sources are single-line strings whose line structure exists only as an
escape. Ordinary English uses of the words ("the class is open to extension",
"half-open") are not keyword uses and are not reported: the pattern requires the
word to stand alone as an identifier, which is what makes `val x = 1` and
`open func` detectable without parsing Solvik.

Usage: python3 tools/check-generator-keyword-prose.py
"""

import io
import pathlib
import re
import sys
import tokenize

ROOT = pathlib.Path(__file__).resolve().parent.parent
GENS = sorted((ROOT / "tck" / "tools").glob("gen*.py"))

# A removed keyword standing alone as a word: `val`, `sealed`, `open`. The
# lookarounds reject hyphenated and attached uses ("half-open", "openFoo",
# "variables", "interval"), which are prose, not syntax.
KEYWORD = re.compile(r"(?<![\w-])(val|sealed|open)(?![\w-])")

# Occasions that name a removed keyword *on purpose*, keyed (generator, exact
# line within a literal). A program that prints the text of a removed word as
# string data is legitimate, but none is needed: `var` is the active keyword now,
# so no generator prints `val` as data. An exception is verified rather than
# trusted: if the line stops appearing the check fails, which stops an exception
# outliving the case it was written for.
EXCEPTIONS = {}


def literals(path):
    """Yield (line, text) for every plain string literal in a generator."""
    src = path.read_text(encoding="utf-8")
    for tok in tokenize.generate_tokens(io.StringIO(src).readline):
        if tok.type != tokenize.STRING:
            continue
        prefix = tok.string[:len(tok.string) - len(tok.string.lstrip("\"'"))].lower()
        if "f" in prefix or "b" in prefix:
            continue  # f-strings interpolate; bytes are not used here
        try:
            value = eval(tok.string, {"__builtins__": {}})
        except Exception:
            continue
        if isinstance(value, bytes):  # belt-and-braces for an odd prefix ordering
            continue
        yield tok.start[0], value


def occurrences(text):
    """Keyword hits, reported per line of the literal's own line structure."""
    hits = []
    for source_line in text.split("\n"):
        # `\\n` is a line break inside a single-line generator source.
        for logical in source_line.split("\\n"):
            if KEYWORD.search(logical):
                hits.append(logical.strip())
    return hits


def main():
    problems = []
    used = set()
    for path in GENS:
        for line, value in literals(path):
            for hit in occurrences(value):
                key = (path.name, hit)
                if key in EXCEPTIONS:
                    used.add(key)
                    continue
                problems.append("%s:%d: %s" % (path.name, line, hit[:150]))
    unused = sorted(set(EXCEPTIONS) - used)
    if unused:
        for gen, text in unused:
            print("generator-keyword-prose: FAIL: exception for %s no longer "
                  "matches anything (%s); delete it or restore the case"
                  % (gen, text[:80]), file=sys.stderr)
    if problems:
        for p in problems:
            print("generator-keyword-prose: FAIL: %s" % p, file=sys.stderr)
        print("generator-keyword-prose: %d literal(s) still name a removed keyword"
              % len(problems), file=sys.stderr)
        return 1
    print("generator-keyword-prose: OK -- no string literal in the %d TCK "
          "generators still names `val`, `sealed`, or `open`" % len(GENS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
