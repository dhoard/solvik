#!/usr/bin/env python3
"""Solvik semantic reference interpreter.

Purpose
=======
This file is intentionally a *semantic* implementation of Solvik, not an
architectural port of the Go compiler/bytecode VM.  It is designed to be easy
for humans and AI agents to read while evolving the language.

Pipeline:

    source -> lexer -> parser -> semantic model -> tree-walking evaluator

The implementation keeps language rules close to the code that enforces them.
The normative external sources remain LANGUAGE.md and Solvik's executable
conformance tests. The Go implementation is the executable behavior reference
until the Rust and Python implementations reach complete parity.

Semantic design priorities
--------------------------
1. LANGUAGE.md semantics win over implementation convenience.
2. Observable behavior matters; Go bytecode/VM architecture does not.
3. Immutable bindings and struct receiver mutability are separate concepts.
4. Struct values cross ordinary read/assignment/call/return boundaries by value.
5. `??` short-circuits on null only; false, zero, empty string/list remain values.
6. Numeric widening is byte -> int -> float; narrowing is explicit.
7. Traits are structural contracts and dispatch to the concrete struct method.
8. Catchable language faults use SolvikExceptionValue/RuntimeSignal only.
9. Standard-library behavior is kept in one explicit semantic table.
10. Prefer boring, local code over abstractions that obscure a language rule.

Python 3.11+; standard library only.
"""
from __future__ import annotations

import argparse
import base64 as py_base64
import copy
import hashlib
import math as py_math
import os
import pathlib
import random as py_random
import re
import secrets as py_secrets
import shutil
import subprocess
import sys
import tempfile
import time as py_time
import urllib.request
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Any, Callable, Iterable, Optional

REFERENCE_VERSION = "development"


# =============================================================================
# Diagnostics
# =============================================================================

@dataclass(frozen=True)
class SourcePos:
    file: str
    line: int
    column: int

    def __str__(self) -> str:
        return f"{self.file}:{self.line}:{self.column}"


class SolvikError(Exception):
    """Base class for source, semantic, and uncaught runtime errors."""


class ParseError(SolvikError):
    def __init__(self, pos: SourcePos, message: str):
        super().__init__(f"{pos}: parse error: {message}")
        self.pos = pos
        self.message = message


class DiagnosticError(SolvikError):
    """Compile-time diagnostic compatible with the Go and Rust CLIs."""

    def __init__(self, code: str, pos: SourcePos, message: str, span_length: int = 1, phase: str = "compilation"):
        super().__init__(message)
        self.code = code
        self.pos = pos
        self.message = message
        self.span_length = max(1, span_length)
        self.phase = phase

    def __str__(self) -> str:
        path = pathlib.Path(self.pos.file)
        parts = path.parts
        diagnostic_name = "/".join(parts[-2:]) if len(parts) >= 2 else str(path)
        input_name = self.pos.file
        try:
            source_line = path.read_text(encoding="utf-8").splitlines()[self.pos.line - 1]
        except (OSError, IndexError):
            source_line = ""
        width = len(str(self.pos.line))
        gutter = " " * (width + 2) + "|"
        primary = [
            f"error {self.code}: {self.message}",
            f"  --> {diagnostic_name}:{self.pos.line}:{self.pos.column}",
            f"{gutter}",
            f" {self.pos.line} | {source_line}",
            f"{gutter} {' ' * (self.pos.column - 1)}{'^' * self.span_length}",
            f"{gutter}",
        ]
        if self.code in ("L016", "L017"):
            end_col = len(source_line)
            primary.extend([
                "error L005: newline in string literal",
                f"  --> {diagnostic_name}:{self.pos.line}:{end_col}",
                f"{gutter}",
                f" {self.pos.line} | {source_line}",
                f"{gutter} {' ' * (end_col - 1)}^",
                f"{gutter}",
            ])
        primary.append(f"error: {self.phase} error in {input_name}" if self.phase in ("lex", "parse") else "error: compilation failed")
        return "\n".join(primary)


@dataclass
class SolvikExceptionValue:
    message: str
    trace: str = ""
    code: str = ""


class RuntimeSignal(Exception):
    """Internal Python exception used to model a catchable Solvik exception."""

    def __init__(self, value: SolvikExceptionValue):
        super().__init__(value.message)
        self.value = value


class ReturnSignal(Exception):
    def __init__(self, value: Any):
        self.value = value


class BreakSignal(Exception):
    pass


class ContinueSignal(Exception):
    pass


def runtime_error(message: str, code: str = "") -> RuntimeSignal:
    return RuntimeSignal(SolvikExceptionValue(message=message, code=code))


# =============================================================================
# Lexer
# =============================================================================

class TK(Enum):
    EOF = auto()
    NEWLINE = auto()
    IDENT = auto()
    INT = auto()
    FLOAT = auto()
    STRING = auto()
    CHAR = auto()

    PACKAGE = auto(); USE = auto(); STRUCT = auto(); TRAIT = auto(); ENUM = auto(); FUNC = auto()
    MUT = auto(); PUB = auto(); IF = auto(); ELSE = auto(); WHILE = auto(); FOR = auto(); IN = auto()
    SWITCH = auto(); CASE = auto(); DEFAULT = auto(); TRY = auto(); CATCH = auto(); FINALLY = auto()
    THROW = auto(); RETURN = auto(); BREAK = auto(); CONTINUE = auto()
    TRUE = auto(); FALSE = auto(); NULL = auto()

    LPAREN = auto(); RPAREN = auto(); LBRACE = auto(); RBRACE = auto(); LBRACKET = auto(); RBRACKET = auto()
    COMMA = auto(); COLON = auto(); SEMI = auto(); DOT = auto(); ELLIPSIS = auto(); QUESTION = auto()
    PLUS = auto(); MINUS = auto(); STAR = auto(); SLASH = auto(); PERCENT = auto()
    BANG = auto(); TILDE = auto(); AMP = auto(); PIPE = auto(); CARET = auto()
    LT = auto(); LE = auto(); GT = auto(); GE = auto(); EQEQ = auto(); NE = auto(); ASSIGN = auto()
    ANDAND = auto(); OROR = auto(); SHL = auto(); SHR = auto(); CONCAT = auto(); COALESCE = auto(); ARROW = auto()


KEYWORDS = {
    "package": TK.PACKAGE, "use": TK.USE, "struct": TK.STRUCT, "trait": TK.TRAIT,
    "enum": TK.ENUM, "func": TK.FUNC, "mut": TK.MUT, "pub": TK.PUB,
    "if": TK.IF, "else": TK.ELSE, "while": TK.WHILE, "for": TK.FOR, "in": TK.IN,
    "switch": TK.SWITCH, "case": TK.CASE, "default": TK.DEFAULT,
    "try": TK.TRY, "catch": TK.CATCH, "finally": TK.FINALLY, "throw": TK.THROW,
    "return": TK.RETURN, "break": TK.BREAK, "continue": TK.CONTINUE,
    "true": TK.TRUE, "false": TK.FALSE, "null": TK.NULL,
}


@dataclass(frozen=True)
class Token:
    kind: TK
    text: str
    value: Any
    pos: SourcePos


class Lexer:
    """Small explicit lexer. Newlines are tokens so statement termination is visible."""

    def __init__(self, source: str, filename: str):
        self.source = source
        self.filename = filename
        self.i = 0
        self.line = 1
        self.col = 1

    def pos(self) -> SourcePos:
        return SourcePos(self.filename, self.line, self.col)

    def peek(self, n: int = 0) -> str:
        j = self.i + n
        return self.source[j] if j < len(self.source) else "\0"

    def advance(self) -> str:
        c = self.peek()
        if c == "\0":
            return c
        self.i += 1
        if c == "\n":
            self.line += 1
            self.col = 1
        else:
            self.col += 1
        return c

    def match(self, text: str) -> bool:
        if self.source.startswith(text, self.i):
            for _ in text:
                self.advance()
            return True
        return False

    def error(self, pos: SourcePos, message: str) -> ParseError:
        return ParseError(pos, message)

    def tokens(self) -> list[Token]:
        out: list[Token] = []
        while True:
            t = self.next_token()
            out.append(t)
            if t.kind is TK.EOF:
                return out

    def next_token(self) -> Token:
        while True:
            p = self.pos()
            c = self.peek()
            if c == "\0":
                return Token(TK.EOF, "", None, p)
            if c in " \t\r":
                self.advance(); continue
            if c == "\n":
                self.advance(); return Token(TK.NEWLINE, "\n", None, p)
            if self.source.startswith("//", self.i):
                while self.peek() not in ("\n", "\0"):
                    self.advance()
                continue
            if self.source.startswith("/*", self.i):
                self._block_comment(p); continue
            break

        p = self.pos()

        # Rust-style raw string: r"...", r#"..."#, r##"..."##, ...
        if c == "r" and (self.peek(1) == '"' or self.peek(1) == "#"):
            raw = self._try_raw_string(p)
            if raw is not None:
                return raw

        if c.isalpha() or c == "_":
            start = self.i
            while self.peek().isalnum() or self.peek() == "_": self.advance()
            text = self.source[start:self.i]
            kind = KEYWORDS.get(text, TK.IDENT)
            value = {TK.TRUE: True, TK.FALSE: False, TK.NULL: None}.get(kind, text)
            return Token(kind, text, value, p)

        if c.isdigit():
            return self._number(p)
        if c == '"':
            return self._quoted(p, '"', TK.STRING)
        if c == "'":
            t = self._quoted(p, "'", TK.CHAR)
            if len(t.value) != 1:
                raise self.error(p, "character literal must contain exactly one Unicode character")
            return t

        multi = [
            ("...", TK.ELLIPSIS), ("??", TK.COALESCE), ("..", TK.CONCAT), ("->", TK.ARROW),
            ("==", TK.EQEQ), ("!=", TK.NE), ("<=", TK.LE), (">=", TK.GE),
            ("&&", TK.ANDAND), ("||", TK.OROR), ("<<", TK.SHL), (">>", TK.SHR),
        ]
        for text, kind in multi:
            if self.match(text):
                return Token(kind, text, None, p)

        singles = {
            "(": TK.LPAREN, ")": TK.RPAREN, "{": TK.LBRACE, "}": TK.RBRACE,
            "[": TK.LBRACKET, "]": TK.RBRACKET, ",": TK.COMMA, ":": TK.COLON,
            ";": TK.SEMI, ".": TK.DOT, "?": TK.QUESTION, "+": TK.PLUS,
            "-": TK.MINUS, "*": TK.STAR, "/": TK.SLASH, "%": TK.PERCENT,
            "!": TK.BANG, "~": TK.TILDE, "&": TK.AMP, "|": TK.PIPE,
            "^": TK.CARET, "<": TK.LT, ">": TK.GT, "=": TK.ASSIGN,
        }
        if c in singles:
            self.advance()
            return Token(singles[c], c, None, p)
        raise self.error(p, f"unexpected character {c!r}")

    def _block_comment(self, p: SourcePos) -> None:
        self.match("/*")
        depth = 1
        while depth:
            if self.peek() == "\0": raise self.error(p, "unterminated block comment")
            if self.source.startswith("/*", self.i): self.match("/*"); depth += 1
            elif self.source.startswith("*/", self.i): self.match("*/"); depth -= 1
            else: self.advance()

    def _try_raw_string(self, p: SourcePos) -> Optional[Token]:
        save = (self.i, self.line, self.col)
        self.advance()  # r
        hashes = 0
        while self.peek() == "#": self.advance(); hashes += 1
        if self.peek() != '"':
            self.i, self.line, self.col = save
            return None
        self.advance()
        start = self.i
        end_marker = '"' + ('#' * hashes)
        while not self.source.startswith(end_marker, self.i):
            if self.peek() == "\0": raise self.error(p, "unterminated raw string")
            self.advance()
        value = self.source[start:self.i]
        self.match(end_marker)
        return Token(TK.STRING, value, value, p)

    def _quoted(self, p: SourcePos, quote: str, kind: TK) -> Token:
        self.advance()
        chars: list[str] = []
        escapes = {"n":"\n", "t":"\t", "r":"\r", "\\":"\\", '"':'"', "'":"'", "0":"\0"}
        while True:
            c = self.peek()
            if c in ("\0", "\n"):
                raise self.error(p, "unterminated string/character literal")
            if c == quote:
                self.advance(); break
            if c != "\\":
                chars.append(self.advance()); continue
            self.advance()
            e = self.advance()
            if e in escapes:
                chars.append(escapes[e]); continue
            widths = {"x":2, "u":4, "U":8}
            if e in widths:
                digits = ""
                for _ in range(widths[e]): digits += self.advance()
                if not re.fullmatch(r"[0-9A-Fa-f]+", digits):
                    raise DiagnosticError("L017", p, "invalid hexadecimal digits in \\x escape", 5, "lex")
                chars.append(chr(int(digits, 16))); continue
            raise DiagnosticError("L016", p, f"unknown escape sequence '\\{e}'", 3, "lex")
        value = "".join(chars)
        return Token(kind, value, value, p)

    def _number(self, p: SourcePos) -> Token:
        start = self.i
        # base-prefixed integers
        if self.peek() == "0" and self.peek(1).lower() in "xbo":
            self.advance(); prefix = self.advance().lower()
            valid = {"x":"0123456789abcdefABCDEF", "b":"01", "o":"01234567"}[prefix]
            while self.peek() in valid or self.peek() == "_": self.advance()
            text = self.source[start:self.i]
            self._validate_numeric_underscores(text, p)
            return Token(TK.INT, text, int(text.replace("_", ""), {"x":16,"b":2,"o":8}[prefix]), p)

        saw_dot = False
        saw_exp = False
        while True:
            c = self.peek()
            if c.isdigit() or c == "_": self.advance(); continue
            if c == "." and self.peek(1) != "." and not saw_dot and not saw_exp:
                saw_dot = True; self.advance(); continue
            if c in "eE" and not saw_exp:
                saw_exp = True; self.advance()
                if self.peek() in "+-": self.advance()
                continue
            break
        text = self.source[start:self.i]
        self._validate_numeric_underscores(text, p)
        clean = text.replace("_", "")
        if saw_dot or saw_exp:
            return Token(TK.FLOAT, text, float(clean), p)
        return Token(TK.INT, text, int(clean), p)

    @staticmethod
    def _validate_numeric_underscores(text: str, p: SourcePos) -> None:
        if "_" not in text:
            return
        lower = text.lower()
        if lower.startswith("0x"):
            digits = set("0123456789abcdefABCDEF")
        elif lower.startswith("0b"):
            digits = set("01")
        elif lower.startswith("0o"):
            digits = set("01234567")
        else:
            digits = set("0123456789")
        for i, c in enumerate(text):
            if c != "_":
                continue
            before = text[i - 1] if i else ""
            after = text[i + 1] if i + 1 < len(text) else ""
            if before not in digits or after not in digits:
                raise ParseError(p, f"numeric underscores must occur between digits: {text}")


