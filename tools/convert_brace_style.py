#!/usr/bin/env python3
# Copyright (c) 2026-present Douglas Hoard
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Re-lays Solvik sources out for the physical-line brace rules of LANGUAGE_SPEC.md section 16.

The rules: an opening brace ends its line; a closing brace stands alone on its line, with only
whitespace and comments around it; a clause keyword (``else``, ``catch``, ``finally``) begins its own
line; and a ``case``/``default`` body is a braced block. Sources written before those rules used
``} else {``, one-line ``if`` expressions and unbraced case bodies, so this script performs the
mechanical rewrites:

  * ``} else``/``} catch``/``} finally``          -> the clause keyword moves to its own line
  * ``}`` shared with ``)``/``]``/``,`` or ``}``  -> each closer takes its own line
  * code before a ``}`` on its line               -> the code keeps the line, the brace takes the next
  * a body written after its ``{`` on one line    -> the body moves below the brace, ``}`` below it
  * ``{}``                                       -> ``{`` and ``}`` on their own lines
  * ``case ...:``/``default:`` bodies             -> wrapped in a brace pair, and a body written on
    the label's own line moves below the label

Text inside strings, character literals, raw strings and comments is never program structure, so the
script first marks those regions and only ever reacts to braces and colons that are code. It is
idempotent, and everything it changes is still validated by running it: ``test-corpus.sh`` compares
each program against its golden output.

Usage: tools/convert_brace_style.py FILE_OR_DIR...   (directories are searched for *.sol)
"""

import re
import sys
from pathlib import Path

INDENT = "    "
Label = tuple[str, str, str, str]  # indent, keyword, code after the colon, trailing comment


FILLER = "~"


def code_mask(text: str) -> list[bool]:
    """One flag per character: whether the character may be read as program structure.

    Comment content is masked to spaces, because a comment is not part of a line at all. String and
    character literal content is masked to a non-space filler, because it is content but never
    structure: a brace or colon inside a literal must not be read as one, while a line whose only
    content is a literal must still read as non-empty.
    """
    mask: list[object] = [True] * len(text)
    index = 0
    size = len(text)
    while index < size:
        ch = text[index]
        if ch in "\"'":
            end = index + 1
            while end < size:
                if text[end] == "\\":
                    mask[end] = False
                    end += 2
                    continue
                if text[end] == ch:
                    break
                if text[end] == "\n" and ch == "'":
                    break
                mask[end] = False
                end += 1
            mask[index] = False
            if end < size:
                mask[end] = False
            index = end + 1
            continue
        if ch == "r" and index + 1 < size and text[index + 1] in "\"#":
            probe = index + 1
            hashes = 0
            while probe < size and text[probe] == "#":
                hashes += 1
                probe += 1
            if probe < size and text[probe] == '"':
                closing = '"' + "#" * hashes
                end = text.find(closing, probe + 1)
                stop = size if end < 0 else end + len(closing)
                for position in range(index, stop):
                    mask[position] = False
                index = stop
                continue
        if ch == "/" and index + 1 < size and text[index + 1] == "/":
            end = text.find("\n", index)
            stop = size if end < 0 else end
            for position in range(index, stop):
                mask[position] = False if text[position] == "\n" else " "
            index = stop
            continue
        if ch == "/" and index + 1 < size and text[index + 1] == "*":
            end = text.find("*/", index + 2)
            stop = size if end < 0 else end + 2
            for position in range(index, stop):
                mask[position] = False if text[position] == "\n" else " "
            index = stop
            continue
        index += 1
    return mask


def blanked(line: str, mask: list[bool], start: int) -> str:
    """One line, same length, with structure-insensitive regions replaced by their marker."""
    return "".join(ch if mask[start + offset] is True else (FILLER if mask[start + offset] is False else " ") for offset, ch in enumerate(line))


def reindent(piece: tuple[str, str], indent: str) -> tuple[str, str]:
    """Move a code piece to a new indentation, keeping its view aligned with its text."""
    text, view = piece
    start = first_non_space(view)
    stop = last_non_space(view)
    if start < 0:
        return piece
    return (indent + text[start : stop + 1] + text[stop + 1 :], indent + view[start : stop + 1] + view[stop + 1 :])


def split(line: str, view: str) -> list[str]:
    """The lines one physical line becomes, as plain text."""
    return [text for text, _ in _split(line, view)]


def _split(line: str, view: str) -> list[tuple[str, str]]:
    """Split one physical line into the lines the brace rules require, as (text, view) pieces."""
    stripped = view.strip()
    if not stripped:
        return [(line, line)]
    indent = line[: len(line) - len(line.lstrip())]
    start = first_non_space(view)
    stop = last_non_space(view)
    code = line[start : stop + 1]
    vcode = view[start : stop + 1]
    comment = line[stop + 1 :]

    if vcode.startswith("}"):
        tail_view = vcode[1:].strip()
        if not tail_view:
            return [(line, line)]
        rest = (indent + " " + code[1:].strip(), indent + " " + vcode[1:].strip())
        pieces = _split(*reindent(rest, indent))
        return [(indent + "}", indent + "}"), *pieces]

    open_at = vcode.find("{")
    close_at = vcode.find("}")
    if open_at >= 0 and (close_at < 0 or open_at < close_at):
        head_code = code[: open_at + 1]
        tail_code = code[open_at + 1 :].strip()
        tail_view = vcode[open_at + 1 :].strip()
        head = (indent + head_code, indent + vcode[: open_at + 1])
        if not tail_view:
            return [(head[0] + comment, head[1] + " " * len(comment))]
        return [head, *after_open(tail_code, tail_view, indent)]

    if close_at >= 0:
        before_code = code[:close_at].rstrip()
        before_view = vcode[:close_at].rstrip()
        rest = (indent + " " + code[close_at:].strip(), indent + " " + vcode[close_at:].strip())
        pieces = _split(*reindent(rest, indent))
        prefix = [(indent + before_code, indent + before_view)] if before_code else []
        if comment:
            last_text, last_view = pieces[-1]
            pieces[-1] = (last_text + comment, last_view + " " * len(comment))
        return [*prefix, *pieces]

    return [(line, line)]


def after_open(code: str, view: str, indent: str) -> list[tuple[str, str]]:  # pieces, views aligned
    """Lay out what followed an opening brace, closing the block at the brace's own indentation."""
    depth = 0
    for index, ch in enumerate(view):
        if ch == "{":
            depth += 1
        elif ch == "}":
            if depth == 0:
                inner = (code[:index].strip(), view[:index].strip())
                after = (code[index + 1 :].strip(), view[index + 1 :].strip())
                pieces: list[tuple[str, str]] = []
                if inner[1]:
                    pieces.extend(_split(*reindent(inner, indent + INDENT)))
                pieces.append((indent + "}", indent + "}"))
                if after[1]:
                    pieces.extend(_split(*reindent(after, indent)))
                return pieces
            depth -= 1
    return _split(*reindent((code, view), indent + INDENT))


