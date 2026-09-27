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
"""A genuinely independent, deliberately incomplete Solvik front end.

Purpose
-------
TCK.md acceptance criterion 11 requires that "a third party can implement the
documented protocol without Solvik Java or Truffle classes." This adapter is the
evidence for that claim: it is a real compiler-and-runner for a declared subset of
Solvik, written in pure Python standard library, importing no Solvik, GraalVM or
Truffle code, and sharing no source of truth with the implementation under test.

Why it refuses rather than guesses
----------------------------------
A second adapter is only useful as a differential partner if its answers are its
own. This front end therefore reports a language result **only** for programs it
fully understands, and reports `IMPLEMENTATION_FAILURE` for everything else. That
status is defined by the protocol as a caught internal error and is mapped by the
runner to a non-conformance exit, never to a language verdict, so an unimplemented
construct can never be mistaken for a pass, a fail, or an agreement.

It is not a conformance oracle and cannot become one: it implements a subset, so it
cannot certify the `full-language` profile. Its role is to prove the protocol is
implementable by an outsider and to provide independent second opinions on the
subset it declares.

Declared subset (everything else is refused)
--------------------------------------------
compile : whole-program include resolution and module-name shape checking, using
          only diagnostics the specification's own required-diagnostics registry
          names (`SOLV-RESOL-008`, `SOLV-RESOL-012`).
execute : top-level `val NAME = <literal>`, `print(<literal|NAME>)` and
          `println(<literal|NAME>)` over `String` and `Integer` literals only.

Known, deliberate limitation on diagnostics
-------------------------------------------
The specification states the module-name rule as a single identifier matching
`[a-z][a-z0-9]*(_[a-z0-9]+)*` that "is not a reserved word", but the specification
contains no reserved-word list. This front end therefore enforces only the
character-form part of that rule and never invents a rejection for the
reserved-word part. That is a specification gap, recorded in
`tck/IMPLEMENTATION_PLAN.md`; encoding a guessed word list would turn an
unspecified boundary into a conformance requirement.
"""

import base64
import hashlib
import json
import os
import re
import sys

PROTOCOL_VERSION = "1"
SCHEMA_VERSION = 1
IMPLEMENTATION_NAME = "reference-subset"
IMPLEMENTATION_VERSION = "1.0.0"
SPEC_VERSIONS = ["2026.09-draft"]
PROFILES = ["full-language"]
# 'compile-only' is what the mandatory profile requires; this adapter also runs
# executables for its declared subset, but declares no capability the runner would
# have to rely on, so it cannot silently stand in for a complete implementation.
CAPABILITIES = ["compile-only"]
LIMITS = {
    "maxRequestBytes": 1048576,
    "maxResponseBytes": 1048576,
    "maxCapturedOutputBytes": 1048576,
    "maxSourceTreeBytes": 16777216,
    "maxDiagnostics": 512,
    "maxArtifacts": 4096,
    "cancelGraceMs": 5000,
}

# The module-name character form, transcribed from the specification sentence in
# section 20. Anchored: an unanchored match would accept a legal prefix of an
# illegal name, which is exactly the false-accept this check exists to prevent.
MODULE_NAME_RE = re.compile(r"[a-z][a-z0-9]*(_[a-z0-9]+)*\Z")

# A `module` declaration must be the first item in the file (section 20).
MODULE_DECL_RE = re.compile(r"^\s*module\s+(\S+)\s*(?:;|\r|\n|$)")

# An `include` directive: `include <string-literal>` with an optional
# `alias <name>`, terminated by an explicit or inserted SEMI.
INCLUDE_RE = re.compile(
    r"""^[ \t]*include[ \t]+(?P<path>r?\#*"(?P<p>[^"]*)"\#*|r?\#*\'(?P<p2>[^\']*)\'\#*)"""
    r"""(?:[ \t]+alias[ \t]+(?P<alias>[A-Za-z_][A-Za-z0-9_]*))?"""
)

RESOL_INCLUDE_NOT_FOUND = ("RESOL", "SOLV-RESOL-008")
RESOL_MODULE_INVALID_NAME = ("RESOL", "SOLV-RESOL-012")


def emit(obj):
    """Write one compact, key-sorted protocol line to adapter stdout."""
    sys.stdout.write(json.dumps(obj, separators=(",", ":"), sort_keys=True) + "\n")
    sys.stdout.flush()