# =============================================================================
# AST and semantic types
# =============================================================================

@dataclass(frozen=True)
class TypeRef:
    name: str
    args: tuple["TypeRef", ...] = ()
    nullable: bool = False

    def __str__(self) -> str:
        body = self.name
        if self.args: body += "<" + ", ".join(map(str, self.args)) + ">"
        return body + ("?" if self.nullable else "")

    def nonnull(self) -> "TypeRef":
        return TypeRef(self.name, self.args, False)


ANY_T = TypeRef("any")
VOID_T = TypeRef("void")
EXCEPTION_T = TypeRef("exception")


@dataclass
class Program:
    package: str
    uses: list["UseDecl"]
    declarations: list[Any]
    file: str

@dataclass
class UseDecl:
    scheme: str
    value: str
    checksum: Optional[str]
    insecure: bool
    pos: SourcePos

@dataclass
class Param:
    name: str
    type: TypeRef
    variadic: bool = False
    pos: Optional[SourcePos] = None

@dataclass
class FunctionDecl:
    name: str
    params: list[Param]
    return_type: TypeRef
    body: Optional["Block"]
    pos: SourcePos
    public: bool = False
    mutating: bool = False
    owner_struct: Optional[str] = None

@dataclass
class FieldDecl:
    name: str
    type: TypeRef
    public: bool
    mutable: bool
    pos: Optional[SourcePos] = None

@dataclass
class StructDecl:
    name: str
    fields: list[FieldDecl]
    methods: list[FunctionDecl]
    pos: SourcePos

@dataclass
class TraitDecl:
    name: str
    methods: list[FunctionDecl]
    pos: SourcePos

@dataclass
class EnumMember:
    name: str
    value: Optional[int]

@dataclass
class EnumDecl:
    name: str
    members: list[EnumMember]
    pos: SourcePos

@dataclass
class Block:
    statements: list[Any]
    pos: SourcePos

@dataclass
class VarDecl:
    name: str; type: TypeRef; value: Any; mutable: bool; pos: SourcePos
@dataclass
class ExprStmt:
    expr: Any; pos: SourcePos
@dataclass
class IfStmt:
    condition: Any; then_block: Block; else_branch: Optional[Any]; pos: SourcePos
@dataclass
class WhileStmt:
    condition: Any; body: Block; pos: SourcePos
@dataclass
class ForStmt:
    names: list[str]; iterable: Any; body: Block; pos: SourcePos
@dataclass
class SwitchCase:
    expr: Optional[Any]; body: Block
@dataclass
class SwitchStmt:
    value: Any; cases: list[SwitchCase]; pos: SourcePos
@dataclass
class TryStmt:
    try_block: Block; catch_name: Optional[str]; catch_type: Optional[TypeRef]; catch_block: Optional[Block]; finally_block: Optional[Block]; pos: SourcePos
@dataclass
class ThrowStmt:
    value: Any; pos: SourcePos
@dataclass
class ReturnStmt:
    value: Optional[Any]; pos: SourcePos
@dataclass
class BreakStmt:
    pos: SourcePos
@dataclass
class ContinueStmt:
    pos: SourcePos

@dataclass
class Literal:
    value: Any; literal_kind: str; pos: SourcePos
@dataclass
class Name:
    name: str; pos: SourcePos
@dataclass
class Unary:
    op: str; expr: Any; pos: SourcePos
@dataclass
class Binary:
    left: Any; op: str; right: Any; pos: SourcePos
@dataclass
class Assign:
    target: Any; value: Any; pos: SourcePos
@dataclass
class CallArg:
    expr: Any; spread: bool = False
@dataclass
class Call:
    callee: Any; args: list[CallArg]; pos: SourcePos
@dataclass
class Member:
    obj: Any; name: str; pos: SourcePos
@dataclass
class Index:
    obj: Any; index: Any; pos: SourcePos
@dataclass
class ListExpr:
    items: list[Any]; pos: SourcePos
@dataclass
class MapExpr:
    items: list[tuple[Any, Any]]; pos: SourcePos
@dataclass
class StructExpr:
    type_name: str; fields: list[tuple[str, Any]]; pos: SourcePos


# =============================================================================
# Parser
# =============================================================================