def first_non_space(view: str) -> int:
    for index, ch in enumerate(view):
        if not ch.isspace():
            return index
    return -1


def last_non_space(view: str) -> int:
    for index in range(len(view) - 1, -1, -1):
        if not view[index].isspace():
            return index
    return -1


class Label:
    """One `case ...`/`default` label line: its indent, its text, and what the line still needs."""

    def __init__(self, indent: str, keyword: str, kind: str, code: str = "", comment: str = ""):
        self.indent = indent
        self.keyword = keyword
        self.kind = kind  # 'open' (body to be wrapped), 'colon-braced' (drop the colon), 'braced'
        self.code = code
        self.comment = comment

    def header(self) -> str:
        return self.indent + self.keyword + " {" + (" " + self.comment if self.comment else "")


def case_label(text: str, view: str) -> Label | None:
    """Recognise a `case ...`/`default` label line through the code view.

    The label ends at its first structural colon or brace, read in the view: inside a string or a
    comment those characters are fillers or spaces, so a regex case label cannot be mistaken for a
    label that is already terminated. The label's text is sliced from the source line at the same
    offsets, so the view's fillers never reach the output.
    """
    start = first_non_space(view)
    if start < 0:
        return None
    mark = -1
    for index in range(start, len(view.rstrip())):
        if view[index] in ":{":
            mark = index
            break
    end = mark if mark >= 0 else last_non_space(view) + 1
    keyword = text[start:end].rstrip()
    if keyword == "default":
        pass
    elif not (keyword.startswith("case") and len(keyword) > 4 and keyword[4] in " \t"):
        return None
    if mark < 0:
        return None if end <= start else Label(indent_of(view), keyword, "open", text[start + len(keyword) : end].strip(), "")
    trailing_view = view[mark + 1 :]
    code_end = last_non_space(trailing_view)
    code = trailing_view[: code_end + 1].strip()
    marker = view[mark]
    body_text = text[mark + 1 : mark + 1 + code_end + 1].strip() if code_end >= 0 else ""
    comment = text[mark + 2 + code_end :].strip() if code_end >= 0 else text[mark + 1 :].strip()
    if marker == "{":
        return Label(indent_of(view), keyword, "braced") if not body_text else None
    if code:
        return Label(indent_of(view), keyword, "open", body_text, comment)
    return Label(indent_of(view), keyword, "open", "", comment)


def indent_of(view: str) -> str:
    return view[: len(view) - len(view.lstrip())]


def convert(text: str) -> str:
    """Re-lay out a whole source so every brace and case label obeys the section 16 rules."""
    mask = code_mask(text)
    lines = text.split("\n")
    views = [blanked(line, mask, offset) for line, offset in zip(lines, line_offsets(lines))]
    out: list[str] = []
    index = 0
    while index < len(lines):
        pieces = split(lines[index], views[index])
        label = case_label(lines[index], views[index]) if len(pieces) == 1 else None
        if label is None or label.kind == "braced":
            out.extend(pieces)
            index += 1
            continue
        if label.kind == "colon-braced":
            out.append(label.header())
            index += 1
            continue
        body: list[str] = []
        if label.code:
            body.extend(split(label.indent + INDENT + label.code, label.indent + INDENT + label.code))
        index += 1
        while index < len(lines):
            line, view = lines[index], views[index]
            if not view.strip():
                body.append(line)
                index += 1
                continue
            at_label_indent = len(line) - len(line.lstrip()) <= len(label.indent)
            code = view.strip()
            if at_label_indent and (code.startswith("case") or code.startswith("default") or code.startswith("}")):
                break
            body.extend(split(line, view))
            index += 1
        while body and not body[-1].strip():
            index -= 1
            body.pop()
        out.append(label.header())
        out.extend(body)
        out.append(label.indent + "}")
    return "\n".join(out)


def line_offsets(lines: list[str]) -> list[int]:
    offsets = [0]
    for line in lines:
        offsets.append(offsets[-1] + len(line) + 1)
    return offsets


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__.split("Usage:")[-1], file=sys.stderr)
        return 2
    changed: list[str] = []
    for argument in argv:
        path = Path(argument)
        files = sorted(path.rglob("*.sol")) if path.is_dir() else [path]
        for file in files:
            before = file.read_text()
            after = convert(before)
            if after != before:
                file.write_text(after)
                changed.append(str(file))
    for name in changed:
        print(name)
    print(f"converted {len(changed)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