def _base(op, req, **fields):
    msg = {"protocolVersion": PROTOCOL_VERSION, "schemaVersion": SCHEMA_VERSION,
           "requestId": req.get("requestId"), "op": op}
    msg.update(fields)
    return msg


def describe(req):
    impl = {"name": IMPLEMENTATION_NAME, "version": IMPLEMENTATION_VERSION,
            "fingerprint": hashlib.sha256(
            (IMPLEMENTATION_NAME + "\0" + IMPLEMENTATION_VERSION).encode()).hexdigest(),
            "specVersions": list(SPEC_VERSIONS), "profiles": list(PROFILES),
            "capabilities": list(CAPABILITIES), "limits": dict(LIMITS)}
    return _base("describe", req, **{"implementation": impl})


def implementation_failure(req, op, message):
    """A caught internal error: never a language verdict.

    The runner maps this to a non-conformance exit status. It is the only honest
    answer for a construct outside the declared subset, because guessing would
    produce a verdict with no independent basis.
    """
    return _base(op, req, status="IMPLEMENTATION_FAILURE", message=message[:4096])


# ---------------------------------------------------------------- source model
class Refusal(Exception):
    """Raised when a program lies outside the subset this front end implements.

    Closed-world rule: this adapter reports COMPILE_ACCEPTED only for programs it
    has *fully* checked. Any construct it does not implement therefore raises this
    exception, which becomes IMPLEMENTATION_FAILURE -- never a silent accept and
    never a fabricated rejection.
    """


# A whole program, in the subset, is a sequence of these top-level items and
# nothing else. Anything matching OTHER means the program is outside the subset.
_COMMENT_RE = re.compile(r"^\s*(//[^\n]*)?$")
# The closing quote sits outside the capture group: capturing it would hand the
# decoder a trailing quote character that then appears in program output.
# Section 15: a normal string cannot contain an unescaped physical newline and
# supports exactly the escapes in _ESCAPES below; "any other escape is a lexical
# error", so the character class rejects a backslash that is not followed by one of
# them instead of accepting it and refusing later.
_STRING_LITERAL_RE = re.compile(r'\A"((?:[^"\\\n]|\\[\\"nrt0])*)"\Z')
_INT_LITERAL_RE = re.compile(r"\A(0|[1-9][0-9]*)\Z")
_IDENT_RE = re.compile(r"\A[A-Za-z_][A-Za-z0-9_]*\Z")
# Raw string literal, section 1's general rule: r + N '#' + '"' + content + '"' +
# exactly N '#', with the count captured and back-referenced so the delimiters must
# balance. Raw strings take no backslash escapes.
#
# Deliberate narrowing: the body excludes quotes entirely. The specification permits
# interior quotes "except when the exact closing delimiter is encountered", but a
# lazy body that lets the regex skip past a genuine closing delimiter over-accepts
# (for example `r"has"quote"`), which a conformance partner must never do. Refusing a
# raw string whose content contains a quote is therefore the safe direction: it costs
# coverage and cannot produce a wrong verdict.
_RAW_LITERAL_RE = re.compile(r'\Ar(?P<h>\#*)"(?P<body>[^"]*)"(?P=h)\Z')


def strip_comment(line):
    """Remove a trailing `//` comment from a source line outside a string literal.

    Section 1 defines `//` line comments; a `//` inside a string literal is not a
    comment, so the scan tracks literal state rather than splitting blindly.
    """
    out = []
    i = 0
    in_str = None
    while i < len(line):
        ch = line[i]
        if in_str:
            if ch == "\\":
                out.append(line[i:i + 2])
                i += 2
                continue
            if ch == in_str:
                in_str = None
            out.append(ch)
            i += 1
            continue
        if ch in "\"'":
            in_str = ch
            out.append(ch)
            i += 1
            continue
        if ch == "/" and line[i:i + 2] == "//":
            break
        out.append(ch)
        i += 1
    return "".join(out)


def decode_string_literal(text):
    """Decode a normal or raw string literal to its value, or raise Refusal."""
    raw = _RAW_LITERAL_RE.match(text)
    if raw:
        # Raw strings perform no escape processing (section 1).
        return raw.group("body")
    m = _STRING_LITERAL_RE.match(text)
    if not m:
        raise Refusal("not a supported string literal")
    # Section 15: "They support exactly `\\`, `\"`, `\n`, `\r`, `\t`, and `\0`.
    # Any other escape is a lexical error." The specification names no SOLV-* code
    # for that error, so an unsupported escape is refused (outside the subset)
    # rather than rejected with an invented diagnostic.
    escapes = {"\\": "\\", '"': '"', "n": "\n", "r": "\r", "t": "\t", "0": "\0"}
    body, out, i = m.group(1), [], 0
    while i < len(body):
        ch = body[i]
        if ch != "\\":
            out.append(ch)
            i += 1
            continue
        out.append(escapes[body[i + 1]])
        i += 2
    return "".join(out)