class Parser:
    """Recursive-descent declarations/statements plus Pratt expressions."""

    def __init__(self, tokens: list[Token]):
        self.ts = tokens
        self.i = 0

    def cur(self) -> Token: return self.ts[self.i]
    def peek(self, n: int = 1) -> Token: return self.ts[min(self.i+n, len(self.ts)-1)]
    def at(self, kind: TK) -> bool: return self.cur().kind is kind
    def advance(self) -> Token:
        t = self.cur()
        if t.kind is not TK.EOF: self.i += 1
        return t
    def match(self, *kinds: TK) -> Optional[Token]:
        if self.cur().kind in kinds: return self.advance()
        return None
    def expect(self, kind: TK, message: str = "") -> Token:
        if not self.at(kind):
            raise ParseError(self.cur().pos, message or f"expected {kind.name}, found {self.cur().text!r}")
        return self.advance()
    def skip_terms(self) -> None:
        while self.match(TK.NEWLINE, TK.SEMI): pass
    def skip_newlines(self) -> None:
        while self.match(TK.NEWLINE): pass

    def parse(self) -> Program:
        self.skip_terms()
        self.expect(TK.PACKAGE, "source file must begin with package declaration")
        package = self.expect(TK.IDENT, "expected package name").text
        self.skip_terms()
        uses: list[UseDecl] = []
        while self.at(TK.USE):
            uses.append(self.parse_use()); self.skip_terms()
        decls: list[Any] = []
        while not self.at(TK.EOF):
            self.skip_terms()
            if self.at(TK.EOF): break
            decls.append(self.parse_top_decl())
            self.skip_terms()
        filename = self.ts[0].pos.file if self.ts else "<source>"
        return Program(package, uses, decls, filename)

    def parse_use(self) -> UseDecl:
        p = self.expect(TK.USE).pos
        scheme = self.expect(TK.IDENT, "expected dependency scheme (file or url)").text
        self.expect(TK.COLON)
        # File paths may be dotted identifiers or quoted/raw strings. URLs are
        # read token-wise until a terminator to keep ':' '/' '?' etc available.
        if self.at(TK.STRING):
            value = self.advance().value
        else:
            pieces: list[str] = []
            while self.cur().kind not in (TK.NEWLINE, TK.SEMI, TK.EOF):
                # optional flags begin with checksum:/insecure:
                if self.at(TK.IDENT) and self.cur().text in ("checksum", "insecure") and self.peek().kind is TK.COLON:
                    break
                pieces.append(self.advance().text)
            value = "".join(pieces)
        checksum = None; insecure = False
        while self.cur().kind not in (TK.NEWLINE, TK.SEMI, TK.EOF):
            key = self.expect(TK.IDENT).text; self.expect(TK.COLON)
            if key == "checksum":
                # normative spelling is checksum:sha256:<hex>
                alg = self.expect(TK.IDENT).text; self.expect(TK.COLON)
                val = self.advance().text
                if alg != "sha256": raise ParseError(p, "only sha256 checksums are supported")
                checksum = val
            elif key == "insecure":
                t = self.advance(); insecure = t.kind is TK.TRUE or t.text == "true"
            else: raise ParseError(p, f"unknown use flag {key}")
        return UseDecl(scheme, value, checksum, insecure, p)

    def parse_top_decl(self) -> Any:
        public = bool(self.match(TK.PUB))
        if self.at(TK.FUNC): return self.parse_function(public=public)
        if self.at(TK.STRUCT): return self.parse_struct()
        if self.at(TK.TRAIT): return self.parse_trait()
        if self.at(TK.ENUM): return self.parse_enum()
        raise ParseError(self.cur().pos, "expected top-level func, struct, trait, or enum")

    def expect_type_gt(self) -> None:
        """Consume one `>` while parsing a type.

        The lexer normally recognizes `>>` as the shift operator. In a type
        context the same bytes may close two generic levels, and Solvik
        explicitly permits `list<list<int>>` without whitespace.
        """
        if self.at(TK.GT):
            self.advance(); return
        if self.at(TK.SHR):
            t = self.cur()
            self.ts[self.i] = Token(TK.GT, ">", None, t.pos)
            self.ts.insert(self.i + 1, Token(TK.GT, ">", None, t.pos))
            self.advance(); return
        raise ParseError(self.cur().pos, "expected '>' to close generic type")

    def parse_type(self) -> TypeRef:
        name = self.expect(TK.IDENT, "expected type name").text
        args: list[TypeRef] = []
        if self.match(TK.LT):
            self.skip_newlines()
            args.append(self.parse_type())
            while self.match(TK.COMMA): self.skip_newlines(); args.append(self.parse_type())
            self.skip_newlines(); self.expect_type_gt()
        nullable = bool(self.match(TK.QUESTION))
        return TypeRef(name, tuple(args), nullable)

    def parse_function(self, public: bool = False, mutating: bool = False, owner: Optional[str] = None, body_required: bool = True) -> FunctionDecl:
        p = self.expect(TK.FUNC).pos
        name = self.expect(TK.IDENT, "expected function name").text
        self.expect(TK.LPAREN); self.skip_newlines()
        params: list[Param] = []
        if not self.at(TK.RPAREN):
            while True:
                param_token = self.expect(TK.IDENT, "expected parameter name")
                pname = param_token.text
                self.expect(TK.COLON)
                variadic = bool(self.match(TK.ELLIPSIS))
                ptype = self.parse_type()
                params.append(Param(pname, ptype, variadic, param_token.pos))
                self.skip_newlines()
                if not self.match(TK.COMMA): break
                self.skip_newlines()
                if self.at(TK.RPAREN): break
        self.expect(TK.RPAREN)
        rtype = VOID_T
        if self.match(TK.ARROW): rtype = self.parse_type()
        self.skip_newlines()
        body = self.parse_block() if body_required else None
        return FunctionDecl(name, params, rtype, body, p, public, mutating, owner)

    def parse_struct(self) -> StructDecl:
        p = self.expect(TK.STRUCT).pos; name = self.expect(TK.IDENT).text
        self.skip_newlines(); self.expect(TK.LBRACE); self.skip_terms()
        fields: list[FieldDecl] = []; methods: list[FunctionDecl] = []
        while not self.at(TK.RBRACE):
            public = bool(self.match(TK.PUB)); mut = bool(self.match(TK.MUT))
            if self.at(TK.FUNC):
                methods.append(self.parse_function(public=public, mutating=mut, owner=name)); self.skip_terms(); continue
            field_token = self.expect(TK.IDENT, "expected struct field or method")
            fname = field_token.text
            self.expect(TK.COLON); ftype = self.parse_type()
            fields.append(FieldDecl(fname, ftype, public, mut, field_token.pos))
            self.match(TK.COMMA); self.skip_terms()
        self.expect(TK.RBRACE)
        return StructDecl(name, fields, methods, p)

    def parse_trait(self) -> TraitDecl:
        p = self.expect(TK.TRAIT).pos; name = self.expect(TK.IDENT).text
        self.skip_newlines(); self.expect(TK.LBRACE); self.skip_terms()
        methods: list[FunctionDecl] = []
        while not self.at(TK.RBRACE):
            mut = bool(self.match(TK.MUT))
            self.expect(TK.FUNC)
            # Parse signature directly; trait methods have no body.
            mp = self.ts[self.i-1].pos
            mname = self.expect(TK.IDENT).text
            self.expect(TK.LPAREN); params: list[Param] = []
            if not self.at(TK.RPAREN):
                while True:
                    param_token = self.expect(TK.IDENT); pn = param_token.text; self.expect(TK.COLON)
                    var = bool(self.match(TK.ELLIPSIS)); pt = self.parse_type(); params.append(Param(pn, pt, var, param_token.pos))
                    if not self.match(TK.COMMA): break
                    if self.at(TK.RPAREN): break
            self.expect(TK.RPAREN); rt = VOID_T
            if self.match(TK.ARROW): rt = self.parse_type()
            methods.append(FunctionDecl(mname, params, rt, None, mp, True, mut, None))
            self.skip_terms()
        self.expect(TK.RBRACE)
        return TraitDecl(name, methods, p)

    def parse_enum(self) -> EnumDecl:
        p = self.expect(TK.ENUM).pos; name = self.expect(TK.IDENT).text
        self.skip_newlines(); self.expect(TK.LBRACE); self.skip_terms()
        members: list[EnumMember] = []
        while not self.at(TK.RBRACE):
            n = self.expect(TK.IDENT).text; value = None
            if self.match(TK.ASSIGN):
                sign = -1 if self.match(TK.MINUS) else 1
                value = sign * self.expect(TK.INT, "enum value must be an integer literal").value
            members.append(EnumMember(n, value))
            self.match(TK.COMMA); self.skip_terms()
        self.expect(TK.RBRACE); return EnumDecl(name, members, p)

    def parse_block(self) -> Block:
        p = self.expect(TK.LBRACE).pos; self.skip_terms(); items: list[Any] = []
        while not self.at(TK.RBRACE):
            if self.at(TK.EOF): raise ParseError(p, "unterminated block")
            items.append(self.parse_statement()); self.skip_terms()
        self.expect(TK.RBRACE); return Block(items, p)

    def parse_statement(self) -> Any:
        self.skip_newlines(); p = self.cur().pos
        if self.at(TK.LBRACE): return self.parse_block()
        if self.match(TK.IF): return self.parse_if_after_if(p)
        if self.match(TK.WHILE):
            cond = self.parse_expr(allow_struct_literal=False); self.skip_newlines(); return WhileStmt(cond, self.parse_block(), p)
        if self.match(TK.FOR): return self.parse_for(p)
        if self.match(TK.SWITCH): return self.parse_switch(p)
        if self.match(TK.TRY): return self.parse_try(p)
        if self.match(TK.THROW): return ThrowStmt(self.parse_expr(), p)
        if self.match(TK.RETURN):
            if self.cur().kind in (TK.NEWLINE, TK.SEMI, TK.RBRACE): return ReturnStmt(None, p)
            return ReturnStmt(self.parse_expr(), p)
        if self.match(TK.BREAK): return BreakStmt(p)
        if self.match(TK.CONTINUE): return ContinueStmt(p)

        mutable = bool(self.match(TK.MUT))
        # declaration is IDENT ':' Type '=' expr
        if self.at(TK.IDENT) and self.peek().kind is TK.COLON:
            n = self.advance().text; self.expect(TK.COLON); typ = self.parse_type(); self.expect(TK.ASSIGN)
            return VarDecl(n, typ, self.parse_expr(), mutable, p)
        if mutable:
            raise ParseError(p, "mut is only valid on a variable declaration")
        return ExprStmt(self.parse_expr(), p)

    def parse_if_after_if(self, p: SourcePos) -> IfStmt:
        cond = self.parse_expr(allow_struct_literal=False); self.skip_newlines(); then = self.parse_block(); self.skip_newlines()
        other = None
        if self.match(TK.ELSE):
            self.skip_newlines()
            if self.match(TK.IF): other = self.parse_if_after_if(self.ts[self.i-1].pos)
            else: other = self.parse_block()
        return IfStmt(cond, then, other, p)

    def parse_for(self, p: SourcePos) -> ForStmt:
        if self.at(TK.LPAREN):
            raise DiagnosticError(
                "P075",
                p,
                "map iteration bindings do not use parentheses; use 'for key, value in map'",
                3,
                "parse",
            )
        names = [self.expect(TK.IDENT, "expected loop binding").text]
        if self.match(TK.COMMA): names.append(self.expect(TK.IDENT).text)
        self.expect(TK.IN); expr = self.parse_expr(allow_struct_literal=False); self.skip_newlines()
        return ForStmt(names, expr, self.parse_block(), p)

    def parse_switch(self, p: SourcePos) -> SwitchStmt:
        value = self.parse_expr(allow_struct_literal=False); self.skip_newlines(); self.expect(TK.LBRACE); self.skip_terms()
        cases: list[SwitchCase] = []
        while not self.at(TK.RBRACE):
            if self.match(TK.CASE):
                e = self.parse_expr(); self.skip_newlines(); cases.append(SwitchCase(e, self.parse_block()))
            elif self.match(TK.DEFAULT):
                self.skip_newlines(); cases.append(SwitchCase(None, self.parse_block()))
            else: raise ParseError(self.cur().pos, "expected case or default")
            self.skip_terms()
        self.expect(TK.RBRACE); return SwitchStmt(value, cases, p)

    def parse_try(self, p: SourcePos) -> TryStmt:
        self.skip_newlines(); tb = self.parse_block(); self.skip_newlines()
        cn = None; ct = None; cb = None; fb = None
        if self.match(TK.CATCH):
            self.skip_newlines(); self.expect(TK.LPAREN); cn = self.expect(TK.IDENT).text
            self.expect(TK.COLON); ct = self.parse_type(); self.expect(TK.RPAREN); self.skip_newlines(); cb = self.parse_block(); self.skip_newlines()
        if self.match(TK.FINALLY): self.skip_newlines(); fb = self.parse_block()
        if cb is None and fb is None: raise ParseError(p, "try requires catch and/or finally")
        return TryStmt(tb, cn, ct, cb, fb, p)

    # precedence values: larger binds tighter. Assignment is handled specially.
    PREC = {
        TK.COALESCE: 1, TK.OROR: 2, TK.ANDAND: 3, TK.EQEQ: 4, TK.NE: 4,
        TK.LT: 5, TK.LE: 5, TK.GT: 5, TK.GE: 5,
        TK.PIPE: 6, TK.CARET: 7, TK.AMP: 8, TK.SHL: 9, TK.SHR: 9,
        TK.CONCAT: 10, TK.PLUS: 11, TK.MINUS: 11, TK.STAR: 12, TK.SLASH: 12, TK.PERCENT: 12,
    }

    def parse_expr(self, min_prec: int = 0, allow_struct_literal: bool = True) -> Any:
        self.skip_newlines()
        left = self.parse_prefix()
        while True:
            self.skip_newlines()
            # postfix operators
            if self.match(TK.LPAREN):
                args: list[CallArg] = []; self.skip_newlines()
                if not self.at(TK.RPAREN):
                    while True:
                        e = self.parse_expr(); spread = bool(self.match(TK.ELLIPSIS)); args.append(CallArg(e, spread))
                        self.skip_newlines()
                        if not self.match(TK.COMMA): break
                        self.skip_newlines()
                        if self.at(TK.RPAREN): break
                self.expect(TK.RPAREN); left = Call(left, args, getattr(left, "pos", self.cur().pos)); continue
            if self.match(TK.LBRACKET):
                idx = self.parse_expr(); self.expect(TK.RBRACKET); left = Index(left, idx, getattr(left, "pos", self.cur().pos)); continue
            if self.match(TK.DOT):
                n = self.expect(TK.IDENT, "expected member name").text; left = Member(left, n, getattr(left, "pos", self.cur().pos)); continue
            # Struct literal is restricted to a simple type name on the left.
            if allow_struct_literal and self.at(TK.LBRACE) and isinstance(left, Name):
                left = self.parse_struct_literal(left); continue

            if self.match(TK.ASSIGN):
                if min_prec > 0: self.i -= 1; break
                right = self.parse_expr(0, allow_struct_literal); left = Assign(left, right, getattr(left, "pos", self.cur().pos)); continue
            op = self.cur().kind; prec = self.PREC.get(op)
            if prec is None or prec < min_prec: break
            tok = self.advance()
            # ?? is right associative; other binary operators are left associative.
            next_min = prec if op is TK.COALESCE else prec + 1
            right = self.parse_expr(next_min, allow_struct_literal)
            left = Binary(left, tok.text, right, tok.pos)
        return left

    def parse_prefix(self) -> Any:
        t = self.advance()
        if t.kind is TK.INT: return Literal(t.value, "int", t.pos)
        if t.kind is TK.FLOAT: return Literal(t.value, "float", t.pos)
        if t.kind is TK.STRING: return Literal(t.value, "string", t.pos)
        if t.kind is TK.CHAR: return Literal(CharValue(t.value), "char", t.pos)
        if t.kind is TK.TRUE: return Literal(True, "bool", t.pos)
        if t.kind is TK.FALSE: return Literal(False, "bool", t.pos)
        if t.kind is TK.NULL: return Literal(None, "null", t.pos)
        if t.kind is TK.IDENT: return Name(t.text, t.pos)
        if t.kind in (TK.BANG, TK.MINUS, TK.TILDE, TK.PLUS): return Unary(t.text, self.parse_expr(13), t.pos)
        if t.kind is TK.LPAREN:
            e = self.parse_expr(); self.expect(TK.RPAREN); return e
        if t.kind is TK.LBRACKET:
            items: list[Any] = []; self.skip_newlines()
            if not self.at(TK.RBRACKET):
                while True:
                    items.append(self.parse_expr()); self.skip_newlines()
                    if not self.match(TK.COMMA): break
                    self.skip_newlines()
                    if self.at(TK.RBRACKET): break
            self.expect(TK.RBRACKET); return ListExpr(items, t.pos)
        if t.kind is TK.LBRACE:
            items: list[tuple[Any, Any]] = []; self.skip_newlines()
            if not self.at(TK.RBRACE):
                while True:
                    k = self.parse_expr(); self.expect(TK.COLON); v = self.parse_expr(); items.append((k, v)); self.skip_newlines()
                    if not self.match(TK.COMMA): break
                    self.skip_newlines()
                    if self.at(TK.RBRACE): break
            self.expect(TK.RBRACE); return MapExpr(items, t.pos)
        raise ParseError(t.pos, f"expected expression, found {t.text!r}")

    def parse_struct_literal(self, type_expr: Name) -> StructExpr:
        self.expect(TK.LBRACE); self.skip_newlines(); fields: list[tuple[str, Any]] = []
        if not self.at(TK.RBRACE):
            while True:
                name = self.expect(TK.IDENT, "expected struct field name").text
                self.expect(TK.COLON); value = self.parse_expr(); fields.append((name, value)); self.skip_newlines()
                if not self.match(TK.COMMA): break
                self.skip_newlines()
                if self.at(TK.RBRACE): break
        self.expect(TK.RBRACE); return StructExpr(type_expr.name, fields, type_expr.pos)