# ---------------------------------------------------------------- compile phase
def _canonical(root_dir, rel):
    """Canonical identity of a file, so two paths to one file are one include.

    Section 20: "Two paths or symlinks that resolve to the same file are the same
    include." Lexical normalization alone would treat `a/../b.sol` and `b.sol` as
    different files, so the filesystem's canonical path is used.
    """
    return os.path.normcase(os.path.realpath(os.path.join(root_dir, rel)))


class DiagnosticError(Exception):
    """A source-level rejection using only registry-named diagnostics."""

    def __init__(self, diagnostics):
        Exception.__init__(self, "rejected")
        self.diagnostics = diagnostics


RESOL_INCLUDE_CYCLE = ("RESOL", "SOLV-RESOL-011")


def expand(root_dir, entry_rel):
    """Expand `include` directives depth-first and left-to-right (section 20).

    Returns an ordered list of ``(relative_path, raw_line)`` in resolved-program
    order. Three rules from the specification are load-bearing here and each was
    violated by a simpler first implementation:

    * **Splice at the include position.** The target's items replace the directive,
      so an included file's items precede the *remaining* items of the including
      file. Appending them after would reorder a program's output.
    * **Expand at most once.** "A canonical physical file is expanded at most once
      per evaluated root. A later include of the same canonical file is a no-op, so
      a diamond is deterministic and an included top-level statement never runs
      twice." Without this, a diamond duplicates output.
    * **A cycle while expanding is SOLV-RESOL-011**, at the include that closes it.
      A depth limit cannot stand in for this: it would report the wrong diagnostic
      for a condition the specification names.

    The module-name check runs here, where the whole file text is available.
    """
    expanded = set()
    active = []

    def check_module_decl(rel, lines):
        """Validate a leading `module` declaration name, if the file has one.

        Section 20 makes the declaration optional and requires it to be the *first*
        item in the file, so only the first non-blank line is examined: a `module`
        keyword anywhere else is not a declaration, and the item analyzer refuses it
        rather than treating it as one. Only the character-form half of the naming
        rule is enforceable -- the specification's "is not a reserved word" clause has
        no word list anywhere in the document, so this front end never rejects on that
        basis and records the gap instead of guessing a list.
        """
        for raw in lines:
            code = strip_comment(raw).strip()
            if not code:
                continue
            m = MODULE_DECL_RE.match(code)
            if not m:
                return                      # no module declaration: default module
            name = m.group(1).rstrip(";").strip()
            if not MODULE_NAME_RE.match(name):
                raise DiagnosticError([(RESOL_MODULE_INVALID_NAME, rel,
                                        "module name %r is not a single lowercase "
                                        "identifier" % name)])
            return

    def walk(rel, including_rel, shown):
        canon = _canonical(root_dir, rel)
        # Order matters and encodes two different specification rules. `active` is the
        # set of files *currently being expanded*: meeting one is the cycle case the
        # sentence "If a canonical file is encountered while it is still being expanded,
        # report SOLV-RESOL-011" names. Only a file that has *finished* expanding may be
        # a no-op. Testing `expanded` first would swallow a self-include -- the very
        # case the cycle rule exists to report -- because a file is added to `expanded`
        # before its own includes are walked.
        if canon in active:
            raise DiagnosticError([(RESOL_INCLUDE_CYCLE, including_rel or rel,
                                    'include "%s" closes a cycle' % shown)])
        if canon in expanded:
            return []                      # a later include of the same file is a no-op
        try:
            with open(os.path.join(root_dir, rel), "r", encoding="utf-8") as handle:
                text = handle.read()
        except (OSError, UnicodeDecodeError):
            if including_rel is None:
                # The runner staged the entry point; failing to read it is internal.
                raise Refusal("cannot read entry point")
            raise DiagnosticError([(RESOL_INCLUDE_NOT_FOUND, including_rel,
                                    'include "%s" names no readable file' % shown)])
        expanded.add(canon)
        active.append(canon)
        lines = text.split("\n")
        check_module_decl(rel, lines)
        items = []
        for raw in lines:
            code = strip_comment(raw)
            m = INCLUDE_RE.match(code)
            if not m:
                if code.strip():
                    items.append((rel, raw))
                continue
            target = m.group("p") if m.group("p") is not None else m.group("p2")
            if target is None:
                raise Refusal("unsupported include path spelling")
            # An include path resolves relative to the *including* file.
            child = os.path.normpath(os.path.join(os.path.dirname(rel), target))
            if child.startswith("..") or os.path.isabs(child):
                raise Refusal("include path escapes the source tree")
            if not os.path.isfile(os.path.join(root_dir, child)):
                raise DiagnosticError([(RESOL_INCLUDE_NOT_FOUND, rel,
                                        'include "%s" names no file' % target)])
            items.extend(walk(child, rel, target))   # splice at the include position
        active.pop()
        return items

    return walk(entry_rel, None, entry_rel)