# =============================================================================
# Runtime values
# =============================================================================

class CharValue(str):
    pass

@dataclass(frozen=True)
class ByteValue:
    value: int
    def __int__(self): return self.value
    def __str__(self): return str(self.value)

@dataclass(frozen=True)
class EnumValue:
    enum_name: str
    member_name: str
    value: int
    def __str__(self): return str(self.value)

@dataclass
class RegexValue:
    pattern: str
    compiled: re.Pattern[str]

@dataclass
class StackValue:
    items: list[Any] = field(default_factory=list)

@dataclass
class StructValue:
    type_name: str
    fields: dict[str, Any]

@dataclass
class Binding:
    value: Any
    declared_type: TypeRef
    mutable: bool

class Env:
    def __init__(self, parent: Optional["Env"] = None): self.parent = parent; self.bindings: dict[str, Binding] = {}
    def declare(self, name: str, value: Any, typ: TypeRef, mutable: bool) -> None:
        if name in self.bindings: raise runtime_error(f"duplicate variable {name}")
        self.bindings[name] = Binding(value, typ, mutable)
    def find(self, name: str) -> "Env":
        if name in self.bindings: return self
        if self.parent: return self.parent.find(name)
        raise runtime_error(f"undefined name {name}")
    def get_binding(self, name: str) -> Binding: return self.find(name).bindings[name]
    def get(self, name: str) -> Any: return self.get_binding(name).value


@dataclass
class Namespace:
    name: str
    values: dict[str, Any]

@dataclass
class NativeFunction:
    name: str
    fn: Callable[..., Any]
    def __call__(self, *args): return self.fn(*args)

@dataclass
class UserFunction:
    decl: FunctionDecl
    package: str

@dataclass
class BoundMethod:
    receiver: StructValue
    function: UserFunction
    receiver_mutable: bool

@dataclass
class StructTypeValue:
    decl: StructDecl

@dataclass
class EnumTypeValue:
    decl: EnumDecl
    members: dict[str, EnumValue]


# =============================================================================
# Semantic rules
# =============================================================================

def copy_value(value: Any) -> Any:
    """Solvik structs have value semantics. Collections also copy recursively here.

    Keeping this rule centralized makes assignment/call/return copy boundaries
    easy to audit. Native opaque values (regex, exceptions) are intentionally
    shared.
    """
    if isinstance(value, StructValue):
        return StructValue(value.type_name, {k: copy_value(v) for k, v in value.fields.items()})
    if isinstance(value, list): return [copy_value(v) for v in value]
    if isinstance(value, dict): return {copy_value(k): copy_value(v) for k, v in value.items()}
    if isinstance(value, StackValue): return StackValue([copy_value(v) for v in value.items])
    return value


def numeric_value(v: Any) -> Any:
    return v.value if isinstance(v, ByteValue) else v


def type_name_of(v: Any) -> str:
    if v is None: return "null"
    if isinstance(v, bool): return "bool"
    if isinstance(v, ByteValue): return "byte"
    if isinstance(v, EnumValue): return v.enum_name.lower()
    if isinstance(v, CharValue): return "char"
    if isinstance(v, int) and not isinstance(v, bool): return "int"
    if isinstance(v, float): return "float"
    if isinstance(v, str): return "string"
    if isinstance(v, list): return "list"
    if isinstance(v, dict): return "map"
    if isinstance(v, StackValue): return "stack"
    if isinstance(v, StructValue): return v.type_name.lower()
    if isinstance(v, SolvikExceptionValue): return "exception"
    if isinstance(v, RegexValue): return "regex"
    return "any"


def solvik_string(v: Any) -> str:
    if v is None: return "null"
    if isinstance(v, bool): return "true" if v else "false"
    if isinstance(v, ByteValue): return str(v.value)
    if isinstance(v, CharValue): return str(v)
    if isinstance(v, EnumValue): return str(v.value)
    if isinstance(v, float):
        return str(v) if not v.is_integer() else str(int(v))
    if isinstance(v, list): return "[" + " ".join(solvik_string(x) for x in v) + "]"
    if isinstance(v, StackValue): return "[" + " ".join(solvik_string(x) for x in v.items) + "]"
    if isinstance(v, dict): return "map[" + " ".join(f"{solvik_string(k)}:{solvik_string(val)}" for k,val in v.items()) + "]"
    if isinstance(v, StructValue): return v.type_name + "{" + ", ".join(f"{k}: {solvik_string(x)}" for k,x in v.fields.items()) + "}"
    if isinstance(v, SolvikExceptionValue): return v.message
    return str(v)


def truth(v: Any) -> bool:
    if not isinstance(v, bool): raise runtime_error(f"condition requires bool, got {type_name_of(v)}", "E066")
    return v


# =============================================================================
# Static validation
# =============================================================================

UNKNOWN_T = TypeRef("<unknown>")
NULL_T = TypeRef("null")
REGEX_T = TypeRef("regex")


@dataclass(frozen=True)
class StaticBinding:
    type: TypeRef
    mutable: bool