# ----------------------------------------------------------------- value model
# Top-level items the execute subset understands. Each is (kind, payload).
_VAL_RE = re.compile(r"\Aval\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+?)\s*;?\s*\Z")
_PRINT_RE = re.compile(r"\A(print|println)\s*\(\s*(.+?)\s*\)\s*;?\s*\Z")
# Literals whose display section 6 states outright: "Boolean values as `true` or
# `false`" and "`null` displays as `null`". Spelled as tokens rather than values so
# the display text is the specification's own.
_KEYWORD_LITERAL = {"true": "true", "false": "false", "null": "null"}


def _literal_or_ref(token, env):
    """Evaluate one `print`/`println` argument: a literal or a prior `val` name."""
    if _IDENT_RE.match(token) and token in env:
        return env[token]
    if _IDENT_RE.match(token) and token in _KEYWORD_LITERAL:
        return _KEYWORD_LITERAL[token]
    if _INT_LITERAL_RE.match(token):
        return int(token)
    if token.startswith("r\"") or token.startswith("r#") or token.startswith('"'):
        return decode_string_literal(token)
    raise Refusal("print argument outside the subset: %r" % token)


def analyze(items):
    """Analyze the expanded program as the execute subset, or raise Refusal.

    Returns an ordered list of executable operations. A program is accepted only if
    *every* top-level item is understood; an unimplemented construct refuses the
    whole program rather than being skipped, so a partial accept is impossible and
    the compile phase stays complete with respect to what execute will run.

    `items` arrives in resolved-program order from `expand`, so statement order here
    is the specification's expansion order -- which section 20 makes observable,
    because the expanded executable top-level statements form the one implicit main.
    """
    ops = []
    declared = set()
    for rel, raw in items:
        code = strip_comment(raw).strip()
        if not code:
            continue
        if INCLUDE_RE.match(code):
            continue                       # spliced away; the directive is not an item
        if MODULE_DECL_RE.match(code):
            if declared:
                # Section 20: the declaration must be the first item in the file. A
                # `module` keyword after other items is not a declaration, and this
                # front end cannot verify what it means, so it refuses.
                raise Refusal("'module' is not the first item of its file")
            continue
        m = _VAL_RE.match(code)
        if m:
            name, rhs = m.group(1), m.group(2)
            if name in declared:
                # Refused rather than judged: the redeclaration rules for top-level
                # bindings of the implicit main are not implemented here, so claiming
                # either verdict would be unfounded.
                raise Refusal("repeated binding %r" % name)
            declared.add(name)
            if _INT_LITERAL_RE.match(rhs):
                ops.append(("val", rel, name, int(rhs)))
                continue
            if _starts_literal(rhs):
                ops.append(("val", rel, name, decode_string_literal(rhs)))
                continue
            raise Refusal("initializer outside the subset: %r" % rhs[:80])
        m = _PRINT_RE.match(code)
        if m:
            arg = m.group(2)
            # Checking resolvability here is what keeps compile complete: accepting a
            # program only to refuse it at execute would let a COMPILE_SUCCESS oracle
            # pass against a program this front end cannot actually run.
            if _IDENT_RE.match(arg) and (arg in declared or arg in _KEYWORD_LITERAL):
                ops.append(("print", rel, m.group(1), arg))
                continue
            if _INT_LITERAL_RE.match(arg) or _starts_literal(arg):
                if _starts_literal(arg):
                    decode_string_literal(arg)   # validate now, not at execute
                ops.append(("print", rel, m.group(1), arg))
                continue
            raise Refusal("print argument outside the subset: %r" % arg[:80])
        raise Refusal("top-level item outside the subset: %r" % code[:80])
    return ops


def _starts_literal(token):
    """True for the literal spellings this subset decodes (normal or raw string)."""
    return token.startswith('"') or token.startswith('r"') or token.startswith("r#")


# In-process compile-to-execute binding. One adapter subprocess is launched per
# test, so this map never outlives a single test's compile/execute pair.
_ARTIFACTS = {}


def compile_(req):
    """Static analysis for the declared subset."""
    workspace = req.get("workspace")
    entry = req.get("entryPoint")
    if not workspace or not entry:
        return implementation_failure(req, "compile", "compile request incomplete")
    try:
        items = expand(workspace, entry)
        analyze(items)
    except DiagnosticError as exc:
        diags = []
        for (family, code), rel, text in exc.diagnostics:
            diags.append({"family": family, "code": code, "file": rel, "text": text[:4096]})
        return _base("compile", req, status="COMPILE_REJECTED", diagnostics=diags[:LIMITS["maxDiagnostics"]])
    except Refusal as exc:
        return implementation_failure(req, "compile", "outside declared subset: %s" % exc)
    except (OSError, ValueError, KeyError, IndexError) as exc:
        return implementation_failure(req, "compile", "internal error: %s" % exc)
    # The handle binds the accepted program to the exact input-tree digest the runner
    # staged, so execute can only ever run what this compile verified.
    handle = hashlib.sha256(
        (req.get("inputTreeDigest", "") + ":" + entry).encode()).hexdigest()
    # The subset needs no materialized artifact; the handle still binds the exact
    # input tree the runner staged, so a mutated tree cannot be executed.
    _ARTIFACTS[handle] = items
    return _base("compile", req, status="COMPILE_ACCEPTED",
                 artifactHandle=handle, artifactManifest=[])


def execute(req):
    """Run the accepted subset program, capturing guest output as base64 fields."""
    handle = req.get("artifactHandle")
    items = _ARTIFACTS.get(handle)
    if items is None:
        # No accepted compile in this process for that handle: refuse rather than
        # run something the compile phase never verified.
        return implementation_failure(req, "execute", "no verified artifact for handle")
    out = []
    env = {}
    try:
        for op in analyze(items):
            if op[0] == "val":
                env[op[2]] = op[3]
            else:
                _, _, func, arg = op
                value = _literal_or_ref(arg, env)
                # Section 6 fixes display for every value the subset can produce:
                # strings as their contents, integers in decimal, and the keyword
                # literals above as their own text, so no ad-hoc formatting is left
                # to the adapter.
                text = value if isinstance(value, str) else str(value)
                # Section 6: "`println` appends the platform line separator." Not a
                # bare newline -- that difference is exactly what the protocol's
                # `platform-line-separator` normalization exists to reconcile.
                out.append(text + (os.linesep if func == "println" else ""))
    except Refusal as exc:
        return implementation_failure(req, "execute", "outside declared subset: %s" % exc)
    except (OSError, ValueError, KeyError, IndexError) as exc:
        return implementation_failure(req, "execute", "internal error: %s" % exc)
    payload = "".join(out).encode("utf-8")
    return _base("execute", req, status="NORMAL_EXIT", languageExit=0,
                 stdoutBase64=base64.b64encode(payload).decode(),
                 stderrBase64=base64.b64encode(b"").decode())


def main():
    # `--fingerprint` lets a config generator ask this adapter for its own identity
    # instead of recomputing it, so the two cannot drift. This mirrors the launcher
    # adapter's contract; a config whose fingerprint disagreed with `describe` would
    # be rejected by the runner, and duplicating the hash here is how that happens.
    if len(sys.argv) == 2 and sys.argv[1] == "--fingerprint":
        sys.stdout.write(describe({"requestId": 0})["implementation"]["fingerprint"] + "\n")
        sys.exit(0)
    for line in sys.stdin:
        line = line.strip("\r\n")
        if not line:
            continue
        try:
            req = json.loads(line)
        except ValueError:
            # An undecodable request is a protocol breach; exiting non-zero lets the
            # runner record an infrastructure event rather than a language result.
            sys.exit(5)
        op = req.get("op")
        if op == "describe":
            emit(describe(req))
        elif op == "compile":
            emit(compile_(req))
        elif op == "execute":
            emit(execute(req))
        else:
            sys.exit(5)
    sys.exit(0)


if __name__ == "__main__":
    main()