class SemanticValidator:
    """Small independent checker for source-level conformance diagnostics.

    The evaluator remains the authoritative implementation for runtime
    semantics. This pass rejects declaration and expression errors that must be
    diagnosed even when the containing function is never called.
    """

    def __init__(self, interpreter: "Interpreter", program: Program):
        self.interpreter = interpreter
        self.program = program
        self.package = program.package
        self.structs = {
            name: decl
            for (package, name), decl in interpreter.structs.items()
            if package == self.package
        }
        self.structs.update({d.name: d for d in program.declarations if isinstance(d, StructDecl)})
        self.traits = {
            name: decl
            for (package, name), decl in interpreter.traits.items()
            if package == self.package
        }
        self.traits.update({d.name: d for d in program.declarations if isinstance(d, TraitDecl)})
        self.functions: dict[str, FunctionDecl] = {}
        ns = interpreter.packages.get(self.package)
        if ns:
            self.functions.update({
                name: value.decl
                for name, value in ns.values.items()
                if isinstance(value, UserFunction)
            })
        self.current_function: Optional[FunctionDecl] = None
        self.current_struct: Optional[StructDecl] = None
        self.scopes: list[dict[str, StaticBinding]] = []

    @staticmethod
    def source_line(pos: SourcePos) -> str:
        try: return pathlib.Path(pos.file).read_text(encoding="utf-8").splitlines()[pos.line - 1]
        except (OSError, IndexError): return ""

    def to_line_end(self, pos: SourcePos) -> int:
        return max(1, len(self.source_line(pos).rstrip()) - pos.column + 1)

    @staticmethod
    def literal_span(expression: Any) -> int:
        if not isinstance(expression, Literal): return 1
        if expression.literal_kind == "string": return len(expression.value) + 2
        if expression.literal_kind == "char": return len(expression.value) + 2
        if expression.literal_kind == "null": return 4
        return len(str(expression.value))

    def error(self, code: str, pos: SourcePos, message: str, span_length: int = 1) -> None:
        raise DiagnosticError(code, pos, message, span_length)

    def validate(self) -> None:
        seen_functions = set(self.functions)
        for decl in self.program.declarations:
            if isinstance(decl, FunctionDecl):
                if decl.name in seen_functions:
                    self.error("C090", decl.pos, f"duplicate function '{decl.name}'", self.to_line_end(decl.pos))
                seen_functions.add(decl.name)
                self.functions[decl.name] = decl
            elif isinstance(decl, StructDecl):
                seen_fields: set[str] = set()
                for field_decl in decl.fields:
                    if field_decl.name in seen_fields:
                        self.error(
                            "C091",
                            field_decl.pos or decl.pos,
                            f"duplicate field '{field_decl.name}' in struct '{decl.name}'",
                        )
                    seen_fields.add(field_decl.name)

        for decl in self.program.declarations:
            if isinstance(decl, FunctionDecl):
                self.validate_function(decl, None)
            elif isinstance(decl, StructDecl):
                for method in decl.methods:
                    self.validate_function(method, decl)
            elif isinstance(decl, TraitDecl):
                for method in decl.methods:
                    self.validate_parameters(method)

    def validate_parameters(self, function: FunctionDecl) -> None:
        seen: set[str] = set()
        for param in function.params:
            if param.name in seen:
                self.error(
                    "C092",
                    param.pos or function.pos,
                    f"duplicate parameter '{param.name}' in function '{function.name}'",
                )
            seen.add(param.name)

    def validate_function(self, function: FunctionDecl, owner: Optional[StructDecl]) -> None:
        self.validate_parameters(function)
        if function.body is None:
            return
        old_function, old_struct, old_scopes = self.current_function, self.current_struct, self.scopes
        self.current_function, self.current_struct = function, owner
        self.scopes = [{p.name: StaticBinding(p.type, False) for p in function.params}]
        self.check_block(function.body, new_scope=False)
        self.current_function, self.current_struct, self.scopes = old_function, old_struct, old_scopes

    def lookup(self, name: str) -> Optional[StaticBinding]:
        for scope in reversed(self.scopes):
            if name in scope:
                return scope[name]
        return None

    def receiver_field(self, name: str) -> Optional[FieldDecl]:
        if not self.current_struct:
            return None
        return next((field_decl for field_decl in self.current_struct.fields if field_decl.name == name), None)

    def check_block(self, block: Block, new_scope: bool = True) -> None:
        if new_scope:
            self.scopes.append({})
        try:
            for statement in block.statements:
                self.check_statement(statement)
        finally:
            if new_scope:
                self.scopes.pop()

    def check_statement(self, statement: Any) -> None:
        if isinstance(statement, Block):
            self.check_block(statement)
        elif isinstance(statement, VarDecl):
            self.check_value_for_type(statement.value, statement.type)
            self.scopes[-1][statement.name] = StaticBinding(statement.type, statement.mutable)
        elif isinstance(statement, ExprStmt):
            self.infer(statement.expr)
        elif isinstance(statement, IfStmt):
            self.infer(statement.condition)
            self.check_block(statement.then_block)
            if isinstance(statement.else_branch, Block): self.check_block(statement.else_branch)
            elif isinstance(statement.else_branch, IfStmt): self.check_statement(statement.else_branch)
        elif isinstance(statement, WhileStmt):
            self.infer(statement.condition); self.check_block(statement.body)
        elif isinstance(statement, ForStmt):
            iterable_type = self.infer(statement.iterable)
            bindings: dict[str, StaticBinding] = {}
            if len(statement.names) == 2 and iterable_type.name == "map" and len(iterable_type.args) == 2:
                bindings[statement.names[0]] = StaticBinding(iterable_type.args[0], False)
                bindings[statement.names[1]] = StaticBinding(iterable_type.args[1], False)
            else:
                element_type = iterable_type.args[0] if iterable_type.args else UNKNOWN_T
                bindings[statement.names[0]] = StaticBinding(element_type, False)
            self.scopes.append(bindings)
            try: self.check_block(statement.body, new_scope=False)
            finally: self.scopes.pop()
        elif isinstance(statement, SwitchStmt):
            switch_type = self.infer(statement.value)
            for case in statement.cases:
                if case.expr is not None:
                    case_type = self.infer(case.expr)
                    if not self.switch_types_overlap(switch_type, case_type):
                        if case_type.name == "null":
                            message = f"case null can never match switch of type {switch_type}; the switch type is not nullable"
                        else:
                            message = f"cannot use case of type {case_type} with switch of type {switch_type}"
                        self.error(
                            "C094",
                            getattr(case.expr, "pos", statement.pos),
                            message,
                            self.literal_span(case.expr),
                        )
                self.check_block(case.body)
        elif isinstance(statement, TryStmt):
            self.check_block(statement.try_block)
            if statement.catch_block is not None:
                self.scopes.append({statement.catch_name or "e": StaticBinding(statement.catch_type or EXCEPTION_T, False)})
                try: self.check_block(statement.catch_block, new_scope=False)
                finally: self.scopes.pop()
            if statement.finally_block is not None: self.check_block(statement.finally_block)
        elif isinstance(statement, ThrowStmt): self.infer(statement.value)
        elif isinstance(statement, ReturnStmt) and statement.value is not None: self.infer(statement.value)

    def check_value_for_type(self, expression: Any, expected: TypeRef) -> None:
        if isinstance(expression, ListExpr) and expected.name == "list" and expected.args:
            for item in expression.items:
                actual = self.infer(item)
                if not self.assignable(actual, expected.args[0]):
                    self.error("C082", item.pos, f"list element: expected {expected.args[0]} but got {actual}", self.literal_span(item))
            return
        if isinstance(expression, MapExpr) and expected.name == "map" and len(expected.args) == 2:
            for key, value in expression.items:
                self.infer(key)
                actual = self.infer(value)
                if not self.assignable(actual, expected.args[1]):
                    self.error("C037", value.pos, f"cannot assign {actual} to map value of type {expected.args[1]}", self.literal_span(value))
            return
        self.infer(expression)

    def assignable(self, actual: TypeRef, expected: TypeRef) -> bool:
        if actual == UNKNOWN_T or expected.name == "any": return True
        if actual.name == "null": return expected.nullable
        if expected.nullable: expected = expected.nonnull()
        if actual.name == expected.name:
            return not actual.args or not expected.args or all(
                self.assignable(a, e) for a, e in zip(actual.args, expected.args)
            )
        trait = self.traits.get(expected.name)
        struct = self.structs.get(actual.name)
        if trait and struct:
            methods = {method.name: method for method in struct.methods if method.public}
            return all(
                (have := methods.get(need.name)) is not None
                and have.mutating == need.mutating
                and have.return_type == need.return_type
                and [(p.type, p.variadic) for p in have.params] == [(p.type, p.variadic) for p in need.params]
                for need in trait.methods
            )
        return (actual.name, expected.name) in {("byte", "int"), ("byte", "float"), ("int", "float")}

    @staticmethod
    def switch_types_overlap(switch_type: TypeRef, case_type: TypeRef) -> bool:
        if switch_type == UNKNOWN_T or case_type == UNKNOWN_T or switch_type.name == "any" or case_type.name == "any": return True
        if case_type.name == "regex": return switch_type.name == "string"
        if case_type.name == "null": return switch_type.nullable
        if switch_type.name == case_type.name: return True
        return {switch_type.name, case_type.name} <= {"byte", "int", "float"}

    def infer(self, expression: Any) -> TypeRef:
        if isinstance(expression, Literal):
            return NULL_T if expression.literal_kind == "null" else TypeRef(expression.literal_kind)
        if isinstance(expression, Name):
            binding = self.lookup(expression.name)
            if binding: return binding.type
            field_decl = self.receiver_field(expression.name)
            if field_decl: return field_decl.type
            if expression.name == "self" and self.current_struct: return TypeRef(self.current_struct.name)
            return UNKNOWN_T
        if isinstance(expression, ListExpr):
            types = [self.infer(item) for item in expression.items]
            return TypeRef("list", (types[0] if types else UNKNOWN_T,))
        if isinstance(expression, MapExpr):
            key_types = [self.infer(item[0]) for item in expression.items]
            value_types = [self.infer(item[1]) for item in expression.items]
            return TypeRef("map", (key_types[0] if key_types else UNKNOWN_T, value_types[0] if value_types else UNKNOWN_T))
        if isinstance(expression, StructExpr):
            for _, value in expression.fields: self.infer(value)
            return TypeRef(expression.type_name)
        if isinstance(expression, Unary):
            operand = self.infer(expression.expr)
            return TypeRef("bool") if expression.op == "!" else operand
        if isinstance(expression, Binary):
            left = self.infer(expression.left)
            right = self.infer(expression.right)
            if expression.op == "??":
                if left.name in ("void", "function", "module", "regex"):
                    left_pos = getattr(expression.left, "pos", expression.pos)
                    line_tail = self.source_line(left_pos)[left_pos.column - 1:]
                    span_length = max(1, len(line_tail.split("??", 1)[0].rstrip()))
                    self.error("C028", left_pos, f"left operand of ?? must be a value, got {left.name}", span_length)
                return right if left.name == "null" else left.nonnull()
            if expression.op in ("<", "<=", ">", ">="):
                if (left.name == "char") != (right.name == "char") and left != UNKNOWN_T and right != UNKNOWN_T:
                    pos = getattr(expression.left, "pos", expression.pos)
                    self.error("C017", pos, f"cannot apply {expression.op} to {left} and {right}", self.to_line_end(pos))
                return TypeRef("bool")
            if expression.op in ("==", "!=", "&&", "||"): return TypeRef("bool")
            if expression.op == "..": return TypeRef("string")
            if left.name == "float" or right.name == "float": return TypeRef("float")
            return left if left != UNKNOWN_T else right
        if isinstance(expression, Assign):
            self.check_assignment_receiver(expression.target, expression.pos)
            value_type = self.infer(expression.value)
            target_type = self.infer(expression.target)
            return target_type if target_type != UNKNOWN_T else value_type
        if isinstance(expression, Index):
            obj_type = self.infer(expression.obj); self.infer(expression.index)
            if obj_type.name == "map" and len(obj_type.args) == 2: return obj_type.args[1]
            if obj_type.name in ("list", "stack") and obj_type.args: return obj_type.args[0]
            if obj_type.name == "string": return TypeRef("char")
            return UNKNOWN_T
        if isinstance(expression, Member):
            obj_type = self.infer(expression.obj)
            struct = self.structs.get(obj_type.name)
            if struct:
                field_decl = next((field_decl for field_decl in struct.fields if field_decl.name == expression.name), None)
                if field_decl: return field_decl.type
                method = next((method for method in struct.methods if method.name == expression.name), None)
                if method: return TypeRef("function")
            if expression.name in ("len", "byteLength", "indexOf"): return TypeRef("function")
            return UNKNOWN_T
        if isinstance(expression, Call):
            for argument in expression.args: self.infer(argument.expr)
            if isinstance(expression.callee, Name):
                name = expression.callee.name
                function = self.functions.get(name)
                if function: return function.return_type
                if self.current_struct:
                    method = next((m for m in self.current_struct.methods if m.name == name), None)
                    if method: return method.return_type
                return {
                    "print": VOID_T, "println": VOID_T, "string": TypeRef("string"),
                    "int": TypeRef("int"), "float": TypeRef("float"), "byte": TypeRef("byte"),
                    "bool": TypeRef("bool"), "regex": REGEX_T, "typeOf": TypeRef("string"),
                    "isType": TypeRef("bool"), "stack": TypeRef("stack", (UNKNOWN_T,)),
                }.get(name, UNKNOWN_T)
            if isinstance(expression.callee, Member):
                obj_type = self.infer(expression.callee.obj)
                struct = self.structs.get(obj_type.name)
                if struct:
                    method = next((m for m in struct.methods if m.name == expression.callee.name), None)
                    if method:
                        if method.mutating and not self.receiver_is_mutable(expression.callee.obj):
                            receiver_name = expression.callee.obj.name if isinstance(expression.callee.obj, Name) else "receiver"
                            self.error(
                                "C068",
                                expression.pos,
                                f"cannot call mutating method '{method.name}' on immutable struct variable '{receiver_name}'; declare the variable as 'mut'",
                                self.to_line_end(expression.pos),
                            )
                        return method.return_type
                if expression.callee.name in ("len", "byteLength", "indexOf"): return TypeRef("int")
                if expression.callee.name in ("contains", "startsWith", "endsWith", "isEmpty"): return TypeRef("bool")
                return UNKNOWN_T
            self.infer(expression.callee)
            return UNKNOWN_T
        return UNKNOWN_T

    def receiver_is_mutable(self, expression: Any) -> bool:
        if isinstance(expression, Name):
            if expression.name == "self": return bool(self.current_function and self.current_function.mutating)
            binding = self.lookup(expression.name)
            return bool(binding and binding.mutable)
        return False

    def check_assignment_receiver(self, target: Any, pos: SourcePos) -> None:
        if not self.current_struct or not self.current_function or self.current_function.mutating:
            return
        if isinstance(target, Name) and self.lookup(target.name) is None and self.receiver_field(target.name):
            self.error(
                "C068",
                pos,
                f"method '{self.current_struct.name}' is not mutating and cannot assign receiver field '{target.name}'; declare it as 'mut func'",
                self.to_line_end(pos),
            )
        if isinstance(target, Member) and isinstance(target.obj, Name) and target.obj.name == "self":
            self.error(
                "C068",
                pos,
                f"method '{self.current_struct.name}' is not mutating and cannot assign receiver field '{target.name}'; declare it as 'mut func'",
                self.to_line_end(pos),
            )


# =============================================================================
# Interpreter
# =============================================================================

class Interpreter:
    def __init__(self):
        self.packages: dict[str, Namespace] = {}
        self.structs: dict[tuple[str,str], StructDecl] = {}
        self.traits: dict[tuple[str,str], TraitDecl] = {}
        self.enums: dict[tuple[str,str], EnumTypeValue] = {}
        self.entry_package: Optional[str] = None
        self.cwd = pathlib.Path.cwd()
        self.builtins = build_builtins()

    # ---- program registration -------------------------------------------------
    def add_program(self, program: Program) -> None:
        SemanticValidator(self, program).validate()
        ns = self.packages.setdefault(program.package, Namespace(program.package, {}))
        # First pass: types, so function bodies can refer forward.
        for d in program.declarations:
            if isinstance(d, StructDecl):
                self.structs[(program.package, d.name)] = d
                ns.values[d.name] = StructTypeValue(d)
            elif isinstance(d, TraitDecl): self.traits[(program.package, d.name)] = d
            elif isinstance(d, EnumDecl):
                next_value = 0; members: dict[str, EnumValue] = {}
                for m in d.members:
                    if m.value is not None: next_value = m.value
                    members[m.name] = EnumValue(d.name, m.name, next_value); next_value += 1
                et = EnumTypeValue(d, members); self.enums[(program.package, d.name)] = et; ns.values[d.name] = et
        for d in program.declarations:
            if isinstance(d, FunctionDecl): ns.values[d.name] = UserFunction(d, program.package)
            elif isinstance(d, StructDecl):
                for m in d.methods: m.owner_struct = d.name

    def run(self, entry_package: str) -> int:
        self.entry_package = entry_package
        ns = self.packages.get(entry_package)
        if not ns or "main" not in ns.values: raise SolvikError(f"package {entry_package!r} has no main function")
        result = self.call_value(ns.values["main"], [], receiver_mutable=False)
        return 0 if result is None else int(numeric_value(result))

    # ---- type enforcement -----------------------------------------------------
    def resolve_type_decl(self, typ: TypeRef, package: str) -> Any:
        return self.structs.get((package, typ.name)) or self.traits.get((package, typ.name)) or self.enums.get((package, typ.name))

    def value_matches_type(self, value: Any, typ: TypeRef, package: str) -> bool:
        if typ.name == "any": return True
        if value is None: return typ.nullable
        base = typ.nonnull()
        if base.name == "bool": return isinstance(value, bool)
        if base.name == "byte": return isinstance(value, ByteValue)
        if base.name == "int": return isinstance(value, (int, ByteValue)) and not isinstance(value, bool)
        if base.name == "float": return isinstance(value, (int, float, ByteValue)) and not isinstance(value, bool)
        if base.name == "char": return isinstance(value, CharValue)
        if base.name == "string": return isinstance(value, str) and not isinstance(value, CharValue)
        if base.name == "exception": return isinstance(value, SolvikExceptionValue)
        if base.name == "list":
            return isinstance(value, list) and (not base.args or all(self.value_matches_type(v, base.args[0], package) for v in value))
        if base.name == "stack":
            return isinstance(value, StackValue) and (not base.args or all(self.value_matches_type(v, base.args[0], package) for v in value.items))
        if base.name == "map":
            return isinstance(value, dict) and (len(base.args) != 2 or all(self.value_matches_type(k, base.args[0], package) and self.value_matches_type(v, base.args[1], package) for k,v in value.items()))
        if (package, base.name) in self.structs:
            return isinstance(value, StructValue) and value.type_name == base.name
        if (package, base.name) in self.enums:
            return isinstance(value, EnumValue) and value.enum_name == base.name
        trait = self.traits.get((package, base.name))
        if trait:
            return isinstance(value, StructValue) and self.struct_satisfies_trait(value.type_name, trait, package)
        return False

    def coerce_for_type(self, value: Any, typ: TypeRef, package: str) -> Any:
        if value is None:
            if typ.nullable: return None
            raise runtime_error(f"type mismatch: null is not assignable to {typ}", "E066")
        # Numeric widening: byte -> int -> float.
        if typ.name == "int" and isinstance(value, ByteValue): return value.value
        if typ.name == "float" and isinstance(value, ByteValue): return float(value.value)
        if typ.name == "float" and isinstance(value, int) and not isinstance(value, bool): return float(value)
        if typ.name == "exception" and isinstance(value, str): return SolvikExceptionValue(value)
        if typ.name == "any": return copy_value(value)
        if not self.value_matches_type(value, typ, package):
            raise runtime_error(f"type mismatch: {type_name_of(value)} is not assignable to {typ}", "E066")
        return copy_value(value)

    def struct_satisfies_trait(self, struct_name: str, trait: TraitDecl, package: str) -> bool:
        struct = self.structs.get((package, struct_name))
        if not struct: return False
        by_name = {m.name:m for m in struct.methods if m.public}
        for need in trait.methods:
            have = by_name.get(need.name)
            if not have or have.mutating != need.mutating or have.return_type != need.return_type: return False
            if [(p.type,p.variadic) for p in have.params] != [(p.type,p.variadic) for p in need.params]: return False
        return True

    # ---- statement execution --------------------------------------------------
    def exec_block(self, block: Block, env: Env, package: str, new_scope: bool = True, receiver: Optional[StructValue] = None, receiver_mutable: bool = False) -> None:
        local = Env(env) if new_scope else env
        for s in block.statements:
            self.exec_stmt(s, local, package, receiver, receiver_mutable)

    def exec_stmt(self, s: Any, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> None:
        if isinstance(s, Block): self.exec_block(s, env, package, True, receiver, receiver_mutable); return
        if isinstance(s, VarDecl):
            value = self.coerce_for_type(self.eval_expr(s.value, env, package, receiver, receiver_mutable), s.type, package)
            env.declare(s.name, value, s.type, s.mutable); return
        if isinstance(s, ExprStmt): self.eval_expr(s.expr, env, package, receiver, receiver_mutable); return
        if isinstance(s, IfStmt):
            if truth(self.eval_expr(s.condition, env, package, receiver, receiver_mutable)):
                self.exec_block(s.then_block, env, package, True, receiver, receiver_mutable)
            elif isinstance(s.else_branch, Block): self.exec_block(s.else_branch, env, package, True, receiver, receiver_mutable)
            elif isinstance(s.else_branch, IfStmt): self.exec_stmt(s.else_branch, env, package, receiver, receiver_mutable)
            return
        if isinstance(s, WhileStmt):
            while truth(self.eval_expr(s.condition, env, package, receiver, receiver_mutable)):
                try: self.exec_block(s.body, env, package, True, receiver, receiver_mutable)
                except ContinueSignal: continue
                except BreakSignal: break
            return
        if isinstance(s, ForStmt):
            source = self.eval_expr(s.iterable, env, package, receiver, receiver_mutable)
            if source is None: raise runtime_error("null reference", "E031")
            if isinstance(source, dict): seq = list(source.items())
            elif isinstance(source, StackValue): seq = list(source.items)
            elif isinstance(source, (list, str)): seq = list(source)
            else: raise runtime_error(f"value of type {type_name_of(source)} is not iterable")
            for item in seq:
                loop_env = Env(env)
                if len(s.names) == 2:
                    if not isinstance(item, tuple): raise runtime_error("two-binding for loop requires a map")
                    loop_env.declare(s.names[0], copy_value(item[0]), ANY_T, False); loop_env.declare(s.names[1], copy_value(item[1]), ANY_T, False)
                else:
                    v = item[0] if isinstance(source, dict) else item
                    loop_env.declare(s.names[0], copy_value(v), ANY_T, False)
                try: self.exec_block(s.body, loop_env, package, False, receiver, receiver_mutable)
                except ContinueSignal: continue
                except BreakSignal: break
            return
        if isinstance(s, SwitchStmt):
            value = self.eval_expr(s.value, env, package, receiver, receiver_mutable)
            for c in s.cases:
                matched = c.expr is None
                if c.expr is not None:
                    cv = self.eval_expr(c.expr, env, package, receiver, receiver_mutable)
                    matched = bool(cv.compiled.search(value)) if isinstance(cv, RegexValue) and isinstance(value, str) else self.equal(value, cv)
                if matched:
                    self.exec_block(c.body, env, package, True, receiver, receiver_mutable); break
            return
        if isinstance(s, TryStmt):
            pending: Optional[BaseException] = None
            try:
                self.exec_block(s.try_block, env, package, True, receiver, receiver_mutable)
            except RuntimeSignal as ex:
                if s.catch_block is not None:
                    ce = Env(env); ce.declare(s.catch_name or "e", ex.value, s.catch_type or EXCEPTION_T, False)
                    try: self.exec_block(s.catch_block, ce, package, False, receiver, receiver_mutable)
                    except BaseException as inner: pending = inner
                else: pending = ex
            except (ReturnSignal, BreakSignal, ContinueSignal) as ex:
                pending = ex
            finally:
                if s.finally_block is not None:
                    try: self.exec_block(s.finally_block, env, package, True, receiver, receiver_mutable)
                    except BaseException as fin: pending = fin
            if pending is not None: raise pending
            return
        if isinstance(s, ThrowStmt):
            v = self.eval_expr(s.value, env, package, receiver, receiver_mutable)
            if isinstance(v, str): v = SolvikExceptionValue(v)
            if not isinstance(v, SolvikExceptionValue): raise runtime_error("throw requires string or exception")
            raise RuntimeSignal(v)
        if isinstance(s, ReturnStmt):
            v = None if s.value is None else self.eval_expr(s.value, env, package, receiver, receiver_mutable)
            raise ReturnSignal(copy_value(v))
        if isinstance(s, BreakStmt): raise BreakSignal()
        if isinstance(s, ContinueStmt): raise ContinueSignal()
        raise SolvikError(f"unhandled statement node {type(s).__name__}")

    # ---- expression evaluation ------------------------------------------------
    def eval_expr(self, e: Any, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> Any:
        if isinstance(e, Literal): return e.value
        if isinstance(e, Name): return self.resolve_name(e.name, env, package, receiver, receiver_mutable)
        if isinstance(e, ListExpr): return [copy_value(self.eval_expr(x, env, package, receiver, receiver_mutable)) for x in e.items]
        if isinstance(e, MapExpr): return {self.eval_expr(k, env, package, receiver, receiver_mutable): copy_value(self.eval_expr(v, env, package, receiver, receiver_mutable)) for k,v in e.items}
        if isinstance(e, StructExpr):
            decl = self.structs.get((package, e.type_name))
            if not decl: raise runtime_error(f"unknown struct {e.type_name}")
            supplied = dict(e.fields); values: dict[str, Any] = {}
            if set(supplied) != {f.name for f in decl.fields}: raise runtime_error(f"struct literal for {e.type_name} must initialize every field exactly once")
            for f in decl.fields:
                values[f.name] = self.coerce_for_type(self.eval_expr(supplied[f.name], env, package, receiver, receiver_mutable), f.type, package)
            return StructValue(e.type_name, values)
        if isinstance(e, Unary):
            v = self.eval_expr(e.expr, env, package, receiver, receiver_mutable)
            v = numeric_value(v)
            if e.op == "!": return not truth(v)
            if e.op == "-": return -v
            if e.op == "+": return +v
            if e.op == "~": return ~int(v)
        if isinstance(e, Binary): return self.eval_binary(e, env, package, receiver, receiver_mutable)
        if isinstance(e, Assign):
            value = self.eval_expr(e.value, env, package, receiver, receiver_mutable)
            return self.assign_target(e.target, value, env, package, receiver, receiver_mutable)
        if isinstance(e, Index):
            obj = self.eval_expr(e.obj, env, package, receiver, receiver_mutable)
            if obj is None: raise runtime_error("null reference", "E031")
            idx = self.eval_expr(e.index, env, package, receiver, receiver_mutable)
            try:
                if isinstance(obj, str): return CharValue(obj[int(numeric_value(idx))])
                return copy_value(obj[numeric_value(idx)])
            except (IndexError, KeyError): raise runtime_error("index out of range" if not isinstance(obj, dict) else "map key not found", "E031")
        if isinstance(e, Member): return self.eval_member(e, env, package, receiver, receiver_mutable)
        if isinstance(e, Call):
            # Calls on an lvalue are special: reading a struct/stack normally
            # produces a value copy, but a mutating method must operate on the
            # actual mutable receiver.  This is a semantic distinction, not an
            # optimization detail.
            callee, mutable = self.resolve_call_callee(e.callee, env, package, receiver, receiver_mutable)
            args: list[Any] = []
            for a in e.args:
                v = self.eval_expr(a.expr, env, package, receiver, receiver_mutable)
                if a.spread:
                    if not isinstance(v, list): raise runtime_error("spread requires a list")
                    args.extend(copy_value(v))
                else: args.append(v)
            return self.call_value(callee, args, mutable)
        raise SolvikError(f"unhandled expression node {type(e).__name__}")


    def resolve_call_callee(self, callee_expr: Any, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> tuple[Any, bool]:
        """Resolve a callable while preserving lvalue receiver identity.

        Ordinary expression reads use copy_value() to model Solvik value
        semantics. Method calls are different: `mut x; x.mutate()` must mutate
        x itself. This helper is the single explicit bridge between those rules.
        """
        if not isinstance(callee_expr, Member):
            return self.eval_expr(callee_expr, env, package, receiver, receiver_mutable), False

        base = callee_expr.obj
        mutable = self.target_is_mutable(base, env, receiver, receiver_mutable)
        actual: Any = None
        have_actual = False
        if isinstance(base, Name):
            try:
                actual = env.get_binding(base.name).value
                have_actual = True
            except RuntimeSignal:
                if receiver is not None and base.name in receiver.fields:
                    actual = receiver.fields[base.name]
                    have_actual = True
        elif isinstance(base, Name) and base.name == "self" and receiver is not None:
            actual = receiver; have_actual = True

        if not have_actual:
            return self.eval_expr(callee_expr, env, package, receiver, receiver_mutable), mutable

        if isinstance(actual, StructValue):
            decl = self.structs.get((package, actual.type_name))
            method = next((m for m in decl.methods if m.name == callee_expr.name), None) if decl else None
            if method:
                return BoundMethod(actual, UserFunction(method, package), mutable), mutable
        native = builtin_method(actual, callee_expr.name)
        if native is not None:
            # Stack operations mutate the stack object even when its binding is
            # immutable. `mut` controls rebinding and struct mut-receivers; the
            # canonical example intentionally declares `s: stack<int>` and then
            # calls s.push()/s.pop().
            return native, mutable
        return self.eval_expr(callee_expr, env, package, receiver, receiver_mutable), mutable

    def resolve_name(self, name: str, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> Any:
        try: return copy_value(env.get(name))
        except RuntimeSignal: pass
        if receiver is not None and name in receiver.fields: return copy_value(receiver.fields[name])
        if name == "self" and receiver is not None: return receiver
        if receiver is not None:
            decl = self.structs.get((package, receiver.type_name))
            method = next((method for method in decl.methods if method.name == name), None) if decl else None
            if method: return BoundMethod(receiver, UserFunction(method, package), receiver_mutable)
        ns = self.packages.get(package)
        if ns and name in ns.values: return ns.values[name]
        if name in self.packages: return self.packages[name]
        if name in self.builtins: return self.builtins[name]
        raise runtime_error(f"undefined name {name}")

    def eval_member(self, e: Member, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> Any:
        obj = self.eval_expr(e.obj, env, package, receiver, receiver_mutable)
        if obj is None: raise runtime_error("null reference", "E031")
        if isinstance(obj, Namespace):
            if e.name not in obj.values: raise runtime_error(f"namespace {obj.name} has no member {e.name}")
            return obj.values[e.name]
        if isinstance(obj, EnumTypeValue):
            if e.name not in obj.members: raise runtime_error(f"enum {obj.decl.name} has no member {e.name}")
            return obj.members[e.name]
        if isinstance(obj, SolvikExceptionValue):
            if e.name == "message": return obj.message
            if e.name == "trace": return obj.trace
            raise runtime_error(f"exception has no member {e.name}")
        if isinstance(obj, StructValue):
            if e.name in obj.fields: return copy_value(obj.fields[e.name])
            decl = self.structs.get((package, obj.type_name))
            if decl:
                m = next((m for m in decl.methods if m.name == e.name), None)
                if m: return BoundMethod(obj, UserFunction(m, package), self.target_is_mutable(e.obj, env, receiver, receiver_mutable))
            raise runtime_error(f"struct {obj.type_name} has no member {e.name}")
        method = builtin_method(obj, e.name)
        if method is not None: return method
        raise runtime_error(f"type {type_name_of(obj)} has no member {e.name}")

    def target_is_mutable(self, target: Any, env: Env, receiver: Optional[StructValue], receiver_mutable: bool) -> bool:
        if isinstance(target, Name):
            if target.name == "self" and receiver is not None: return receiver_mutable
            try: return env.get_binding(target.name).mutable
            except RuntimeSignal: return receiver is not None and target.name in receiver.fields and receiver_mutable
        if isinstance(target, Member): return self.target_is_mutable(target.obj, env, receiver, receiver_mutable)
        return False

    def assign_target(self, target: Any, value: Any, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> Any:
        if isinstance(target, Name):
            try:
                b = env.get_binding(target.name)
                if not b.mutable: raise runtime_error(f"cannot assign to immutable variable {target.name}")
                b.value = self.coerce_for_type(value, b.declared_type, package); return copy_value(b.value)
            except RuntimeSignal as ex:
                if not ex.value.message.startswith("undefined name"): raise
            if receiver is not None and target.name in receiver.fields:
                if not receiver_mutable: raise runtime_error("cannot mutate receiver from non-mutating method")
                decl = self.structs[(package, receiver.type_name)]; fd = next(f for f in decl.fields if f.name == target.name)
                if not fd.mutable: raise runtime_error(f"field {target.name} is immutable")
                receiver.fields[target.name] = self.coerce_for_type(value, fd.type, package); return copy_value(receiver.fields[target.name])
            raise runtime_error(f"undefined assignment target {target.name}")
        if isinstance(target, Member):
            obj = self.eval_expr(target.obj, env, package, receiver, receiver_mutable)
            if not isinstance(obj, StructValue): raise runtime_error("member assignment requires struct")
            decl = self.structs[(package, obj.type_name)]; fd = next((f for f in decl.fields if f.name == target.name), None)
            if not fd or not fd.mutable: raise runtime_error(f"field {target.name} is immutable")
            obj.fields[target.name] = self.coerce_for_type(value, fd.type, package)
            # write modified value back when base is a simple name (value semantics)
            if isinstance(target.obj, Name) and target.obj.name != "self":
                b = env.get_binding(target.obj.name); b.value = obj
            return copy_value(obj.fields[target.name])
        if isinstance(target, Index):
            obj = self.eval_expr(target.obj, env, package, receiver, receiver_mutable); idx = self.eval_expr(target.index, env, package, receiver, receiver_mutable)
            obj[numeric_value(idx)] = copy_value(value)
            if isinstance(target.obj, Name): env.get_binding(target.obj.name).value = obj
            return copy_value(value)
        raise runtime_error("invalid assignment target")

    def eval_binary(self, e: Binary, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> Any:
        left = self.eval_expr(e.left, env, package, receiver, receiver_mutable)
        if e.op == "??":
            return left if left is not None else self.eval_expr(e.right, env, package, receiver, receiver_mutable)
        if e.op == "&&": return False if not truth(left) else truth(self.eval_expr(e.right, env, package, receiver, receiver_mutable))
        if e.op == "||": return True if truth(left) else truth(self.eval_expr(e.right, env, package, receiver, receiver_mutable))
        right = self.eval_expr(e.right, env, package, receiver, receiver_mutable)
        if e.op in ("==", "!="):
            q = self.equal(left, right); return q if e.op == "==" else not q
        if left is None or right is None: raise runtime_error("null reference", "E031")
        if e.op == "..": return solvik_string(left) + solvik_string(right)
        a, b = numeric_value(left), numeric_value(right)
        if e.op == "+": return a + b
        if e.op == "-": return a - b
        if e.op == "*": return a * b
        if e.op == "/":
            if b == 0: raise runtime_error("division by zero", "E031")
            if isinstance(a, int) and isinstance(b, int): return int(a / b)  # truncates toward zero
            return a / b
        if e.op == "%":
            if b == 0: raise runtime_error("division by zero", "E031")
            return py_math.fmod(a, b) if isinstance(a, float) or isinstance(b, float) else a % b
        if e.op == "<<": return int(a) << int(b)
        if e.op == ">>": return int(a) >> int(b)
        if e.op == "&": return int(a) & int(b)
        if e.op == "|": return int(a) | int(b)
        if e.op == "^": return int(a) ^ int(b)
        if e.op in ("<", "<=", ">", ">="):
            if isinstance(left, CharValue) and isinstance(right, CharValue): a,b = ord(left), ord(right)
            return {"<":a < b, "<=":a <= b, ">":a > b, ">=":a >= b}[e.op]
        raise runtime_error(f"unsupported operator {e.op}")

    def equal(self, a: Any, b: Any) -> bool:
        if a is None or b is None: return a is b
        if isinstance(a, StructValue) and isinstance(b, StructValue): return a.type_name == b.type_name and a.fields == b.fields
        if isinstance(a, EnumValue) or isinstance(b, EnumValue): return isinstance(a, EnumValue) and isinstance(b, EnumValue) and a.enum_name == b.enum_name and a.value == b.value
        return numeric_value(a) == numeric_value(b)

    # ---- function calls --------------------------------------------------------
    def call_value(self, callee: Any, args: list[Any], receiver_mutable: bool) -> Any:
        if isinstance(callee, CallableNamespace):
            try: return callee.call(*[copy_value(a) for a in args])
            except RuntimeSignal: raise
            except Exception as ex: raise runtime_error(str(ex))
        if isinstance(callee, NativeFunction):
            try: return callee(*[copy_value(a) for a in args])
            except RuntimeSignal: raise
            except Exception as ex: raise runtime_error(str(ex))
        if isinstance(callee, BoundMethod):
            if callee.function.decl.mutating and not callee.receiver_mutable: raise runtime_error(f"mutating method {callee.function.decl.name} requires mutable receiver")
            return self.call_user(callee.function, args, callee.receiver, callee.receiver_mutable)
        if isinstance(callee, UserFunction): return self.call_user(callee, args, None, False)
        if isinstance(callee, StructTypeValue): raise runtime_error("structs use named-field literals, not call syntax")
        raise runtime_error(f"value of type {type_name_of(callee)} is not callable")

    def call_user(self, fn: UserFunction, args: list[Any], receiver: Optional[StructValue], receiver_mutable: bool) -> Any:
        d = fn.decl; env = Env()
        fixed = len(d.params) - (1 if d.params and d.params[-1].variadic else 0)
        if len(args) < fixed or (not d.params or not d.params[-1].variadic) and len(args) != len(d.params):
            raise runtime_error(f"{d.name} argument count mismatch")
        for i, p in enumerate(d.params):
            if p.variadic:
                val = [self.coerce_for_type(x, p.type, fn.package) for x in args[i:]]
                env.declare(p.name, val, TypeRef("list", (p.type,)), False)
            else:
                env.declare(p.name, self.coerce_for_type(args[i], p.type, fn.package), p.type, False)
        try:
            assert d.body is not None
            self.exec_block(d.body, env, fn.package, False, receiver, receiver_mutable and d.mutating)
        except ReturnSignal as r:
            return None if d.return_type.name == "void" else self.coerce_for_type(r.value, d.return_type, fn.package)
        if d.return_type.name != "void": raise runtime_error(f"function {d.name} reached end without returning {d.return_type}")
        return None


# =============================================================================
# Built-ins and standard library
# =============================================================================

def nf(name: str, fn: Callable[..., Any]) -> NativeFunction: return NativeFunction(name, fn)


def builtin_method(obj: Any, name: str) -> Optional[NativeFunction]:
    """Methods are deliberately centralized: this is the semantic table."""
    if isinstance(obj, str):
        methods = {
            "len": lambda: len(obj), "byteLength": lambda: len(obj.encode("utf-8")),
            "charAt": lambda i: _char_at(obj, i), "substring": lambda a,b: _substring(obj,a,b),
            "contains": lambda s: s in obj, "startsWith": lambda s: obj.startswith(s),
            "endsWith": lambda s: obj.endswith(s), "indexOf": lambda s: obj.find(s),
            "toUpper": lambda: obj.upper(), "toLower": lambda: obj.lower(), "trim": lambda: obj.strip(),
            "split": lambda s: obj.split(s),
        }
        if name in methods: return nf(f"string.{name}", methods[name])
    if isinstance(obj, list):
        if name == "len": return nf("list.len", lambda: len(obj))
    if isinstance(obj, dict):
        if name == "len": return nf("map.len", lambda: len(obj))
        if name == "contains": return nf("map.contains", lambda k: k in obj)
    if isinstance(obj, StackValue):
        if name == "push": return nf("stack.push", lambda v: obj.items.append(copy_value(v)))
        if name == "pop": return nf("stack.pop", lambda: _stack_pop(obj))
        if name == "peek": return nf("stack.peek", lambda: _stack_peek(obj))
        if name == "len": return nf("stack.len", lambda: len(obj.items))
        if name == "isEmpty": return nf("stack.isEmpty", lambda: not obj.items)
    if isinstance(obj, SolvikExceptionValue):
        if name == "message": return None
    return None


def _char_at(s: str, i: Any) -> CharValue:
    try: return CharValue(s[int(numeric_value(i))])
    except IndexError: raise runtime_error("index out of range", "E031")

def _substring(s: str, a: Any, b: Any) -> str:
    start = max(0, min(len(s), int(numeric_value(a)))); end = max(start, min(len(s), int(numeric_value(b))))
    return s[start:end]

def _stack_pop(s: StackValue) -> Any:
    if not s.items: raise runtime_error("pop from empty stack")
    return s.items.pop()

def _stack_peek(s: StackValue) -> Any:
    if not s.items: raise runtime_error("peek from empty stack")
    return copy_value(s.items[-1])


def build_builtins() -> dict[str, Any]:
    def to_int(v: Any) -> int:
        if isinstance(v, EnumValue): return v.value
        if isinstance(v, CharValue): return ord(v)
        if isinstance(v, ByteValue): return v.value
        return int(v)
    def to_float(v: Any) -> float: return float(numeric_value(v))
    def to_byte(v: Any) -> ByteValue:
        n = int(float(v)) if isinstance(v, str) else int(numeric_value(v))
        if not 0 <= n <= 255: raise runtime_error("byte conversion out of range")
        return ByteValue(n)
    def to_bool(v: Any) -> bool:
        if isinstance(v, str): return v.lower() == "true"
        return bool(numeric_value(v))
    def make_regex(p: str) -> RegexValue:
        try: return RegexValue(p, re.compile(p))
        except re.error as ex: raise runtime_error(f"invalid regex: {ex}")
    def println(*xs: Any) -> None: print(" ".join(solvik_string(x) for x in xs))
    def print_no_nl(*xs: Any) -> None: print(" ".join(solvik_string(x) for x in xs), end="")
    def is_type(v: Any, name: str) -> bool: return type_name_of(v) == name.lower()

    core = {
        "print": nf("print", print_no_nl), "println": nf("println", println), "string": nf("string", solvik_string),
        "int": nf("int", to_int), "float": nf("float", to_float), "byte": nf("byte", to_byte), "bool": nf("bool", to_bool),
        "typeOf": nf("typeOf", type_name_of), "isType": nf("isType", is_type), "regex": nf("regex", make_regex),
        "stack": nf("stack", lambda: StackValue()),
    }
    core["string"] = Namespace("string", {"join": nf("string.join", lambda xs, sep: sep.join(xs)), "convert": nf("string.convert", solvik_string)})
    # Conversion syntax string(x) must remain callable. Namespace + callable is
    # represented using a small hybrid object below.
    core["string"] = CallableNamespace("string", solvik_string, {"join": nf("string.join", lambda xs, sep: sep.join(xs))})

    core.update({
        "math": Namespace("math", {
            "abs": nf("math.abs", abs), "min": nf("math.min", min), "max": nf("math.max", max),
            "floor": nf("math.floor", lambda x: float(py_math.floor(x))), "ceil": nf("math.ceil", lambda x: float(py_math.ceil(x))),
            "round": nf("math.round", lambda x: float(py_math.floor(x + 0.5))), "sqrt": nf("math.sqrt", py_math.sqrt),
            "pow": nf("math.pow", py_math.pow), "sin": nf("math.sin", py_math.sin), "cos": nf("math.cos", py_math.cos), "tan": nf("math.tan", py_math.tan),
            "PI": py_math.pi, "E": py_math.e,
        }),
        "env": Namespace("env", {
            "get": nf("env.get", lambda k: os.environ.get(k)), "set": nf("env.set", lambda k,v: _env_set(k,v)),
            "keys": nf("env.keys", lambda: list(os.environ.keys())),
        }),
        "file": Namespace("file", {
            "read": nf("file.read", lambda p: pathlib.Path(p).read_text()),
            "write": nf("file.write", lambda p,s: pathlib.Path(p).write_text(s)),
            "append": nf("file.append", lambda p,s: _append(p,s)), "delete": nf("file.delete", _delete),
            "exists": nf("file.exists", lambda p: pathlib.Path(p).exists()), "temp": nf("file.temp", _temp_file), "tempDir": nf("file.tempDir", _temp_dir),
        }),
        "process": Namespace("process", {"run": nf("process.run", _process_run)}),
        "time": Namespace("time", {"now": nf("time.now", lambda: int(py_time.time()*1000)), "sleep": nf("time.sleep", lambda ms: py_time.sleep(ms/1000.0))}),
        "random": Namespace("random", {
            "float": nf("random.float", py_random.random), "int": nf("random.int", py_random.randint), "range": nf("random.range", py_random.randrange),
            "uniform": nf("random.uniform", py_random.uniform), "choice": nf("random.choice", lambda xs: None if not xs else copy_value(py_random.choice(xs))),
            "shuffle": nf("random.shuffle", lambda xs: _shuffle(xs)), "sample": nf("random.sample", lambda xs,k: _sample(xs,k)),
            "seed": nf("random.seed", py_random.seed),
        }),
        "path": Namespace("path", {
            "join": nf("path.join", os.path.join), "basename": nf("path.basename", os.path.basename), "dirname": nf("path.dirname", lambda p: os.path.dirname(p) or "."),
            "ext": nf("path.ext", lambda p: os.path.splitext(p)[1]), "abs": nf("path.abs", os.path.abspath), "exists": nf("path.exists", os.path.exists),
        }),
        "base64": Namespace("base64", {
            "encode": nf("base64.encode", lambda s: py_base64.b64encode(s.encode()).decode()),
            "decode": nf("base64.decode", lambda s: py_base64.b64decode(s).decode()),
        }),
        "hash": Namespace("hash", {
            "md5": nf("hash.md5", lambda s: hashlib.md5(s.encode()).hexdigest()), "sha1": nf("hash.sha1", lambda s: hashlib.sha1(s.encode()).hexdigest()),
            "sha256": nf("hash.sha256", lambda s: hashlib.sha256(s.encode()).hexdigest()), "sha512": nf("hash.sha512", lambda s: hashlib.sha512(s.encode()).hexdigest()),
        }),
        "secrets": Namespace("secrets", {
            "token": nf("secrets.token", lambda n: py_secrets.token_urlsafe(n)), "hex": nf("secrets.hex", lambda n: py_secrets.token_hex(n)),
        }),
    })
    return core


@dataclass
class CallableNamespace(Namespace):
    call: Callable[[Any], Any] = lambda x: x

    def __init__(self, name: str, call: Callable[..., Any], values: dict[str, Any]):
        super().__init__(name, values); self.call = call


def _env_set(k: str, v: str) -> None: os.environ[k] = v
def _append(p: str, s: str) -> None:
    with open(p, "a", encoding="utf-8") as f: f.write(s)
def _delete(p: str) -> None:
    path = pathlib.Path(p)
    if path.is_dir(): shutil.rmtree(path)
    elif path.exists(): path.unlink()
def _temp_file(prefix: str) -> str:
    fd, p = tempfile.mkstemp(prefix=prefix); os.close(fd); return p
def _temp_dir(prefix: str) -> str: return tempfile.mkdtemp(prefix=prefix)
def _process_run(command: str, *args: str) -> int: return subprocess.run([command, *args], check=False).returncode
def _shuffle(xs: list[Any]) -> list[Any]:
    out = copy_value(xs); py_random.shuffle(out); return out
def _sample(xs: list[Any], count: int) -> list[Any]:
    count = max(0, min(len(xs), count))
    return copy_value(py_random.sample(xs, count))


# =============================================================================
# Module loading
# =============================================================================

class Loader:
    def __init__(self, interpreter: Interpreter):
        self.interpreter = interpreter
        self.loaded: dict[str, Program] = {}
        self.loading: set[str] = set()
        self.entry_file: Optional[pathlib.Path] = None

    def load_entry(self, path: str) -> Program:
        p = pathlib.Path(path).expanduser().resolve(); self.entry_file = p
        program = self.load_file(p, is_entry=True, source_name=path)
        return program

    def load_file(self, path: pathlib.Path, is_entry: bool = False, source_name: Optional[str] = None) -> Program:
        key = str(path)
        if key in self.loaded: return self.loaded[key]
        if key in self.loading: raise SolvikError(f"cyclic dependency involving {path}")
        self.loading.add(key)
        source = path.read_text(encoding="utf-8")
        program = Parser(Lexer(source, source_name or str(path)).tokens()).parse()
        if not is_entry and any(isinstance(d, FunctionDecl) and d.name == "main" for d in program.declarations):
            raise SolvikError(f"library file {path} may not declare main")
        self.loaded[key] = program
        for use in program.uses:
            dep = self.resolve_use(path, use)
            if isinstance(dep, pathlib.Path): self.load_file(dep, False)
            else: self.load_url(dep, use)
        self.interpreter.add_program(program)
        self.loading.remove(key)
        return program

    def resolve_use(self, owner: pathlib.Path, use: UseDecl) -> pathlib.Path | str:
        if use.scheme == "file":
            raw = os.path.expanduser(use.value)
            if not any(ch in raw for ch in ("/", "\\")): raw = raw.replace(".", os.sep)
            if not raw.endswith(".sol"): raw += ".sol"
            p = pathlib.Path(raw)
            return p.resolve() if p.is_absolute() else (owner.parent / p).resolve()
        if use.scheme == "url": return use.value
        raise SolvikError(f"unsupported dependency scheme {use.scheme}")

    def load_url(self, url: str, use: UseDecl) -> Program:
        key = url
        if key in self.loaded: return self.loaded[key]
        with urllib.request.urlopen(url) as r: data = r.read()
        if use.checksum:
            got = hashlib.sha256(data).hexdigest()
            if got.lower() != use.checksum.lower(): raise SolvikError(f"checksum mismatch for {url}")
        source = data.decode("utf-8")
        program = Parser(Lexer(source, url).tokens()).parse()
        if any(isinstance(d, FunctionDecl) and d.name == "main" for d in program.declarations): raise SolvikError(f"library URL {url} may not declare main")
        self.loaded[key] = program; self.interpreter.add_program(program); return program


# =============================================================================
# CLI
# =============================================================================

def parse_source(source: str, filename: str = "<source>") -> Program:
    return Parser(Lexer(source, filename).tokens()).parse()


def run_file(path: str) -> int:
    try:
        interp = Interpreter(); loader = Loader(interp); entry = loader.load_entry(path)
        return interp.run(entry.package)
    except RuntimeSignal as ex:
        code = f" [{ex.value.code}]" if ex.value.code else ""
        print(f"uncaught exception{code}: {ex.value.message}", file=sys.stderr)
        return 2
    except SolvikError as ex:
        print(str(ex), file=sys.stderr)
        return 1


def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Solvik semantic reference interpreter")
    ap.add_argument("file", nargs="?", help="Solvik source file")
    ap.add_argument("--check", action="store_true", help="parse and resolve dependencies without executing (syntax check)")
    ap.add_argument("--version", action="store_true")
    args = ap.parse_args(argv)
    if args.version:
        print(f"solvik version {REFERENCE_VERSION}"); return 0
    if not args.file: ap.error("a source file is required")
    if args.check:
        try:
            interp = Interpreter(); loader = Loader(interp); loader.load_entry(args.file); return 0
        except SolvikError as ex:
            print(str(ex), file=sys.stderr); return 1
    return run_file(args.file)


if __name__ == "__main__":
    raise SystemExit(main())
