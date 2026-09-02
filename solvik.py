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
LANGUAGE.md defines the normative language contract. This Python interpreter is
the executable semantic reference: when an implementation disagrees with it on
observable language behavior, the Python result is authoritative unless
LANGUAGE.md is changed deliberately at the same time. Go and Rust are optimized
implementations that must demonstrate parity against this reference.

Semantic design priorities
--------------------------
1. LANGUAGE.md semantics win over implementation convenience.
2. Observable behavior matters; Go bytecode/VM architecture does not.
3. Immutable bindings and struct receiver mutability are separate concepts.
4. Struct values cross ordinary read/assignment/call/return boundaries by value.
5. `??` short-circuits on null only; false, zero, empty string/list remain values.
6. Numeric widening is byte -> int -> float; narrowing is explicit.
7. Traits are structural contracts for built-in and user-defined value types.
8. Generic type parameters are erased at runtime only after their constraints
   and concrete substitutions have been established.
9. Catchable language faults use SolvikExceptionValue/RuntimeSignal only.
10. Standard-library behavior is kept in one explicit semantic table.
11. Prefer boring, local code over abstractions that obscure a language rule.

Python 3.11+; standard library only.
"""
from __future__ import annotations

import argparse
import base64 as py_base64
import copy
import datetime as py_datetime
import functools
import hashlib
import json as py_json
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

@dataclass
class TypeRef:
    """A type reference. Mutable so the Phase 5 canonicalization pass can
    rewrite names in place; never used as a dict key."""
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

BUILTIN_TYPE_NAMES = {
    "bool", "byte", "int", "float", "char", "string",
    "list", "map", "stack", "any", "void", "exception", "regex",
    "func", "null", "<unknown>",
}
CORE_TRAIT_NAMES = {"Stringable", "Equatable", "Comparable", "Hashable", "Countable", "Iterable", "Collection"}


def split_type_name(name: str) -> tuple[str, str]:
    """Split a (possibly qualified) type name into (package, local name)."""
    if "." in name:
        pkg, _, local = name.rpartition(".")
        return pkg, local
    return "", name


def type_key(name: str, package: str) -> tuple[str, str]:
    """Canonical (package, local) identity key for a type name."""
    pkg, local = split_type_name(name)
    return (package, local) if not pkg else (pkg, local)


def dotted_expression_name(expr: Any) -> Optional[str]:
    """Dotted name of a Name or member chain, else None."""
    if isinstance(expr, Name):
        return expr.name
    if isinstance(expr, Member):
        base = dotted_expression_name(expr.obj)
        if base is not None:
            return base + "." + expr.name
    return None


def canonicalize_type(typ: TypeRef, package: str, params: set[str]) -> None:
    """Qualify an unqualified type name with its package (canonical identity).

    Built-ins, core traits, type parameters, and already-qualified names stay
    as written. Idempotent: dotted names are never re-qualified.
    """
    if typ.name and "." not in typ.name and typ.name not in params \
            and typ.name not in BUILTIN_TYPE_NAMES and typ.name not in CORE_TRAIT_NAMES:
        typ.name = f"{package}.{typ.name}"
    for arg in typ.args:
        canonicalize_type(arg, package, params)


def canonicalize_expr(e: Any, package: str, params: set[str]) -> None:
    if isinstance(e, Literal):
        return
    if isinstance(e, Name):
        for a in e.type_args:
            canonicalize_type(a, package, params)
        return
    if isinstance(e, Unary):
        canonicalize_expr(e.expr, package, params); return
    if isinstance(e, Binary):
        canonicalize_expr(e.left, package, params); canonicalize_expr(e.right, package, params); return
    if isinstance(e, Assign):
        canonicalize_expr(e.target, package, params); canonicalize_expr(e.value, package, params); return
    if isinstance(e, Call):
        for a in e.type_args:
            canonicalize_type(a, package, params)
        for a in e.args:
            canonicalize_expr(a.expr, package, params)
        canonicalize_expr(e.callee, package, params); return
    if isinstance(e, Member):
        canonicalize_expr(e.obj, package, params); return
    if isinstance(e, Index):
        canonicalize_expr(e.obj, package, params); canonicalize_expr(e.index, package, params); return
    if isinstance(e, ListExpr):
        for x in e.items:
            canonicalize_expr(x, package, params)
        return
    if isinstance(e, MapExpr):
        for k, v in e.items:
            canonicalize_expr(k, package, params)
            canonicalize_expr(v, package, params)
        return
    if isinstance(e, StructExpr):
        if e.type_name and "." not in e.type_name:
            e.type_name = f"{package}.{e.type_name}"
        for a in e.type_args:
            canonicalize_type(a, package, params)
        for _, v in e.fields:
            canonicalize_expr(v, package, params)
        return
    if isinstance(e, FuncExpr):
        for p in e.params:
            canonicalize_type(p.type, package, params)
        canonicalize_type(e.return_type, package, params)
        canonicalize_block(e.body, package, params)


def canonicalize_block(b: Block, package: str, params: set[str]) -> None:
    for s in b.statements:
        canonicalize_statement(s, package, params)


def canonicalize_statement(s: Any, package: str, params: set[str]) -> None:
    if isinstance(s, VarDecl):
        canonicalize_type(s.type, package, params)
        canonicalize_expr(s.value, package, params)
    elif isinstance(s, ExprStmt):
        canonicalize_expr(s.expr, package, params)
    elif isinstance(s, IfStmt):
        canonicalize_expr(s.condition, package, params)
        canonicalize_block(s.then_block, package, params)
        if isinstance(s.else_branch, Block):
            canonicalize_block(s.else_branch, package, params)
        elif isinstance(s.else_branch, IfStmt):
            canonicalize_statement(s.else_branch, package, params)
    elif isinstance(s, WhileStmt):
        canonicalize_expr(s.condition, package, params)
        canonicalize_block(s.body, package, params)
    elif isinstance(s, ForStmt):
        canonicalize_expr(s.iterable, package, params)
        canonicalize_block(s.body, package, params)
    elif isinstance(s, SwitchStmt):
        canonicalize_expr(s.value, package, params)
        for c in s.cases:
            if c.expr is not None:
                canonicalize_expr(c.expr, package, params)
            canonicalize_block(c.body, package, params)
    elif isinstance(s, TryStmt):
        canonicalize_block(s.try_block, package, params)
        if s.catch_block is not None:
            if s.catch_type is not None:
                canonicalize_type(s.catch_type, package, params)
            canonicalize_block(s.catch_block, package, params)
        if s.finally_block is not None:
            canonicalize_block(s.finally_block, package, params)
    elif isinstance(s, ThrowStmt):
        canonicalize_expr(s.value, package, params)
    elif isinstance(s, ReturnStmt) and s.value is not None:
        canonicalize_expr(s.value, package, params)


def canonicalize_function(f: FunctionDecl, package: str, params: set[str]) -> None:
    for p in f.params:
        canonicalize_type(p.type, package, params)
    canonicalize_type(f.return_type, package, params)
    if f.body is not None:
        canonicalize_block(f.body, package, params)


def canonicalize_program(program: Program) -> None:
    """Rewrite every type reference in a parsed program to its canonical
    `package.Type` identity (see Phase 5). Idempotent."""
    package = program.package
    for d in program.declarations:
        if isinstance(d, FunctionDecl):
            canonicalize_function(d, package, {p.name for p in d.type_params})
        elif isinstance(d, StructDecl):
            params = {p.name for p in d.type_params}
            for field in d.fields:
                canonicalize_type(field.type, package, params)
            for m in d.methods:
                canonicalize_function(m, package, params | {p.name for p in m.type_params})
        elif isinstance(d, EnumDecl):
            params = {p.name for p in d.type_params}
            for m in d.members:
                for pt in m.payload_types:
                    canonicalize_type(pt, package, params)
        elif isinstance(d, TraitDecl):
            params = {p.name for p in d.type_params}
            for m in d.methods:
                canonicalize_function(m, package, params)


@dataclass(frozen=True)
class TypeParam:
    name: str
    constraints: tuple[TypeRef, ...] = ()

    def __str__(self) -> str:
        if not self.constraints:
            return self.name
        return self.name + ": " + " & ".join(map(str, self.constraints))


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
    type_params: tuple[TypeParam, ...] = ()

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
    type_params: tuple[TypeParam, ...] = ()
    public: bool = False

@dataclass
class TraitDecl:
    name: str
    methods: list[FunctionDecl]
    pos: SourcePos
    type_params: tuple[TypeParam, ...] = ()
    public: bool = False

@dataclass
class EnumMember:
    name: str
    value: Optional[int]
    payload_types: tuple[TypeRef, ...] = ()

@dataclass
class EnumDecl:
    name: str
    members: list[EnumMember]
    pos: SourcePos
    type_params: tuple[TypeParam, ...] = ()
    public: bool = False

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
    type_args: tuple[TypeRef, ...] = ()
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
    type_args: tuple[TypeRef, ...] = ()
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
    type_args: tuple[TypeRef, ...] = ()
@dataclass
class FuncExpr:
    """Anonymous function expression: `func(x: int) -> int { ... }`."""
    params: list[Param]
    return_type: TypeRef
    body: Block
    pos: SourcePos


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
            if not self.at(TK.EOF) and self.ts[self.i - 1].kind not in (TK.NEWLINE, TK.SEMI):
                raise DiagnosticError(
                    "P078",
                    self.cur().pos,
                    "expected a newline or semicolon after declaration",
                    1,
                    "parse",
                )
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
        if self.at(TK.STRUCT): return self.parse_struct(public=public)
        if self.at(TK.TRAIT): return self.parse_trait(public=public)
        if self.at(TK.ENUM): return self.parse_enum(public=public)
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
        if self.at(TK.FUNC):
            # Function type: func<P1, ..., Pn, R> — the last argument is the
            # return type, so `func<int>` is `() -> int` and `func<int, void>`
            # is `(int) -> void`.
            pos = self.expect(TK.FUNC).pos
            if not self.match(TK.LT):
                raise DiagnosticError("P076", pos, "function types require at least a return type; write func<ReturnType> or func<P1, ..., ReturnType>", 4, "parse")
            self.skip_newlines()
            args = [self.parse_type()]
            while self.match(TK.COMMA):
                self.skip_newlines()
                args.append(self.parse_type())
            self.skip_newlines()
            self.expect_type_gt()
            nullable = bool(self.match(TK.QUESTION))
            return TypeRef("func", tuple(args), nullable)
        name = self.expect(TK.IDENT, "expected type name").text
        while self.match(TK.DOT):
            # Qualified type names: http.Client, collections.Box<int>.
            name += "." + self.expect(TK.IDENT, "expected type name after '.'").text
        args: list[TypeRef] = []
        if self.match(TK.LT):
            self.skip_newlines()
            args.append(self.parse_type())
            while self.match(TK.COMMA): self.skip_newlines(); args.append(self.parse_type())
            self.skip_newlines(); self.expect_type_gt()
        nullable = bool(self.match(TK.QUESTION))
        return TypeRef(name, tuple(args), nullable)

    def parse_type_params(self) -> tuple[TypeParam, ...]:
        if not self.match(TK.LT):
            return ()
        self.skip_newlines()
        params: list[TypeParam] = []
        seen: set[str] = set()
        while True:
            name_token = self.expect(TK.IDENT, "expected generic type parameter")
            if name_token.text in seen:
                raise ParseError(name_token.pos, f"duplicate generic type parameter {name_token.text}")
            seen.add(name_token.text)
            constraints: list[TypeRef] = []
            if self.match(TK.COLON):
                constraints.append(self.parse_type())
                while self.match(TK.AMP):
                    constraints.append(self.parse_type())
            params.append(TypeParam(name_token.text, tuple(constraints)))
            self.skip_newlines()
            if not self.match(TK.COMMA):
                break
            self.skip_newlines()
        self.expect_type_gt()
        return tuple(params)

    def parse_param_list(self) -> list[Param]:
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
        return params

    def parse_func_expr(self, pos: SourcePos) -> FuncExpr:
        """Anonymous function (closure) expression."""
        if self.at(TK.LT):
            raise ParseError(pos, "anonymous functions cannot declare type parameters; write func(name: type) ...")
        params = self.parse_param_list()
        rtype = VOID_T
        if self.match(TK.ARROW): rtype = self.parse_type()
        self.skip_newlines()
        body = self.parse_block()
        return FuncExpr(params, rtype, body, pos)

    def parse_function(self, public: bool = False, mutating: bool = False, owner: Optional[str] = None, body_required: bool = True) -> FunctionDecl:
        p = self.expect(TK.FUNC).pos
        name = self.expect(TK.IDENT, "expected function name").text
        type_params = self.parse_type_params()
        params = self.parse_param_list()
        rtype = VOID_T
        if self.match(TK.ARROW): rtype = self.parse_type()
        self.skip_newlines()
        body = self.parse_block() if body_required else None
        return FunctionDecl(name, params, rtype, body, p, public, mutating, owner, type_params)

    def parse_struct(self, public: bool = False) -> StructDecl:
        p = self.expect(TK.STRUCT).pos; name = self.expect(TK.IDENT).text
        type_params = self.parse_type_params()
        self.skip_newlines(); self.expect(TK.LBRACE); self.skip_terms()
        fields: list[FieldDecl] = []; methods: list[FunctionDecl] = []
        while not self.at(TK.RBRACE):
            member_public = bool(self.match(TK.PUB)); mut = bool(self.match(TK.MUT))
            if self.at(TK.FUNC):
                methods.append(self.parse_function(public=member_public, mutating=mut, owner=name)); self.skip_terms(); continue
            field_token = self.expect(TK.IDENT, "expected struct field or method")
            fname = field_token.text
            self.expect(TK.COLON); ftype = self.parse_type()
            fields.append(FieldDecl(fname, ftype, member_public, mut, field_token.pos))
            self.match(TK.COMMA); self.skip_terms()
            if not self.at(TK.RBRACE) and self.ts[self.i - 1].kind not in (TK.NEWLINE, TK.SEMI, TK.COMMA):
                raise DiagnosticError(
                    "P078",
                    self.cur().pos,
                    "expected a newline, semicolon, or comma after struct member",
                    1,
                    "parse",
                )
        self.expect(TK.RBRACE)
        return StructDecl(name, fields, methods, p, type_params, public)

    def parse_trait(self, public: bool = False) -> TraitDecl:
        p = self.expect(TK.TRAIT).pos; name = self.expect(TK.IDENT).text
        type_params = self.parse_type_params()
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
        return TraitDecl(name, methods, p, type_params, public)

    def parse_enum(self, public: bool = False) -> EnumDecl:
        p = self.expect(TK.ENUM).pos; name = self.expect(TK.IDENT).text
        type_params = self.parse_type_params()
        self.skip_newlines(); self.expect(TK.LBRACE); self.skip_terms()
        members: list[EnumMember] = []
        while not self.at(TK.RBRACE):
            n = self.expect(TK.IDENT).text
            payload: list[TypeRef] = []
            if self.match(TK.LPAREN):
                self.skip_newlines()
                payload.append(self.parse_type())
                while self.match(TK.COMMA):
                    self.skip_newlines()
                    payload.append(self.parse_type())
                self.skip_newlines()
                self.expect(TK.RPAREN)
            value = None
            if self.match(TK.ASSIGN):
                if payload:
                    raise DiagnosticError("P077", p, f"payload case '{n}' cannot declare an integer value; an enum with payload cases uses names only", 4, "parse")
                sign = -1 if self.match(TK.MINUS) else 1
                value = sign * self.expect(TK.INT, "enum value must be an integer literal").value
            members.append(EnumMember(n, value, tuple(payload)))
            self.match(TK.COMMA); self.skip_terms()
        self.expect(TK.RBRACE)
        return EnumDecl(name, members, p, type_params, public)

    def parse_block(self) -> Block:
        p = self.expect(TK.LBRACE).pos; self.skip_terms(); items: list[Any] = []
        while not self.at(TK.RBRACE):
            if self.at(TK.EOF): raise ParseError(p, "unterminated block")
            items.append(self.parse_statement())
            self.skip_terms()
            if not self.at(TK.RBRACE) and not self.at(TK.EOF) and self.ts[self.i - 1].kind not in (TK.NEWLINE, TK.SEMI):
                raise DiagnosticError(
                    "P078",
                    self.cur().pos,
                    "expected a newline or semicolon after statement",
                    1,
                    "parse",
                )
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
                # Case expressions disallow struct literals so `case Option.None {`
                # (an enum constant followed by the case body) is unambiguous.
                e = self.parse_expr(allow_struct_literal=False); self.skip_newlines(); cases.append(SwitchCase(e, self.parse_block()))
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

    def try_parse_type_args(self, follow: tuple[TK, ...]) -> Optional[tuple[TypeRef, ...]]:
        """Attempt to parse explicit generic type arguments `<T, ...>`.

        Used in expression position, where `<` is normally the comparison
        operator. Parsing runs on a scratch copy of the token stream so a
        failed attempt (e.g. `a < b`) leaves the parser untouched, including
        tokens inserted while splitting `>>` at a generic closer. The parse is
        committed only when the argument list is followed by one of `follow`
        (a call `(` or a struct literal `{`), which mirrors how Go resolves
        the same ambiguity.
        """
        if not self.at(TK.LT):
            return None
        clone = Parser(list(self.ts))
        clone.i = self.i
        try:
            clone.advance()  # '<'
            args = [clone.parse_type()]
            while clone.match(TK.COMMA):
                clone.skip_newlines()
                args.append(clone.parse_type())
            clone.expect_type_gt()
            if clone.cur().kind not in follow:
                return None
        except ParseError:
            return None
        self.ts = clone.ts
        self.i = clone.i
        return tuple(args)

    def parse_call_args(self) -> list[CallArg]:
        self.expect(TK.LPAREN); args: list[CallArg] = []; self.skip_newlines()
        if not self.at(TK.RPAREN):
            while True:
                e = self.parse_expr(); spread = bool(self.match(TK.ELLIPSIS)); args.append(CallArg(e, spread))
                self.skip_newlines()
                if not self.match(TK.COMMA): break
                self.skip_newlines()
                if self.at(TK.RPAREN): break
        self.expect(TK.RPAREN)
        return args

    def parse_expr(self, min_prec: int = 0, allow_struct_literal: bool = True) -> Any:
        self.skip_newlines()
        left = self.parse_prefix(allow_struct_literal)
        while True:
            self.skip_newlines()
            # postfix operators
            if self.at(TK.LPAREN):
                args = self.parse_call_args()
                left = Call(left, args, getattr(left, "pos", self.cur().pos)); continue
            if self.match(TK.LBRACKET):
                idx = self.parse_expr(); self.expect(TK.RBRACKET); left = Index(left, idx, getattr(left, "pos", self.cur().pos)); continue
            if self.match(TK.DOT):
                n = self.expect(TK.IDENT, "expected member name").text; left = Member(left, n, getattr(left, "pos", self.cur().pos)); continue
            # Explicit generic instantiation: Name<...>( or obj.m<...>( for
            # calls, Name<...> { for struct literals, Name<...> .member for
            # qualified enum-case access like Result<int, string>.Ok(5).
            if self.at(TK.LT) and isinstance(left, (Name, Member)):
                if isinstance(left, Name):
                    follow = (TK.LPAREN, TK.DOT) if not allow_struct_literal else (TK.LPAREN, TK.LBRACE, TK.DOT)
                elif dotted_expression_name(left) is not None:
                    # Qualified type path: http.Client<int> { ... } or
                    # http.Result<int, string>.Ok(...).
                    follow = (TK.LPAREN, TK.LBRACE) if allow_struct_literal else (TK.LPAREN, TK.DOT)
                else:
                    follow = (TK.LPAREN,)
                targs = self.try_parse_type_args(follow)
                if targs is not None:
                    if self.at(TK.DOT):
                        left.type_args = targs
                    elif self.at(TK.LBRACE):
                        left = self.parse_struct_literal(left, targs)
                    else:
                        left = Call(left, self.parse_call_args(), left.pos, targs)
                    continue
            # Struct literal is restricted to a simple type name or a
            # package-qualified type path on the left.
            if allow_struct_literal and self.at(TK.LBRACE) and isinstance(left, (Name, Member)) and dotted_expression_name(left) is not None:
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

    def parse_prefix(self, allow_struct_literal: bool = True) -> Any:
        t = self.advance()
        if t.kind is TK.INT: return Literal(t.value, "int", t.pos)
        if t.kind is TK.FLOAT: return Literal(t.value, "float", t.pos)
        if t.kind is TK.STRING: return Literal(t.value, "string", t.pos)
        if t.kind is TK.CHAR: return Literal(CharValue(t.value), "char", t.pos)
        if t.kind is TK.TRUE: return Literal(True, "bool", t.pos)
        if t.kind is TK.FALSE: return Literal(False, "bool", t.pos)
        if t.kind is TK.NULL: return Literal(None, "null", t.pos)
        if t.kind is TK.IDENT: return Name(t.text, t.pos)
        if t.kind is TK.FUNC: return self.parse_func_expr(t.pos)
        if t.kind in (TK.BANG, TK.MINUS, TK.TILDE, TK.PLUS): return Unary(t.text, self.parse_expr(13, allow_struct_literal), t.pos)
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

    def parse_struct_literal(self, type_expr: Any, type_args: tuple[TypeRef, ...] = ()) -> StructExpr:
        name = dotted_expression_name(type_expr)
        if name is None:
            raise ParseError(type_expr.pos, "struct literal requires a type name")
        self.expect(TK.LBRACE); self.skip_newlines(); fields: list[tuple[str, Any]] = []
        if not self.at(TK.RBRACE):
            while True:
                fname = self.expect(TK.IDENT, "expected struct field name").text
                self.expect(TK.COLON); value = self.parse_expr(); fields.append((fname, value)); self.skip_newlines()
                if not self.match(TK.COMMA): break
                self.skip_newlines()
                if self.at(TK.RBRACE): break
        self.expect(TK.RBRACE)
        if not type_args:
            type_args = getattr(type_expr, "type_args", ())
        return StructExpr(name, fields, type_expr.pos, type_args)


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
    payload: tuple[Any, ...] = ()
    type_args: tuple[TypeRef, ...] = ()
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
    type_args: tuple[TypeRef, ...] = ()

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

@dataclass(eq=False)
class ClosureValue:
    """An anonymous function together with its captured lexical environment.

    Capture model: the closure references the environment chain that was
    current at the point of definition. Immutable bindings never change after
    initialization, so capturing them behaves like a value copy; `mut`
    bindings share storage with the enclosing scope. The resolved `type_ref`
    is the closure's function type (parameters..., return).

    Equality is reference identity (eq=False), matching the language rule that
    distinct closures are never equal even with identical behavior."""
    decl: FunctionDecl
    env: "Env"
    package: str
    receiver: Optional["StructValue"]
    receiver_mutable: bool
    type_ref: TypeRef

@dataclass
class StructTypeValue:
    decl: StructDecl

@dataclass
class EnumTypeValue:
    decl: EnumDecl
    members: dict[str, EnumValue]
    canonical_name: str = ""

@dataclass
class CaseConstructor:
    """Callable produced by qualified enum-case access (`Result.Ok`).

    Calling it constructs an enum value with the case's payload. `type_args`
    are the explicit generic arguments when written
    (`Result<int, string>.Ok`), empty when they must be inferred from the
    payload values or seeded by an expected type."""
    enum_name: str
    case_name: str
    payload_types: tuple[TypeRef, ...]
    type_args: tuple[TypeRef, ...]
    package: str


# =============================================================================
# Semantic rules
# =============================================================================

def enum_pattern_shape(expr: Any) -> Optional[tuple[str, Optional[Name], str, list[Any]]]:
    """Shape of a possible enum-case pattern in a switch case.

    Returns (kind, enum_name_expr, case_name, elements) where kind is
    "qualified" for `Enum.Case(...)` / `Enum<Args>.Case(...)` and "bare" for
    `Case(...)` (same-enum omission). Resolution decides whether the shape is
    actually a pattern: the callee must resolve to an enum case member.
    """
    if isinstance(expr, Call):
        elements = [a.expr for a in expr.args]
        if isinstance(expr.callee, Member) and dotted_expression_name(expr.callee.obj) is not None:
            return ("qualified", expr.callee.obj, expr.callee.name, elements)
        if isinstance(expr.callee, Name):
            return ("bare", None, expr.callee.name, elements)
    return None


def copy_value(value: Any) -> Any:
    """Solvik structs have value semantics. Collections also copy recursively here.

    Keeping this rule centralized makes assignment/call/return copy boundaries
    easy to audit. Native opaque values (regex, exceptions) are intentionally
    shared.
    """
    if isinstance(value, StructValue):
        return StructValue(value.type_name, {k: copy_value(v) for k, v in value.fields.items()}, value.type_args)
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
    if isinstance(v, CharValue): return "char"
    if isinstance(v, (int, float)) and not isinstance(v, bool): return "int" if isinstance(v, int) else "float"
    if isinstance(v, str): return "string"
    if isinstance(v, (list, dict, StackValue)): return "list" if isinstance(v, list) else ("map" if isinstance(v, dict) else "stack")
    if isinstance(v, StructValue): return v.type_name.rsplit(".", 1)[-1].lower()
    if isinstance(v, EnumValue): return v.enum_name.rsplit(".", 1)[-1].lower()
    if isinstance(v, (UserFunction, ClosureValue, BoundMethod, NativeFunction)): return "function"
    if isinstance(v, SolvikExceptionValue): return "exception"
    if isinstance(v, RegexValue): return "regex"
    return "any"


def solvik_string(v: Any) -> str:
    if v is None: return "null"
    if isinstance(v, bool): return "true" if v else "false"
    if isinstance(v, ByteValue): return str(v.value)
    if isinstance(v, CharValue): return str(v)
    if isinstance(v, EnumValue):
        if v.payload:
            return v.member_name + "(" + ", ".join(solvik_string(x) for x in v.payload) + ")"
        return str(v.value)
    if isinstance(v, float):
        return str(v) if not v.is_integer() else str(int(v))
    if isinstance(v, list): return "[" + " ".join(solvik_string(x) for x in v) + "]"
    if isinstance(v, StackValue): return "[" + " ".join(solvik_string(x) for x in v.items) + "]"
    if isinstance(v, dict): return "map[" + " ".join(f"{solvik_string(k)}:{solvik_string(val)}" for k,val in v.items()) + "]"
    if isinstance(v, StructValue): return v.type_name.rsplit(".", 1)[-1] + "{" + ", ".join(f"{k}: {solvik_string(x)}" for k,x in v.fields.items()) + "}"
    if isinstance(v, SolvikExceptionValue): return v.message
    if isinstance(v, ClosureValue): return "<closure>"
    if isinstance(v, UserFunction): return f"<function {v.decl.name}>"
    if isinstance(v, BoundMethod): return f"<function {v.function.decl.name}>"
    if isinstance(v, NativeFunction): return f"<function {v.name}>"
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
class MethodSig:
    params: tuple[TypeRef, ...]
    return_type: TypeRef
    mutating: bool = False


def substitute_type(typ: TypeRef, bindings: dict[str, TypeRef]) -> TypeRef:
    replacement = bindings.get(typ.name)
    if replacement is not None and not typ.args:
        if typ.nullable and not replacement.nullable:
            return TypeRef(replacement.name, replacement.args, True)
        return replacement
    return TypeRef(typ.name, tuple(substitute_type(a, bindings) for a in typ.args), typ.nullable)


def free_names(typ: TypeRef) -> set[str]:
    """Names mentioned by a type, including inside generic arguments."""
    names = {typ.name}
    for arg in typ.args:
        names |= free_names(arg)
    return names


def bind_type_pattern(pattern: TypeRef, actual: TypeRef, variables: set[str], bindings: dict[str, TypeRef]) -> bool:
    if pattern.name in variables and not pattern.args:
        previous = bindings.get(pattern.name)
        if actual.name == "null":
            # A null value carries no type evidence and never determines (or
            # overrides) a type parameter.
            return True
        if previous is None or previous == UNKNOWN_T:
            bindings[pattern.name] = actual.nonnull() if pattern.nullable else actual
            return True
        return actual == UNKNOWN_T or previous == actual or previous == actual.nonnull()
    if pattern.name != actual.name:
        return actual == UNKNOWN_T
    if pattern.args and len(pattern.args) != len(actual.args):
        return False
    return all(bind_type_pattern(p, a, variables, bindings) for p, a in zip(pattern.args, actual.args))


def value_type_ref(value: Any) -> TypeRef:
    if value is None: return NULL_T
    if isinstance(value, bool): return TypeRef("bool")
    if isinstance(value, ByteValue): return TypeRef("byte")
    if isinstance(value, EnumValue): return TypeRef(value.enum_name, value.type_args)
    if isinstance(value, CharValue): return TypeRef("char")
    if isinstance(value, int) and not isinstance(value, bool): return TypeRef("int")
    if isinstance(value, float): return TypeRef("float")
    if isinstance(value, str): return TypeRef("string")
    if isinstance(value, list):
        element = value_type_ref(value[0]) if value else UNKNOWN_T
        return TypeRef("list", (element,))
    if isinstance(value, dict):
        if value:
            k, v = next(iter(value.items()))
            return TypeRef("map", (value_type_ref(k), value_type_ref(v)))
        return TypeRef("map", (UNKNOWN_T, UNKNOWN_T))
    if isinstance(value, StackValue):
        element = value_type_ref(value.items[0]) if value.items else UNKNOWN_T
        return TypeRef("stack", (element,))
    if isinstance(value, StructValue): return TypeRef(value.type_name, value.type_args)
    if isinstance(value, ClosureValue): return function_value_type(value)
    if isinstance(value, UserFunction): return function_value_type(value)
    if isinstance(value, BoundMethod): return function_value_type(value)
    if isinstance(value, SolvikExceptionValue): return EXCEPTION_T
    if isinstance(value, RegexValue): return REGEX_T
    return ANY_T


INTERP: Optional["Interpreter"] = None
"""The active interpreter. Callable-value typing needs it to resolve generic
struct receivers for bound methods; one interpreter runs per process."""


def _is_variadic(decl: FunctionDecl) -> bool:
    return bool(decl.params and decl.params[-1].variadic)


def function_value_type(value: Any) -> TypeRef:
    """Function type of a callable value: parameters..., return.

    Generic and variadic callables have no assignable function type (their
    signatures are not fixed), so they surface as `any`."""
    if isinstance(value, ClosureValue):
        if _is_variadic(value.decl): return ANY_T
        return value.type_ref
    if isinstance(value, BoundMethod):
        d = value.function.decl
        if d.type_params or _is_variadic(d): return ANY_T
        owner = INTERP.structs.get((value.function.package, d.owner_struct)) if (INTERP and d.owner_struct) else None
        bindings = {p.name: a for p, a in zip(owner.type_params, value.receiver.type_args)} if owner else {}
        return TypeRef("func", tuple(substitute_type(p.type, bindings) for p in d.params) + (substitute_type(d.return_type, bindings),))
    if isinstance(value, UserFunction):
        d = value.decl
        if d.type_params or _is_variadic(d): return ANY_T
        return TypeRef("func", tuple(p.type for p in d.params) + (d.return_type,))
    return ANY_T


def _same_signature(a: MethodSig, b: MethodSig) -> bool:
    if a.mutating != b.mutating or len(a.params) != len(b.params):
        return False
    def same_type(x: TypeRef, y: TypeRef) -> bool:
        if x == UNKNOWN_T or y == UNKNOWN_T:
            return True
        if x.name == "any" or y.name == "any":
            return True
        return x == y
    return same_type(a.return_type, b.return_type) and all(same_type(x, y) for x, y in zip(a.params, b.params))


def unify_trait_argument(need: TypeRef, have: TypeRef, variables: set[str], found: dict[str, TypeRef]) -> None:
    """Infer not-yet-bound trait type arguments from a concrete signature."""
    if need.name in variables and not need.args:
        if have != UNKNOWN_T and need.name not in found:
            found[need.name] = have.nonnull() if need.nullable else have
        return
    if need.name == have.name and len(need.args) == len(have.args):
        for n, h in zip(need.args, have.args):
            unify_trait_argument(n, h, variables, found)


def trait_satisfaction(
    actual: TypeRef,
    expected: TypeRef,
    trait: TraitDecl,
    method_sig: Callable[[TypeRef, str], Optional[MethodSig]],
    variables: frozenset[str] = frozenset(),
) -> tuple[bool, dict[str, TypeRef]]:
    """Central structural trait satisfaction check.

    `variables` names function-level type parameters that are not yet bound.
    When such a name appears as a trait type argument (`func apply<X, C: Sized<X>>`),
    it is solved from the actual type's method signatures. Returns the verdict
    and any solved bindings for those variables.
    """
    if trait.type_params and len(expected.args) != len(trait.type_params):
        return False, {}
    bindings = {p.name: a for p, a in zip(trait.type_params, expected.args)}
    have_sigs: dict[str, MethodSig] = {}
    for need in trait.methods:
        have = method_sig(actual, need.name)
        if have is None:
            return False, {}
        have_sigs[need.name] = have
    solved: dict[str, TypeRef] = {}
    if variables:
        # Pass 1: solve unresolved trait arguments from method shapes.
        for need in trait.methods:
            have = have_sigs[need.name]
            for p, h in zip(need.params, have.params):
                unify_trait_argument(substitute_type(p.type, bindings), h, variables, solved)
            unify_trait_argument(substitute_type(need.return_type, bindings), have.return_type, variables, solved)
    def resolve(t: TypeRef) -> TypeRef:
        # Resolve trait parameters, then any solved function-level variables.
        return substitute_type(substitute_type(t, bindings), solved)
    for need in trait.methods:
        # Pass 2: verify every method against the (possibly completed) bindings.
        need_sig = MethodSig(
            tuple(resolve(p.type) for p in need.params),
            resolve(need.return_type),
            need.mutating,
        )
        if not _same_signature(have_sigs[need.name], need_sig):
            return False, {}
    return True, solved


def builtin_method_signature(typ: TypeRef, name: str) -> Optional[MethodSig]:
    base = typ.nonnull()
    t = base.args[0] if base.args else UNKNOWN_T
    if base.name in ("bool", "byte", "int", "float", "char", "string", "list", "map", "stack", "func"):
        universal = {
            "string": MethodSig((), TypeRef("string")),
            "equals": MethodSig((ANY_T,), TypeRef("bool")),
        }
        if name in universal:
            return universal[name]
    if base.name in ("byte", "int", "float") and name == "abs":
        return MethodSig((), base)
    if base.name in ("byte", "int", "float", "char", "string"):
        if name == "compare": return MethodSig((ANY_T,), TypeRef("int"))
        if name == "hash": return MethodSig((), TypeRef("int"))
    if base.name == "string":
        table = {
            "len": MethodSig((), TypeRef("int")),
            "isEmpty": MethodSig((), TypeRef("bool")),
            "contains": MethodSig((TypeRef("string"),), TypeRef("bool")),
            "startsWith": MethodSig((TypeRef("string"),), TypeRef("bool")),
            "endsWith": MethodSig((TypeRef("string"),), TypeRef("bool")),
            "indexOf": MethodSig((TypeRef("string"),), TypeRef("int")),
            "byteLength": MethodSig((), TypeRef("int")),
            "charAt": MethodSig((TypeRef("int"),), TypeRef("char")),
            "substring": MethodSig((TypeRef("int"), TypeRef("int")), TypeRef("string")),
            "toUpper": MethodSig((), TypeRef("string")),
            "toLower": MethodSig((), TypeRef("string")),
            "trim": MethodSig((), TypeRef("string")),
            "split": MethodSig((TypeRef("string"),), TypeRef("list", (TypeRef("string"),))),
            "iterator": MethodSig((), TypeRef("list", (TypeRef("char"),))),
        }
        return table.get(name)
    if base.name == "list":
        return {
            "len": MethodSig((), TypeRef("int")),
            "isEmpty": MethodSig((), TypeRef("bool")),
            "contains": MethodSig((t,), TypeRef("bool")),
            "iterator": MethodSig((), TypeRef("list", (t,))),
            "map": MethodSig((TypeRef("func", (t, UNKNOWN_T)),), TypeRef("list", (UNKNOWN_T,))),
            "filter": MethodSig((TypeRef("func", (t, TypeRef("bool"))),), TypeRef("list", (t,))),
            "reduce": MethodSig((TypeRef("func", (t, t, t)),), t),
            "fold": MethodSig((UNKNOWN_T, TypeRef("func", (UNKNOWN_T, t, UNKNOWN_T))), UNKNOWN_T),
            "find": MethodSig((TypeRef("func", (t, TypeRef("bool"))),), TypeRef(t.name, t.args, True)),
            "any": MethodSig((TypeRef("func", (t, TypeRef("bool"))),), TypeRef("bool")),
            "all": MethodSig((TypeRef("func", (t, TypeRef("bool"))),), TypeRef("bool")),
            "first": MethodSig((), TypeRef(t.name, t.args, True)),
            "last": MethodSig((), TypeRef(t.name, t.args, True)),
            "reverse": MethodSig((), TypeRef("list", (t,))),
            "sort": MethodSig((TypeRef("func", (t, t, TypeRef("int"))),), TypeRef("list", (t,))),
        }.get(name)
    if base.name == "map":
        key = base.args[0] if len(base.args) >= 1 else UNKNOWN_T
        return {
            "len": MethodSig((), TypeRef("int")),
            "isEmpty": MethodSig((), TypeRef("bool")),
            "contains": MethodSig((key,), TypeRef("bool")),
            "iterator": MethodSig((), TypeRef("list", (key,))),
        }.get(name)
    if base.name == "stack":
        return {
            "len": MethodSig((), TypeRef("int")),
            "isEmpty": MethodSig((), TypeRef("bool")),
            "contains": MethodSig((t,), TypeRef("bool")),
            "iterator": MethodSig((), TypeRef("list", (t,))),
            "push": MethodSig((t,), VOID_T),
            "pop": MethodSig((), t),
            "peek": MethodSig((), t),
        }.get(name)
    return None


def core_trait_decls() -> dict[str, TraitDecl]:
    p = SourcePos("<solvik-core>", 1, 1)
    t = TypeRef("T")
    def method(name: str, params: tuple[TypeRef, ...], result: TypeRef, mutating: bool = False) -> FunctionDecl:
        return FunctionDecl(name, [Param(f"p{i}", x) for i, x in enumerate(params)], result, None, p, True, mutating)
    return {
        "Stringable": TraitDecl("Stringable", [method("string", (), TypeRef("string"))], p),
        "Equatable": TraitDecl("Equatable", [method("equals", (ANY_T,), TypeRef("bool"))], p),
        "Comparable": TraitDecl("Comparable", [method("compare", (ANY_T,), TypeRef("int"))], p),
        "Hashable": TraitDecl("Hashable", [method("hash", (), TypeRef("int"))], p),
        "Countable": TraitDecl("Countable", [method("len", (), TypeRef("int"))], p),
        "Iterable": TraitDecl("Iterable", [method("iterator", (), TypeRef("list", (t,)))], p, (TypeParam("T"),)),
        "Collection": TraitDecl("Collection", [
            method("len", (), TypeRef("int")),
            method("isEmpty", (), TypeRef("bool")),
            method("contains", (t,), TypeRef("bool")),
            method("iterator", (), TypeRef("list", (t,))),
        ], p, (TypeParam("T"),)),
    }


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
        # Canonical dotted identity keys: same-package types are qualified with
        # the current package; other packages keep their own prefix. Program
        # declarations are already canonicalized before validation.
        self.structs = {
            f"{package}.{name}": decl
            for (package, name), decl in interpreter.structs.items()
        }
        self.structs.update({f"{self.package}.{d.name}": d for d in program.declarations if isinstance(d, StructDecl)})
        self.traits = core_trait_decls()
        self.traits.update({
            f"{package}.{name}": decl
            for (package, name), decl in interpreter.traits.items()
        })
        self.traits.update({f"{self.package}.{d.name}": d for d in program.declarations if isinstance(d, TraitDecl)})
        self.enums: dict[str, EnumDecl] = {
            f"{package}.{name}": et.decl
            for (package, name), et in interpreter.enums.items()
        }
        self.enums.update({f"{self.package}.{d.name}": d for d in program.declarations if isinstance(d, EnumDecl)})
        self.functions: dict[str, FunctionDecl] = {}
        self.functions.update({
            f"{pkg}.{fname}": value.decl
            for pkg, ns in interpreter.packages.items()
            for fname, value in ns.values.items()
            if isinstance(value, UserFunction)
        })
        ns = interpreter.packages.get(self.package)
        if ns:
            self.functions.update({
                name: value.decl
                for name, value in ns.values.items()
                if isinstance(value, UserFunction)
            })
        self.current_function: Optional[FunctionDecl] = None
        self.loop_depth = 0
        self.current_struct: Optional[StructDecl] = None
        self.current_type_params: dict[str, TypeParam] = {}
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
        seen_types: dict[str, Any] = {}
        for decl in self.program.declarations:
            if isinstance(decl, FunctionDecl):
                if decl.name in seen_functions:
                    self.error("C090", decl.pos, f"duplicate function '{decl.name}'", self.to_line_end(decl.pos))
                seen_functions.add(decl.name)
                self.functions[decl.name] = decl
                if decl.name in seen_types:
                    self.duplicate_name_error(decl.pos, decl.name, seen_types[decl.name], "function")
                else:
                    seen_types[decl.name] = decl
            elif isinstance(decl, StructDecl):
                if decl.name in seen_types:
                    self.duplicate_name_error(decl.pos, decl.name, seen_types[decl.name], "struct")
                else:
                    seen_types[decl.name] = decl
                seen_fields: set[str] = set()
                for field_decl in decl.fields:
                    if field_decl.name in seen_fields:
                        self.error(
                            "C091",
                            field_decl.pos or decl.pos,
                            f"duplicate field '{field_decl.name}' in struct '{decl.name}'",
                        )
                    seen_fields.add(field_decl.name)
            elif isinstance(decl, (TraitDecl, EnumDecl)):
                if decl.name in seen_types:
                    self.duplicate_name_error(decl.pos, decl.name, seen_types[decl.name], type(decl).__name__.replace("Decl", "").lower())
                else:
                    seen_types[decl.name] = decl

        for decl in self.program.declarations:
            if isinstance(decl, StructDecl):
                self.check_struct_recursion(decl)

        for decl in self.program.declarations:
            if isinstance(decl, FunctionDecl):
                self.validate_function(decl, None)
            elif isinstance(decl, StructDecl):
                self.check_constraints(decl.type_params, decl.pos)
                for method in decl.methods:
                    self.validate_function(method, decl)
                old_type_params = self.current_type_params
                self.current_type_params = {p.name: p for p in decl.type_params}
                try:
                    for field in decl.fields:
                        self.check_annotation_type(field.type, field.pos or decl.pos)
                finally:
                    self.current_type_params = old_type_params
            elif isinstance(decl, TraitDecl):
                self.check_constraints(decl.type_params, decl.pos)
                for method in decl.methods:
                    self.validate_parameters(method)
            elif isinstance(decl, EnumDecl):
                self.validate_enum_decl(decl)

    @staticmethod
    def kind_of(decl: Any) -> str:
        if isinstance(decl, FunctionDecl): return "function"
        if isinstance(decl, StructDecl): return "struct"
        if isinstance(decl, TraitDecl): return "trait"
        if isinstance(decl, EnumDecl): return "enum"
        return "declaration"

    def duplicate_name_error(self, pos: SourcePos, name: str, other: Any, kind: str) -> None:
        self.error(
            "C109",
            pos,
            f"'{name}' is already declared as a {self.kind_of(other)}; top-level names must be unique within a package ({kind})",
            self.to_line_end(pos),
        )

    def check_type_visibility(self, decl: Any, name: str, pos: Optional[SourcePos], kind: str) -> None:
        """Cross-package use of a type requires the type to be `pub` (C120)."""
        pkg, _ = split_type_name(self.canonical(name))
        if pkg and pkg != self.package and not decl.public:
            self.error(
                "C120",
                pos,
                f"{kind} '{self.canonical(name)}' is private; declare it 'pub' to use it outside package '{pkg}'",
                self.to_line_end(pos) if pos else 1,
            )

    def check_constraints(self, type_params: tuple[TypeParam, ...], pos: SourcePos) -> None:
        """Constraint references must name a trait with matching arity."""
        for type_param in type_params:
            for constraint in type_param.constraints:
                trait = self.trait_of(constraint.name)
                if trait is None:
                    self.error("C110", pos, f"unknown type '{constraint.name}' in constraint", self.to_line_end(pos))
                    continue
                self.check_type_visibility(trait, constraint.name, pos, "trait")
                if len(constraint.args) != len(trait.type_params):
                    kind = "generic trait" if trait.type_params else "non-generic trait"
                    self.error(
                        "C096",
                        pos,
                        f"constraint '{constraint.name}' is a {kind}; it requires exactly {len(trait.type_params)} type argument(s)",
                        self.to_line_end(pos),
                    )

    def validate_enum_decl(self, decl: EnumDecl) -> None:
        """Enum declarations: unique cases, coherent payload/integer rules,
        annotated payload types, and type-parameter constraints."""
        self.check_constraints(decl.type_params, decl.pos)
        seen: set[str] = set()
        has_payload = any(m.payload_types for m in decl.members)
        old_type_params = self.current_type_params
        self.current_type_params = {p.name: p for p in decl.type_params}
        try:
            for member in decl.members:
                if member.name in seen:
                    self.error("C091", decl.pos, f"duplicate case '{member.name}' in enum '{decl.name}'", self.to_line_end(decl.pos))
                seen.add(member.name)
                if has_payload and member.value is not None:
                    self.error(
                        "C107",
                        decl.pos,
                        f"enum '{decl.name}' has payload cases, so case '{member.name}' cannot declare an integer value; algebraic enums use case names only",
                        self.to_line_end(decl.pos),
                    )
                for ptype in member.payload_types:
                    self.check_annotation_type(ptype, decl.pos)
        finally:
            self.current_type_params = old_type_params

    def check_struct_recursion(self, decl: StructDecl) -> None:
        """A struct value must have finite size.

        A field chain may only recurse through a nullable type or a collection
        (`list`/`map`/`stack`), which are indirect. A direct non-nullable cycle
        such as `struct A { x: A }` is rejected (C097).
        """
        def visit(struct: StructDecl, path: list[str]) -> None:
            for field in struct.fields:
                typ = field.type
                if typ.nullable:
                    continue
                target = self.struct_of(typ.name)
                if target is None or target.name in path:
                    if target is not None and target.name in path:
                        chain = " -> ".join(path + [target.name])
                        self.error(
                            "C097",
                            field.pos or struct.pos,
                            f"recursive struct field '{field.name}' of type '{typ.name}' must be nullable or indirect (cycle: {chain})",
                        )
                    continue
                visit(target, path + [target.name])
        visit(decl, [decl.name])

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
        if function.name == "main" and owner is None:
            if function.params:
                self.error(
                    "C123",
                    function.pos,
                    "entry function 'main' must take no parameters",
                    self.to_line_end(function.pos),
                )
            if function.return_type.name not in ("int", "void"):
                self.error(
                    "C124",
                    function.pos,
                    f"entry function 'main' must return int or nothing, not {function.return_type}",
                    self.to_line_end(function.pos),
                )
        if owner is not None:
            for type_param in function.type_params:
                if type_param.name in {p.name for p in owner.type_params}:
                    self.error(
                        "C099",
                        function.pos,
                        f"type parameter '{type_param.name}' of method '{function.name}' shadows a type parameter of struct '{owner.name}'; use a distinct name",
                        self.to_line_end(function.pos),
                    )
        self.check_constraints(function.type_params, function.pos)
        old_function, old_struct, old_type_params, old_scopes = self.current_function, self.current_struct, self.current_type_params, self.scopes
        self.current_function, self.current_struct = function, owner
        declared = list(owner.type_params if owner else ()) + list(function.type_params)
        self.current_type_params = {p.name: p for p in declared}
        for param in function.params:
            self.check_annotation_type(param.type, param.pos or function.pos)
        if function.return_type.name != "void":
            self.check_annotation_type(function.return_type, function.pos)
        if function.body is None:
            self.current_function, self.current_struct, self.current_type_params, self.scopes = old_function, old_struct, old_type_params, old_scopes
            return
        self.scopes = [{
            p.name: StaticBinding(TypeRef("list", (p.type,)) if p.variadic else p.type, False)
            for p in function.params
        }]
        self.check_block(function.body, new_scope=False)
        if function.return_type.name != "void" and not self.block_definitely_returns(function.body):
            self.error(
                "C111",
                function.pos,
                f"function '{function.name}' declares return type {function.return_type} but not every path returns a value",
                self.to_line_end(function.pos),
            )
        self.current_function, self.current_struct, self.current_type_params, self.scopes = old_function, old_struct, old_type_params, old_scopes

    def lookup(self, name: str) -> Optional[StaticBinding]:
        for scope in reversed(self.scopes):
            if name in scope:
                return scope[name]
        return None

    def receiver_field(self, name: str) -> Optional[FieldDecl]:
        if not self.current_struct:
            return None
        return next((field_decl for field_decl in self.current_struct.fields if field_decl.name == name), None)

    def block_definitely_returns(self, block: Block) -> bool:
        """Whether every path through the block ends in a return or throw.

        `break`/`continue` exit paths do not return from the function, so they
        never count here (they count for unreachable-code detection only).
        Try/finally is deliberately conservative: a return inside `try` is not
        trusted to be the only path.
        """
        for stmt in block.statements:
            if isinstance(stmt, (ReturnStmt, ThrowStmt)):
                return True
            if isinstance(stmt, Block) and self.block_definitely_returns(stmt):
                return True
            if isinstance(stmt, TryStmt) and self.block_definitely_returns(stmt.try_block):
                # A try block that returns on every path returns on every path
                # regardless of finally content (the finally may only add its
                # own return or exception).
                return True
            if isinstance(stmt, IfStmt):
                if not self.block_definitely_returns(stmt.then_block):
                    continue
                if isinstance(stmt.else_branch, Block):
                    if self.block_definitely_returns(stmt.else_branch):
                        return True
                elif isinstance(stmt.else_branch, IfStmt):
                    if self.block_definitely_returns(Block([stmt.else_branch], stmt.else_branch.pos)):
                        return True
            if isinstance(stmt, SwitchStmt):
                if not stmt.cases or not all(self.block_definitely_returns(c.body) for c in stmt.cases):
                    continue
                if any(c.expr is None for c in stmt.cases):
                    return True
                # An exhaustive switch over a closed enum also returns on all
                # paths even without an explicit default.
                switch_type = self.infer(stmt.value)
                enum = self.enum_of(switch_type.name)
                if enum is not None and self.switch_covers_all_cases(stmt, enum, switch_type.name):
                    return True
            if isinstance(stmt, WhileStmt):
                if isinstance(stmt.condition, Literal) and stmt.condition.literal_kind == "bool" and stmt.condition.value is True \
                        and self.block_definitely_returns(stmt.body):
                    return True
        return False

    def switch_covers_all_cases(self, stmt: SwitchStmt, enum: EnumDecl, enum_canon: str) -> bool:
        """Whether the switch's cases cover every member of a closed enum."""
        covered: set[str] = set()
        for case in stmt.cases:
            if case.expr is None:
                continue
            shape = enum_pattern_shape(case.expr)
            if shape is not None:
                kind, enum_expr, case_name, _ = shape
                if kind == "qualified":
                    pat_enum = self.enum_of(dotted_expression_name(enum_expr) or "")
                    if pat_enum is not None and pat_enum.name == enum.name and any(m.name == case_name for m in enum.members):
                        covered.add(case_name)
                elif any(m.name == case_name for m in enum.members):
                    covered.add(case_name)
            elif isinstance(case.expr, Member) and self.canonical(dotted_expression_name(case.expr.obj) or "") == enum_canon:
                member = next((m for m in enum.members if m.name == case.expr.name), None)
                if member is not None and not member.payload_types:
                    covered.add(case.expr.name)
        return all(m.name in covered for m in enum.members)

    def statement_terminates(self, stmt: Any) -> bool:
        """Whether execution cannot continue past this statement in the same
        block. Only the straightforward, directly visible cases count:
        return/throw/break/continue statements. Compound constructs whose
        branches all terminate are excluded so cross-implementation demo code
        (and belt-and-suspenders trailing returns) stay accepted."""
        return isinstance(stmt, (ReturnStmt, ThrowStmt, BreakStmt, ContinueStmt))

    def check_block(self, block: Block, new_scope: bool = True) -> None:
        if new_scope:
            self.scopes.append({})
        try:
            terminated = False
            for statement in block.statements:
                if terminated:
                    self.error(
                        "C112",
                        statement.pos,
                        "unreachable statement: execution cannot continue past an earlier return, throw, break, or continue",
                        self.to_line_end(statement.pos),
                    )
                self.check_statement(statement)
                if not terminated and self.statement_terminates(statement):
                    terminated = True
        finally:
            if new_scope:
                self.scopes.pop()

    def check_statement(self, statement: Any) -> None:
        if isinstance(statement, Block):
            self.check_block(statement)
        elif isinstance(statement, VarDecl):
            self.check_annotation_type(statement.type, statement.pos)
            self.check_value_for_type(statement.value, statement.type)
            self.scopes[-1][statement.name] = StaticBinding(statement.type, statement.mutable)
        elif isinstance(statement, ExprStmt):
            self.infer(statement.expr)
        elif isinstance(statement, IfStmt):
            then_n, else_n = self.null_narrowing(statement.condition)
            self.infer(statement.condition)
            self.check_narrowed_block(statement.then_block, then_n)
            if isinstance(statement.else_branch, Block):
                self.check_narrowed_block(statement.else_branch, else_n)
            elif isinstance(statement.else_branch, IfStmt):
                if else_n:
                    self.scopes.append({k: StaticBinding(t, False) for k, t in else_n.items()})
                    try:
                        self.check_statement(statement.else_branch)
                    finally:
                        self.scopes.pop()
                else:
                    self.check_statement(statement.else_branch)
        elif isinstance(statement, WhileStmt):
            self.loop_depth += 1
            try:
                self.infer(statement.condition)
                self.check_block(statement.body)
            finally:
                self.loop_depth -= 1
        elif isinstance(statement, ForStmt):
            iterable_type = self.infer(statement.iterable)
            bindings: dict[str, StaticBinding] = {}
            if len(statement.names) == 2 and iterable_type.name == "map" and len(iterable_type.args) == 2:
                bindings[statement.names[0]] = StaticBinding(iterable_type.args[0], False)
                bindings[statement.names[1]] = StaticBinding(iterable_type.args[1], False)
            else:
                element_type = self.iterable_element_type(iterable_type)
                bindings[statement.names[0]] = StaticBinding(element_type, False)
            self.loop_depth += 1
            self.scopes.append(bindings)
            try:
                self.check_block(statement.body, new_scope=False)
            finally:
                self.scopes.pop()
                self.loop_depth -= 1
        elif isinstance(statement, SwitchStmt):
            switch_type = self.infer(statement.value)
            enum_decl = self.enum_of(switch_type.name)
            enum_bindings = {p.name: a for p, a in zip(enum_decl.type_params, switch_type.args)} if enum_decl else {}
            covered: set[str] = set()
            has_default = False
            has_null_case = False
            for case in statement.cases:
                if case.expr is None:
                    has_default = True
                    self.check_block(case.body)
                    continue
                shape = enum_pattern_shape(case.expr)
                pat: Optional[tuple[EnumDecl, str, list[Any], tuple[TypeRef, ...]]] = None
                if shape is not None:
                    kind, enum_expr, case_name, elements = shape
                    if kind == "qualified":
                        pat_enum = self.enum_of(dotted_expression_name(enum_expr) or "")
                        if pat_enum is not None and any(m.name == case_name for m in pat_enum.members):
                            pat = (pat_enum, case_name, elements, getattr(enum_expr, "type_args", ()))
                    elif enum_decl is not None and any(m.name == case_name for m in enum_decl.members):
                        pat = (enum_decl, case_name, elements, ())
                if pat is not None:
                    pat_enum, case_name, elements, pat_args = pat
                    compatible = (
                        enum_decl is not None
                        and pat_enum.name == enum_decl.name
                        and (not pat_args or not switch_type.args or pat_args == switch_type.args)
                    )
                    if not compatible:
                        self.error(
                            "C094",
                            getattr(case.expr, "pos", statement.pos),
                            f"case pattern of enum '{pat_enum.name}' can never match switch of type {switch_type}",
                            self.literal_span(case.expr),
                        )
                    if case_name in covered:
                        self.error("C106", getattr(case.expr, "pos", statement.pos), f"duplicate case pattern '{case_name}' already matched by an earlier case", self.literal_span(case.expr))
                    covered.add(case_name)
                    bindings = self.validate_enum_pattern(pat_enum, case_name, elements, enum_bindings, case.expr)
                    self.scopes.append({n: StaticBinding(t, False) for n, t in bindings.items()})
                    try:
                        self.check_block(case.body, new_scope=False)
                    finally:
                        self.scopes.pop()
                    continue
                case_type = self.infer(case.expr)
                if case_type.name == "null":
                    has_null_case = True
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
                if enum_decl is not None and isinstance(case.expr, Member) and self.canonical(dotted_expression_name(case.expr.obj) or "") == self.canonical(switch_type.name):
                    member = next((m for m in enum_decl.members if m.name == case.expr.name), None)
                    if member is not None and member.payload_types:
                        self.error(
                            "C107",
                            getattr(case.expr, "pos", statement.pos),
                            f"payload case '{case.expr.name}' requires pattern arguments in a switch, e.g. case {enum_decl.name}.{case.expr.name}(value)",
                            self.literal_span(case.expr),
                        )
                    elif member is not None:
                        if case.expr.name in covered:
                            self.error("C106", getattr(case.expr, "pos", statement.pos), f"duplicate case '{case.expr.name}' already matched by an earlier case", self.literal_span(case.expr))
                        covered.add(case.expr.name)
                self.check_block(case.body)
            if enum_decl is not None and not has_default:
                missing = [m.name for m in enum_decl.members if m.name not in covered]
                if switch_type.nullable and not has_null_case:
                    missing.append("null")
                if missing:
                    self.error(
                        "C105",
                        statement.pos,
                        f"non-exhaustive switch over enum '{enum_decl.name}'; missing case(s): " + ", ".join(missing),
                        self.to_line_end(statement.pos),
                    )
        elif isinstance(statement, TryStmt):
            self.check_block(statement.try_block)
            if statement.catch_block is not None:
                self.scopes.append({statement.catch_name or "e": StaticBinding(statement.catch_type or EXCEPTION_T, False)})
                try: self.check_block(statement.catch_block, new_scope=False)
                finally: self.scopes.pop()
            if statement.finally_block is not None: self.check_block(statement.finally_block)
        elif isinstance(statement, ThrowStmt): self.infer(statement.value)
        elif isinstance(statement, BreakStmt) or isinstance(statement, ContinueStmt):
            if self.loop_depth == 0:
                kind = "break" if isinstance(statement, BreakStmt) else "continue"
                self.error(
                    "C113",
                    statement.pos,
                    f"{kind} outside of a loop (while/for)",
                    self.to_line_end(statement.pos),
                )
        elif isinstance(statement, ReturnStmt):
            self.check_return_statement(statement)

    def check_return_statement(self, statement: ReturnStmt) -> None:
        function = self.current_function
        declared = function.return_type if function is not None else VOID_T
        if statement.value is None:
            if declared.name != "void":
                self.error(
                    "C115",
                    statement.pos,
                    f"return without a value in a function returning {declared}",
                    self.to_line_end(statement.pos),
                )
            return
        if declared.name == "void":
            self.error(
                "C115",
                statement.pos,
                "return with a value in a function that returns nothing",
                self.to_line_end(statement.pos),
            )
            return
        self.check_struct_value(statement.value, declared)
        actual = self.infer(statement.value)
        if actual != UNKNOWN_T and not self.assignable(actual, declared):
            self.error(
                "C114",
                statement.pos,
                f"return value of type {actual} is not assignable to declared return type {declared}",
                self.to_line_end(statement.pos),
            )

    def null_narrowing(self, condition: Any) -> tuple[dict[str, TypeRef], dict[str, TypeRef]]:
        """Narrowings from a null-comparison condition.

        Returns (then-branch, else-branch) maps of name -> non-nullable type
        for `if x != null` / `if x == null` conditions.
        """
        if isinstance(condition, Binary) and condition.op in ("==", "!="):
            null_side = None
            other: Optional[Any] = None
            for side in (condition.left, condition.right):
                if isinstance(side, Literal) and side.literal_kind == "null":
                    null_side = side
                else:
                    other = side
            if null_side is not None and isinstance(other, Name):
                binding = self.lookup(other.name)
                if binding is not None and binding.type.nullable:
                    nonnull = binding.type.nonnull()
                    if condition.op == "!=":
                        return ({other.name: nonnull}, {})
                    return ({}, {other.name: nonnull})
        return ({}, {})

    def check_narrowed_block(self, block: Block, narrowings: dict[str, TypeRef]) -> None:
        if not narrowings:
            self.check_block(block)
            return
        base = dict(self.scopes[-1])
        for name, typ in narrowings.items():
            current = self.lookup(name)
            base[name] = StaticBinding(typ, bool(current and current.mutable))
        self.scopes.append(base)
        try:
            self.check_block(block, new_scope=False)
        finally:
            self.scopes.pop()

    def check_enum_value(self, expression: Any, expected: Optional[TypeRef]) -> None:
        """Check an enum construction against an expected type, seeding the
        instantiation from the annotation (mirrors struct-literal seeding)."""
        if (isinstance(expression, Call) and not expression.type_args
                and isinstance(expression.callee, Member) and isinstance(expression.callee.obj, Name)
                and expected is not None and expected.name.rsplit(".", 1)[-1] == expression.callee.obj.name and expected.args):
            enum = self.enum_of(dotted_expression_name(expression.callee.obj) or "")
            if enum is not None and len(expected.args) == len(enum.type_params):
                self.infer_enum_construction(enum, TypeRef(enum.name, expected.args), expression)
                return
        self.infer(expression)

    def check_struct_value(self, expression: Any, expected: Optional[TypeRef]) -> None:
        """Check a value against an expected type, seeding struct literals.

        When the expected type names a concrete generic instantiation, the
        struct literal's type parameters are seeded from it, mirroring the
        runtime's expected-type seeding.
        """
        if (isinstance(expression, StructExpr) and expected is not None
                and expected.name == expression.type_name and expected.args
                and not expression.type_args):
            struct = self.struct_of(expression.type_name)
            if struct is not None and len(expected.args) == len(struct.type_params):
                seeds = {p.name: a for p, a in zip(struct.type_params, expected.args)}
                self.infer_struct_expr(expression, seeds)
                return
        self.check_enum_value(expression, expected)

    def check_enum_value(self, expression: Any, expected: Optional[TypeRef]) -> None:
        """Check an enum construction against an expected type, seeding the
        instantiation from the annotation (mirrors struct-literal seeding)."""
        if (isinstance(expression, Call) and not expression.type_args
                and isinstance(expression.callee, Member) and isinstance(expression.callee.obj, Name)
                and expected is not None and expected.name.rsplit(".", 1)[-1] == expression.callee.obj.name and expected.args):
            enum = self.enum_of(dotted_expression_name(expression.callee.obj) or "")
            if enum is not None and len(expected.args) == len(enum.type_params):
                self.infer_enum_construction(enum, TypeRef(enum.name, expected.args), expression)
                return
        self.infer(expression)

    def check_struct_value(self, expression: Any, expected: Optional[TypeRef]) -> None:
        """Check a value against an expected type, seeding struct literals.

        When the expected type names a concrete generic instantiation, the
        struct literal's type parameters are seeded from it, mirroring the
        runtime's expected-type seeding.
        """
        if (isinstance(expression, StructExpr) and expected is not None
                and expected.name == expression.type_name and expected.args
                and not expression.type_args):
            struct = self.struct_of(expression.type_name)
            if struct is not None and len(expected.args) == len(struct.type_params):
                seeds = {p.name: a for p, a in zip(struct.type_params, expected.args)}
                self.infer_struct_expr(expression, seeds)
                return
        self.check_enum_value(expression, expected)

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
        if isinstance(expression, StructExpr) and expected.name == expression.type_name and expected.args:
            self.check_struct_value(expression, expected)
            return
        if expected.name == "func":
            self.check_func_assignment(expected, expression)
        self.check_struct_value(expression, expected)
        actual = self.infer(expression)
        # Downcasting from `any` to a concrete type is a documented runtime-
        # checked operation (E066 on mismatch), so it is not a static error.
        if actual != UNKNOWN_T and actual.name != "any" and expected.name != "any" and expected.name != UNKNOWN_T.name \
                and not (expected.name == "exception" and actual.name == "string") and not self.assignable(actual, expected):
            self.error(
                "C118",
                getattr(expression, "pos", None),
                f"declared type {expected} but initializer has type {actual}",
                self.literal_span(expression),
            )

    def canonical(self, name: str) -> str:
        """Canonical dotted identity of a type name in the current package."""
        if not name or "." in name:
            return name
        return f"{self.package}.{name}"

    def struct_of(self, name: str) -> Optional[StructDecl]:
        return self.structs.get(self.canonical(name))

    def enum_of(self, name: str) -> Optional[EnumDecl]:
        return self.enums.get(self.canonical(name))

    def trait_of(self, name: str) -> Optional[TraitDecl]:
        return self.traits.get(self.canonical(name)) or self.traits.get(name)

    def same_type_name(self, a: str, b: str) -> bool:
        return self.canonical(a) == self.canonical(b)

    def method_sig_for_type(self, typ: TypeRef, name: str) -> Optional[MethodSig]:
        built = builtin_method_signature(typ, name)
        if built is not None:
            return built
        struct = self.struct_of(typ.name)
        if struct is None:
            return None
        bindings = {p.name: a for p, a in zip(struct.type_params, typ.args)}
        method = next((m for m in struct.methods if m.public and m.name == name), None)
        if method is None:
            return None
        return MethodSig(
            tuple(substitute_type(p.type, bindings) for p in method.params),
            substitute_type(method.return_type, bindings),
            method.mutating,
        )

    def type_satisfies_trait(self, actual: TypeRef, expected: TypeRef, variables: frozenset[str] = frozenset()) -> bool:
        return self.trait_satisfaction(actual, expected, variables)[0]

    def trait_satisfaction(self, actual: TypeRef, expected: TypeRef, variables: frozenset[str] = frozenset()) -> tuple[bool, dict[str, TypeRef]]:
        trait = self.trait_of(expected.name)
        if trait is None:
            return False, {}
        return trait_satisfaction(actual, expected, trait, self.method_sig_for_type, variables)

    def check_annotation_type(self, typ: TypeRef, pos: Optional[SourcePos]) -> None:
        """Generic annotations must be exactly instantiated.

        `list<int, string>`, `map<int>`, a bare `Box`, or `Point<int>` are
        arity errors (C096). Unknown names are left to other passes.
        """
        for arg in typ.args:
            if arg.name != "void":
                # `void` is only valid as the return element of a function
                # type (handled by the func branch below); nested void in any
                # other position is an error.
                self.check_annotation_type(arg, pos)
        if typ.name == "func":
            # func<P1, ..., Pn, R>: at least the return type; `void` is only
            # legal as the final (return) element.
            if not typ.args:
                self.error("C104", pos, "function types require at least a return type", self.to_line_end(pos) if pos else 1)
            for arg in typ.args[:-1]:
                if arg.name == "void":
                    self.error("C104", pos, "void is only allowed as the return element of a function type", self.to_line_end(pos) if pos else 1)
            return
        if typ.name == "void":
            self.error(
                "C122",
                pos,
                "void is not a value type; it may appear only as the return element of a function type",
                self.to_line_end(pos) if pos else 1,
            )
        if typ.name in self.current_type_params or typ.name in ("any", "exception", "regex"):
            return
        expected_arity: Optional[int] = None
        if typ.name in ("list", "stack"):
            expected_arity = 1
        elif typ.name == "map":
            expected_arity = 2
        else:
            struct = self.struct_of(typ.name)
            if struct is not None:
                self.check_type_visibility(struct, typ.name, pos, "struct")
                expected_arity = len(struct.type_params)
            else:
                enum = self.enum_of(typ.name)
                if enum is not None:
                    self.check_type_visibility(enum, typ.name, pos, "enum")
                    expected_arity = len(enum.type_params)
        if expected_arity is None:
            # Not a generic collection, struct, or enum: either a built-in
            # scalar, a known trait (valid in annotation position), or an
            # unknown type name.
            known = typ.name in ("bool", "byte", "int", "float", "char", "string") or typ.name == UNKNOWN_T.name
            if not known:
                trait = self.trait_of(typ.name)
                if trait is not None:
                    self.check_type_visibility(trait, typ.name, pos, "trait")
                    known = True
            if not known:
                self.error(
                    "C110",
                    pos,
                    f"unknown type '{typ.name}'",
                    self.to_line_end(pos) if pos else 1,
                )
            return
        if len(typ.args) != expected_arity:
            kind = "generic type" if typ.name in ("list", "stack", "map") else ("enum" if self.enum_of(typ.name) is not None else "struct")
            found = "none" if not typ.args else str(len(typ.args))
            self.error(
                "C096",
                pos,
                f"{kind} '{typ.name}' requires {expected_arity} type argument(s), found {found}",
                self.to_line_end(pos) if pos else 1,
            )

    def assignable(self, actual: TypeRef, expected: TypeRef) -> bool:
        if actual == UNKNOWN_T or expected == UNKNOWN_T or expected.name == "any": return True
        if actual.name == "null": return expected.nullable
        if expected.name in self.current_type_params:
            return True
        if actual.name in self.current_type_params:
            return actual.name == expected.name or expected.name == "any"
        if actual.nullable and not expected.nullable:
            # A nullable value cannot flow into a non-nullable target without
            # narrowing or coalescing.
            return False
        if expected.nullable: expected = expected.nonnull()
        if actual.nullable: actual = actual.nonnull()
        if actual.name == expected.name == "func":
            # Function types are invariant: widening inside a signature would
            # let callers pass arguments the callee cannot accept. UNKNOWN
            # entries (higher-order collection methods) defer to runtime.
            return same_func_type(actual, expected)
        if self.same_type_name(actual.name, expected.name) and (self.struct_of(actual.name) is not None or self.enum_of(actual.name) is not None):
            # Struct and enum instantiations are invariant (matching runtime
            # value checks). Unknown arguments defer to runtime enforcement.
            if not actual.args or not expected.args:
                return True
            if len(actual.args) != len(expected.args):
                return False
            return all(
                a == e or a == UNKNOWN_T or e == UNKNOWN_T
                for a, e in zip(actual.args, expected.args)
            )
        if self.same_type_name(actual.name, expected.name):
            if bool(actual.args) != bool(expected.args):
                return not actual.args or not expected.args
            return len(actual.args) == len(expected.args) and all(
                self.assignable(a, e) for a, e in zip(actual.args, expected.args)
            )
        if self.type_satisfies_trait(actual, expected):
            return True
        return (actual.name, expected.name) in {("byte", "int"), ("byte", "float"), ("int", "float")}

    @staticmethod
    def switch_types_overlap(switch_type: TypeRef, case_type: TypeRef) -> bool:
        if switch_type == UNKNOWN_T or case_type == UNKNOWN_T or switch_type.name == "any" or case_type.name == "any": return True
        if case_type.name == "regex": return switch_type.name == "string"
        if case_type.name == "null": return switch_type.nullable
        if switch_type.name == case_type.name: return True
        return {switch_type.name, case_type.name} <= {"byte", "int", "float"}

    def function_signature(self, function: FunctionDecl) -> TypeRef:
        """Function type of a named function; UNKNOWN when not fixed (generic/variadic)."""
        if function.type_params or (function.params and function.params[-1].variadic):
            return UNKNOWN_T
        return TypeRef("func", tuple(p.type for p in function.params) + (function.return_type,))

    def check_func_value_call(self, sig: TypeRef, expression: Call, description: str) -> TypeRef:
        """Statically check a call through a function-typed value (C101)."""
        params, result = sig.args[:-1], sig.args[-1]
        if any(argument.spread for argument in expression.args):
            return result
        if len(expression.args) != len(params):
            self.error(
                "C101",
                expression.pos,
                f"{description} expects {len(params)} argument(s), found {len(expression.args)}",
                self.to_line_end(expression.pos),
            )
        for i, (argument, param_type) in enumerate(zip(expression.args, params)):
            actual = self.infer(argument.expr)
            if actual != UNKNOWN_T and not self.assignable(actual, param_type):
                self.error(
                    "C101",
                    getattr(argument.expr, "pos", expression.pos),
                    f"argument {i + 1} of {description}: expected {param_type} but got {actual}",
                    self.literal_span(argument.expr),
                )
        return result

    def check_func_assignment(self, expected: TypeRef, expression: Any) -> None:
        """Reject incompatible function signatures (C100)."""
        if expected.name != "func":
            return
        actual = self.infer(expression)
        if actual.name == "func" and actual.nonnull() != expected.nonnull():
            self.error(
                "C100",
                getattr(expression, "pos", None),
                f"function signature mismatch: expected {expected} but got {actual}",
                self.literal_span(expression),
            )

    def infer_func_expr(self, expression: FuncExpr) -> TypeRef:
        """Type an anonymous function and check its body like a function body."""
        seen: set[str] = set()
        for param in expression.params:
            if param.name in seen:
                self.error("C092", param.pos or expression.pos, f"duplicate parameter '{param.name}' in anonymous function")
            seen.add(param.name)
            self.check_annotation_type(param.type, param.pos or expression.pos)
        if expression.return_type.name != "void":
            self.check_annotation_type(expression.return_type, expression.pos)
        closure_decl = FunctionDecl(
            "<closure>", expression.params, expression.return_type, expression.body, expression.pos,
            mutating=bool(self.current_function and self.current_function.mutating),
        )
        old_function = self.current_function
        self.current_function = closure_decl
        self.scopes.append({
            p.name: StaticBinding(TypeRef("list", (p.type,)) if p.variadic else p.type, False)
            for p in expression.params
        })
        try:
            self.check_block(expression.body, new_scope=False)
        finally:
            self.scopes.pop()
            self.current_function = old_function
        if expression.return_type.name != "void" and not self.block_definitely_returns(expression.body):
            self.error(
                "C111",
                expression.pos,
                f"closure declares return type {expression.return_type} but not every path returns a value",
                self.to_line_end(expression.pos),
            )
        return TypeRef("func", tuple(p.type for p in expression.params) + (expression.return_type,))

    def infer(self, expression: Any) -> TypeRef:
        if isinstance(expression, Literal):
            return NULL_T if expression.literal_kind == "null" else TypeRef(expression.literal_kind)
        if isinstance(expression, Name):
            binding = self.lookup(expression.name)
            if binding: return binding.type
            field_decl = self.receiver_field(expression.name)
            if field_decl: return field_decl.type
            if expression.name == "self" and self.current_struct: return TypeRef(self.current_struct.name)
            function = self.functions.get(expression.name)
            if function is not None: return self.function_signature(function)
            if self.enum_of(expression.name) is not None: return TypeRef(self.canonical(expression.name), expression.type_args)
            return UNKNOWN_T
        if isinstance(expression, FuncExpr):
            return self.infer_func_expr(expression)
        if isinstance(expression, ListExpr):
            types = [self.infer(item) for item in expression.items]
            return TypeRef("list", (types[0] if types else UNKNOWN_T,))
        if isinstance(expression, MapExpr):
            key_types = [self.infer(item[0]) for item in expression.items]
            value_types = [self.infer(item[1]) for item in expression.items]
            return TypeRef("map", (key_types[0] if key_types else UNKNOWN_T, value_types[0] if value_types else UNKNOWN_T))
        if isinstance(expression, StructExpr):
            return self.infer_struct_expr(expression, {})
        if isinstance(expression, Unary):
            operand = self.infer(expression.expr)
            return TypeRef("bool") if expression.op == "!" else operand
        if isinstance(expression, Binary):
            left = self.infer(expression.left)
            right = self.infer(expression.right)
            if expression.op == "??":
                if left.name in ("void", "module", "regex"):
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
            self.check_assignment_target(expression.target, expression.pos)
            target_type = self.infer(expression.target)
            if target_type.name == "func":
                self.check_func_assignment(target_type, expression.value)
            self.check_struct_value(expression.value, target_type if target_type.args else None)
            value_type = self.infer(expression.value)
            if target_type != UNKNOWN_T and value_type != UNKNOWN_T and value_type.name != "any" and target_type.name != "any" \
                    and not (target_type.name == "exception" and value_type.name == "string") and not self.assignable(value_type, target_type):
                self.error(
                    "C119",
                    expression.pos,
                    f"cannot assign {value_type} to target of type {target_type}",
                    self.to_line_end(expression.pos),
                )
            return target_type if target_type != UNKNOWN_T else value_type
        if isinstance(expression, Index):
            obj_type = self.infer(expression.obj); self.infer(expression.index)
            if obj_type.name == "map" and len(obj_type.args) == 2: return obj_type.args[1]
            if obj_type.name in ("list", "stack") and obj_type.args: return obj_type.args[0]
            if obj_type.name == "string": return TypeRef("char")
            return UNKNOWN_T
        if isinstance(expression, Member):
            obj_type = self.infer(expression.obj)
            struct = self.struct_of(obj_type.name)
            if struct:
                self.check_type_visibility(struct, obj_type.name, expression.pos, "struct")
                cross_pkg = split_type_name(self.canonical(obj_type.name))[0] != self.package
                field_decl = next((field_decl for field_decl in struct.fields if field_decl.name == expression.name), None)
                if field_decl:
                    if cross_pkg and not field_decl.public:
                        self.error(
                            "C120",
                            expression.pos,
                            f"field '{expression.name}' of '{self.canonical(obj_type.name)}' is private; only pub fields are visible outside the package",
                            self.to_line_end(expression.pos),
                        )
                    bindings = {p.name: a for p, a in zip(struct.type_params, obj_type.args)}
                    return substitute_type(field_decl.type, bindings)
                method = next((m for m in struct.methods if m.name == expression.name), None)
                if method is not None and cross_pkg and not method.public:
                    self.error(
                        "C120",
                        expression.pos,
                        f"method '{expression.name}' of '{self.canonical(obj_type.name)}' is private; only pub methods are visible outside the package",
                        self.to_line_end(expression.pos),
                    )
                sig = self.method_sig_for_type(obj_type, expression.name)
                if sig is not None:
                    return TypeRef("func", sig.params + (sig.return_type,))
            enum = self.enum_of(obj_type.name)
            if enum is not None:
                self.check_type_visibility(enum, obj_type.name, expression.pos, "enum")
                enum_canon = self.canonical(dotted_expression_name(expression.obj) or obj_type.name)
                member = next((m for m in enum.members if m.name == expression.name), None)
                if member is not None:
                    if member.payload_types:
                        # A payload case reference is a constructor; its type is
                        # fixed only when the enum instantiation is explicit.
                        if obj_type.args:
                            enum_type = TypeRef(enum_canon, obj_type.args)
                            bindings = {p.name: a for p, a in zip(enum.type_params, obj_type.args)}
                            return TypeRef("func", tuple(substitute_type(p, bindings) for p in member.payload_types) + (enum_type,))
                        return UNKNOWN_T
                    return TypeRef(enum_canon, obj_type.args)
            builtin_sig = builtin_method_signature(obj_type, expression.name)
            if builtin_sig is not None:
                return TypeRef("func", builtin_sig.params + (builtin_sig.return_type,))
            return UNKNOWN_T
        if isinstance(expression, Call):
            argument_types = [self.infer(argument.expr) for argument in expression.args]
            if isinstance(expression.callee, Name):
                name = expression.callee.name
                binding = self.lookup(name)
                if binding is not None:
                    # A local function-typed variable shadows any package function.
                    if binding.type.name == "func":
                        return self.check_func_value_call(binding.type, expression, f"function value '{name}'")
                    if binding.type not in (UNKNOWN_T,) and binding.type.name not in ("any",) and not binding.type.nullable:
                        self.error(
                            "C102",
                            expression.pos,
                            f"cannot call '{name}': value of type {binding.type} is not callable",
                            self.to_line_end(expression.pos),
                        )
                    if binding.type.nullable and binding.type.name != "any":
                        return binding.type.nonnull().args[-1] if binding.type.nonnull().name == "func" else UNKNOWN_T
                    return UNKNOWN_T
                function = self.functions.get(name)
                if function:
                    if function.type_params or expression.type_args:
                        return self.infer_generic_call(function, argument_types, expression.type_args)
                    # Non-generic named functions: reject obviously incompatible
                    # arguments, including mismatched function values (C101).
                    # Fixed parameters first, then the variadic tail against the
                    # variadic element type.
                    variadic_param = function.params[-1] if function.params and function.params[-1].variadic else None
                    fixed_count = len(function.params) - (1 if variadic_param else 0)
                    for i, (argument, param) in enumerate(zip(expression.args, function.params)):
                        if argument.spread:
                            continue
                        actual = argument_types[i]
                        if actual != UNKNOWN_T and not self.assignable(actual, param.type):
                            self.error(
                                "C101",
                                getattr(argument.expr, "pos", expression.pos),
                                f"argument {i + 1} of '{name}': expected {param.type} but got {actual}",
                                self.literal_span(argument.expr),
                            )
                    if variadic_param is not None:
                        for i in range(fixed_count, len(expression.args)):
                            argument = expression.args[i]
                            if argument.spread:
                                continue
                            actual = argument_types[i]
                            if actual != UNKNOWN_T and not self.assignable(actual, variadic_param.type):
                                self.error(
                                    "C101",
                                    getattr(argument.expr, "pos", expression.pos),
                                    f"argument {i + 1} of '{name}': expected {variadic_param.type} but got {actual}",
                                    self.literal_span(argument.expr),
                                )
                    return function.return_type
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
                callee_type = self.infer(expression.callee)
                obj_type = self.infer(expression.callee.obj)
                qualified_function = self.functions.get(dotted_expression_name(expression.callee) or "")
                if qualified_function is not None and not qualified_function.type_params:
                    # Qualified cross-package function call: check arguments.
                    variadic_param = qualified_function.params[-1] if qualified_function.params and qualified_function.params[-1].variadic else None
                    fixed_count = len(qualified_function.params) - (1 if variadic_param else 0)
                    for i, (argument, param) in enumerate(zip(expression.args, qualified_function.params)):
                        if argument.spread:
                            continue
                        actual = self.infer(argument.expr)
                        if actual != UNKNOWN_T and not self.assignable(actual, param.type):
                            self.error(
                                "C101",
                                getattr(argument.expr, "pos", expression.pos),
                                f"argument {i + 1} of '{dotted_expression_name(expression.callee)}': expected {param.type} but got {actual}",
                                self.literal_span(argument.expr),
                            )
                    if variadic_param is not None:
                        for i in range(fixed_count, len(expression.args)):
                            argument = expression.args[i]
                            if argument.spread:
                                continue
                            actual = self.infer(argument.expr)
                            if actual != UNKNOWN_T and not self.assignable(actual, variadic_param.type):
                                self.error(
                                    "C101",
                                    getattr(argument.expr, "pos", expression.pos),
                                    f"argument {i + 1} of '{dotted_expression_name(expression.callee)}': expected {variadic_param.type} but got {actual}",
                                    self.literal_span(argument.expr),
                                )
                    return qualified_function.return_type
                struct = self.struct_of(obj_type.name)
                method = None
                if struct:
                    method = next((m for m in struct.methods if m.name == expression.callee.name), None)
                    if method and method.mutating and not self.receiver_is_mutable(expression.callee.obj):
                        receiver_name = expression.callee.obj.name if isinstance(expression.callee.obj, Name) else "receiver"
                        self.error(
                            "C068",
                            expression.pos,
                            f"cannot call mutating method '{method.name}' on immutable struct variable '{receiver_name}'; declare the variable as 'mut'",
                            self.to_line_end(expression.pos),
                        )
                    if method is not None and method.type_params:
                        # Generic methods instantiate per call; the return type
                        # is substituted from explicit type arguments when given
                        # (argument types are checked at call time otherwise).
                        if expression.type_args and len(expression.type_args) == len(method.type_params):
                            return substitute_type(method.return_type, {p.name: a for p, a in zip(method.type_params, expression.type_args)})
                        return method.return_type
                enum = self.enum_of(obj_type.name)
                if enum is not None:
                    return self.infer_enum_construction(enum, obj_type, expression)
                if callee_type.name == "func":
                    return self.check_func_value_call(callee_type, expression, f"'{expression.callee.name}'")
                if callee_type != UNKNOWN_T and callee_type.name not in ("any",) and struct is None and obj_type.name in ("bool", "byte", "int", "float", "char", "string", "list", "map", "stack"):
                    self.error(
                        "C102",
                        expression.pos,
                        f"cannot call '{expression.callee.name}' on value of type {obj_type}",
                        self.to_line_end(expression.pos),
                    )
                if callee_type.name == "func":
                    return callee_type.args[-1]
                return UNKNOWN_T
            callee_type = self.infer(expression.callee)
            if callee_type.name == "func":
                return self.check_func_value_call(callee_type, expression, "function value")
            return UNKNOWN_T
        return UNKNOWN_T

    def infer_struct_expr(self, expression: StructExpr, seeds: dict[str, TypeRef]) -> TypeRef:
        struct = self.struct_of(expression.type_name)
        if struct is None:
            for _, value in expression.fields: self.infer(value)
            return TypeRef(expression.type_name, expression.type_args)
        self.check_type_visibility(struct, expression.type_name, expression.pos, "struct")
        if split_type_name(self.canonical(expression.type_name))[0] != self.package:
            # Cross-package struct literals may only initialize public fields.
            public_fields = {f.name for f in struct.fields if f.public}
            for fname, fvalue in expression.fields:
                if fname not in public_fields:
                    self.error(
                        "C120",
                        getattr(fvalue, "pos", expression.pos),
                        f"field '{fname}' of '{self.canonical(expression.type_name)}' is private; only pub fields are visible outside the package",
                        self.literal_span(fvalue),
                    )
        if expression.type_args and len(expression.type_args) != len(struct.type_params):
            found = len(expression.type_args)
            self.error(
                "C096",
                expression.pos,
                f"struct '{struct.name}' requires {len(struct.type_params)} type argument(s), found {found}",
                self.to_line_end(expression.pos),
            )
        variables = {p.name for p in struct.type_params}
        type_bindings: dict[str, TypeRef] = dict(seeds)
        for p, a in zip(struct.type_params, expression.type_args):
            type_bindings.setdefault(p.name, a)
        field_map = {f.name: f for f in struct.fields}
        for name, value in expression.fields:
            actual = self.infer(value)
            field_decl = field_map.get(name)
            if field_decl:
                bind_type_pattern(field_decl.type, actual, variables, type_bindings)
        for p in struct.type_params:
            actual = type_bindings.get(p.name, UNKNOWN_T)
            if actual != UNKNOWN_T and actual.name not in self.current_type_params:
                for constraint in p.constraints:
                    need = substitute_type(constraint, type_bindings)
                    unbound = frozenset(n for n in variables if n in type_bindings and type_bindings[n] == UNKNOWN_T)
                    ok, solved = self.trait_satisfaction(actual, need, unbound)
                    if not ok:
                        self.error("C095", expression.pos, f"type {actual} does not satisfy generic constraint {need}")
        for name, value in expression.fields:
            field_decl = field_map.get(name)
            if field_decl:
                concrete = substitute_type(field_decl.type, type_bindings)
                if any(n in variables for n in free_names(concrete)):
                    # The instantiation is not fully known; fields are checked
                    # where the type is (here or at runtime).
                    continue
                actual = self.infer(value)
                if concrete != UNKNOWN_T and actual != UNKNOWN_T and not self.assignable(actual, concrete):
                    self.error(
                        "C098",
                        getattr(value, "pos", expression.pos),
                        f"field '{name}' of struct '{struct.name}': expected {concrete} but got {actual}",
                        self.literal_span(value),
                    )
        args = tuple(type_bindings.get(p.name, UNKNOWN_T) for p in struct.type_params)
        return TypeRef(expression.type_name, args)

    def validate_enum_pattern(self, pat_enum: EnumDecl, case_name: str, elements: list[Any], enum_bindings: dict[str, TypeRef], expr: Any) -> dict[str, TypeRef]:
        """Validate one enum-case pattern; return pattern-binding types.

        Pattern elements may be binding names, `_` wildcards, literals, or
        nested enum-case patterns (qualified `Pair(x, y)` or bare same-enum
        `Ok(v)`).
        """
        member = next((m for m in pat_enum.members if m.name == case_name), None)
        if member is None:
            self.error("C107", getattr(expr, "pos", None), f"enum '{pat_enum.name}' has no case '{case_name}'", self.literal_span(expr))
            return {}
        if not member.payload_types:
            if elements:
                self.error("C107", getattr(expr, "pos", None), f"case '{case_name}' takes no payload and cannot be used with pattern arguments", self.literal_span(expr))
            return {}
        if len(elements) != len(member.payload_types):
            self.error(
                "C107",
                getattr(expr, "pos", None),
                f"case '{case_name}' expects {len(member.payload_types)} payload value(s), found {len(elements)}",
                self.literal_span(expr),
            )
        bindings: dict[str, TypeRef] = {}
        seen: set[str] = set()
        for element, ptype in zip(elements, member.payload_types):
            concrete = substitute_type(ptype, enum_bindings)
            if isinstance(element, Name):
                if element.name == "_":
                    continue
                if element.name in seen:
                    self.error("C107", element.pos, f"duplicate pattern binding '{element.name}' in case '{case_name}'", self.literal_span(element))
                seen.add(element.name)
                bindings[element.name] = concrete
            elif isinstance(element, Literal):
                actual = NULL_T if element.literal_kind == "null" else TypeRef(element.literal_kind)
                if not any(n in {p.name for p in pat_enum.type_params} for n in free_names(concrete)) and not self.assignable(actual, concrete):
                    self.error("C108", element.pos, f"pattern payload for case '{case_name}': expected {concrete} but got {actual}", self.literal_span(element))
            elif isinstance(element, Call):
                nested: Optional[tuple[EnumDecl, str, list[Any], dict[str, TypeRef]]] = None
                if isinstance(element.callee, Member) and isinstance(element.callee.obj, Name):
                    nested_enum = self.enum_of(dotted_expression_name(element.callee.obj) or "")
                    if nested_enum is not None and any(m.name == element.callee.name for m in nested_enum.members):
                        nested_args = {p.name: a for p, a in zip(nested_enum.type_params, element.callee.obj.type_args)}
                        nested = (nested_enum, element.callee.name, [a.expr for a in element.args], nested_args)
                elif isinstance(element.callee, Name):
                    nested_enum = self.enum_of(pat_enum.name)
                    if element.callee.name in {m.name for m in pat_enum.members}:
                        nested = (pat_enum, element.callee.name, [a.expr for a in element.args], enum_bindings)
                if nested is None:
                    self.error(
                        "C107",
                        getattr(element, "pos", getattr(expr, "pos", None)),
                        "invalid pattern element: expected a binding name, '_' wildcard, literal, or enum case pattern",
                        self.literal_span(element),
                    )
                    continue
                nested_enum, nested_case, nested_elements, nested_bindings = nested
                nested_type = TypeRef(nested_enum.name, tuple(nested_bindings.get(p.name, UNKNOWN_T) for p in nested_enum.type_params))
                if not any(n in {p.name for p in pat_enum.type_params} for n in free_names(concrete)) and not self.assignable(nested_type, concrete):
                    self.error("C108", getattr(element, "pos", None), f"nested pattern of enum '{nested_enum.name}' cannot match payload of type {concrete}", self.literal_span(element))
                nested_bindings_out = self.validate_enum_pattern(nested_enum, nested_case, nested_elements, nested_bindings, element)
                for name, typ in nested_bindings_out.items():
                    if name in seen:
                        self.error("C107", getattr(element, "pos", None), f"duplicate pattern binding '{name}' in case '{case_name}'", self.literal_span(element))
                    seen.add(name)
                    bindings[name] = typ
            else:
                self.error(
                    "C107",
                    getattr(element, "pos", getattr(expr, "pos", None)),
                    "invalid pattern element: expected a binding name, '_' wildcard, literal, or enum case pattern",
                    self.literal_span(element),
                )
        return bindings

    def infer_enum_construction(self, enum: EnumDecl, obj_type: TypeRef, expression: Call) -> TypeRef:
        """Type an enum-case construction `Enum.Case(args)` / `Enum<Args>.Case(args)`."""
        case_name = expression.callee.name
        member = next((m for m in enum.members if m.name == case_name), None)
        if member is None:
            self.error("C107", expression.pos, f"enum '{enum.name}' has no case '{case_name}'", self.to_line_end(expression.pos))
            return UNKNOWN_T
        explicit = getattr(expression.callee.obj, "type_args", ()) or obj_type.args
        if not member.payload_types:
            if expression.args:
                self.error("C107", expression.pos, f"case '{case_name}' takes no payload", self.to_line_end(expression.pos))
            return TypeRef(enum.name, explicit)
        if explicit and len(explicit) != len(enum.type_params):
            self.error("C096", expression.pos, f"enum '{enum.name}' requires {len(enum.type_params)} type argument(s), found {len(explicit)}", self.to_line_end(expression.pos))
        variables = {p.name for p in enum.type_params}
        type_bindings: dict[str, TypeRef] = {p.name: a for p, a in zip(enum.type_params, explicit)}
        argument_types = [self.infer(a.expr) for a in expression.args]
        if len(argument_types) != len(member.payload_types):
            self.error(
                "C101",
                expression.pos,
                f"case '{case_name}' expects {len(member.payload_types)} payload value(s), found {len(argument_types)}",
                self.to_line_end(expression.pos),
            )
        for ptype, actual in zip(member.payload_types, argument_types):
            bind_type_pattern(ptype, actual, variables, type_bindings)
        for ptype, actual in zip(member.payload_types, argument_types):
            concrete = substitute_type(ptype, type_bindings)
            if any(n in variables for n in free_names(concrete)):
                continue
            if actual != UNKNOWN_T and not self.assignable(actual, concrete):
                self.error(
                    "C101",
                    expression.pos,
                    f"payload of case '{case_name}': expected {concrete} but got {actual}",
                    self.to_line_end(expression.pos),
                )
        for p in enum.type_params:
            actual = type_bindings.get(p.name, UNKNOWN_T)
            if actual != UNKNOWN_T and actual.name not in self.current_type_params:
                for constraint in p.constraints:
                    need = substitute_type(constraint, type_bindings)
                    if not self.type_satisfies_trait(actual, need):
                        self.error("C095", expression.pos, f"type {actual} does not satisfy generic constraint {need}")
        return TypeRef(enum.name, tuple(type_bindings.get(p.name, UNKNOWN_T) for p in enum.type_params))

    def iterable_element_type(self, typ: TypeRef) -> TypeRef:
        if typ.name == "string": return TypeRef("char")
        if typ.name in ("list", "stack") and typ.args: return typ.args[0]
        if typ.name == "map" and typ.args: return typ.args[0]
        iterator = self.method_sig_for_type(typ, "iterator")
        if iterator and iterator.return_type.name == "list" and iterator.return_type.args:
            return iterator.return_type.args[0]
        return UNKNOWN_T

    def infer_generic_call(self, function: FunctionDecl, argument_types: list[TypeRef], type_args: tuple[TypeRef, ...] = ()) -> TypeRef:
        variables = {p.name for p in function.type_params}
        bindings: dict[str, TypeRef] = {}
        if type_args:
            if len(type_args) != len(function.type_params):
                self.error(
                    "C096",
                    function.pos,
                    f"function '{function.name}' requires {len(function.type_params)} type argument(s), found {len(type_args)}",
                    self.to_line_end(function.pos),
                )
            for p, a in zip(function.type_params, type_args):
                bindings[p.name] = a
        for param, actual in zip(function.params, argument_types):
            bind_type_pattern(param.type, actual, variables, bindings)
        # Arguments are checked against substituted parameter types where the
        # instantiation is known (explicit arguments fix every parameter).
        if type_args:
            for i, param in enumerate(function.params):
                if i >= len(argument_types):
                    continue
                concrete = substitute_type(param.type, bindings)
                if any(n in variables for n in free_names(concrete)):
                    continue
                if argument_types[i] != UNKNOWN_T and not self.assignable(argument_types[i], concrete):
                    self.error(
                        "C101",
                        function.pos,
                        f"argument {i + 1} of '{function.name}': expected {concrete} but got {argument_types[i]}",
                        self.to_line_end(function.pos),
                    )
        for type_param in function.type_params:
            actual = bindings.get(type_param.name, UNKNOWN_T)
            if actual == UNKNOWN_T:
                continue
            for constraint in type_param.constraints:
                need = substitute_type(constraint, bindings)
                unbound = frozenset(n for n in variables if bindings.get(n, UNKNOWN_T) == UNKNOWN_T)
                ok, solved = self.trait_satisfaction(actual, need, unbound)
                if not ok:
                    self.error("C095", function.pos, f"type {actual} does not satisfy generic constraint {need}")
                bindings.update(solved)
        return substitute_type(function.return_type, bindings)

    def receiver_is_mutable(self, expression: Any) -> bool:
        if isinstance(expression, Name):
            if expression.name == "self": return bool(self.current_function and self.current_function.mutating)
            binding = self.lookup(expression.name)
            return bool(binding and binding.mutable)
        return False

    def check_assignment_target(self, target: Any, pos: SourcePos) -> None:
        """Mutability of assignment targets: immutable bindings (C116) and
        immutable struct fields (C117)."""
        if isinstance(target, Name):
            if target.name == "self":
                return
            binding = self.lookup(target.name)
            if binding is not None and not binding.mutable:
                self.error(
                    "C116",
                    pos,
                    f"cannot assign to immutable variable '{target.name}'; declare it as 'mut'",
                    self.to_line_end(pos),
                )
            return
        if isinstance(target, Member) and isinstance(target.obj, Name) and target.obj.name == "self":
            field = self.receiver_field(target.name)
            if field is not None and not field.mutable:
                self.error(
                    "C117",
                    pos,
                    f"cannot assign to immutable field '{target.name}' of struct '{self.current_struct.name}'; declare the field as 'pub mut'",
                    self.to_line_end(pos),
                )
            return
        if isinstance(target, Member):
            obj_type = self.infer(target.obj)
            struct = self.struct_of(obj_type.name)
            if struct is not None:
                field = next((f for f in struct.fields if f.name == target.name), None)
                if field is not None and not field.mutable:
                    self.error(
                        "C117",
                        pos,
                        f"cannot assign to immutable field '{target.name}' of struct '{struct.name}'",
                        self.to_line_end(pos),
                    )

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
        global INTERP
        INTERP = self
        self.packages: dict[str, Namespace] = {}
        self.structs: dict[tuple[str,str], StructDecl] = {}
        self.traits: dict[tuple[str,str], TraitDecl] = {}
        self.enums: dict[tuple[str,str], EnumTypeValue] = {}
        self.entry_package: Optional[str] = None
        self.cwd = pathlib.Path.cwd()
        self.builtins = build_builtins()
        self.core_traits = core_trait_decls()
        self.type_bindings_stack: list[dict[str, TypeRef]] = []
        self.expected_return_stack: list[TypeRef] = []

    # ---- program registration -------------------------------------------------
    def add_program(self, program: Program) -> None:
        canonicalize_program(program)
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
                    members[m.name] = EnumValue(d.name, m.name, next_value)
                    next_value += 1
                et = EnumTypeValue(d, members); et.canonical_name = f"{program.package}.{d.name}"
                self.enums[(program.package, d.name)] = et; ns.values[d.name] = et
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
        return self.structs.get(type_key(typ.name, package)) or self.traits.get(type_key(typ.name, package)) or self.enums.get(type_key(typ.name, package)) or self.core_traits.get(typ.name)

    def resolve_runtime_type(self, typ: TypeRef) -> TypeRef:
        resolved = typ
        for bindings in self.type_bindings_stack:
            resolved = substitute_type(resolved, bindings)
        return resolved

    def method_sig_for_type(self, typ: TypeRef, name: str, package: str) -> Optional[MethodSig]:
        built = builtin_method_signature(typ, name)
        if built is not None:
            return built
        struct = self.structs.get(type_key(typ.name, package))
        if struct is None:
            return None
        bindings = {p.name: a for p, a in zip(struct.type_params, typ.args)}
        method = next((m for m in struct.methods if m.public and m.name == name), None)
        if method is None:
            return None
        return MethodSig(
            tuple(substitute_type(p.type, bindings) for p in method.params),
            substitute_type(method.return_type, bindings),
            method.mutating,
        )

    def type_satisfies_trait(self, actual: TypeRef, expected: TypeRef, package: str, variables: frozenset[str] = frozenset()) -> bool:
        return self.trait_satisfaction(actual, expected, package, variables)[0]

    def trait_satisfaction(self, actual: TypeRef, expected: TypeRef, package: str, variables: frozenset[str] = frozenset()) -> tuple[bool, dict[str, TypeRef]]:
        trait = self.traits.get(type_key(expected.name, package)) or self.core_traits.get(expected.name)
        if trait is None:
            return False, {}
        return trait_satisfaction(actual, expected, trait, lambda t, n: self.method_sig_for_type(t, n, package), variables)

    def generic_arity_of(self, typ: TypeRef, package: str) -> Optional[int]:
        """Required number of type arguments for a generic type, if any."""
        if typ.name in ("list", "stack"): return 1
        if typ.name == "map": return 2
        decl = self.structs.get(type_key(typ.name, package))
        if decl is not None: return len(decl.type_params)
        enum = self.enums.get(type_key(typ.name, package))
        if enum is not None: return len(enum.decl.type_params)
        return None

    def value_matches_type(self, value: Any, typ: TypeRef, package: str) -> bool:
        typ = self.resolve_runtime_type(typ)
        if typ.name == "any": return True
        base = typ.nonnull()
        if base.args:
            arity = self.generic_arity_of(base, package)
            if arity is not None and len(base.args) != arity:
                return False
        if value is None: return typ.nullable
        if base.name == "bool": return isinstance(value, bool)
        if base.name == "byte": return isinstance(value, ByteValue)
        if base.name == "int": return isinstance(value, (int, ByteValue)) and not isinstance(value, bool)
        if base.name == "float": return isinstance(value, (int, float, ByteValue)) and not isinstance(value, bool)
        if base.name == "char": return isinstance(value, CharValue)
        if base.name == "string": return isinstance(value, str) and not isinstance(value, CharValue)
        if base.name == "exception": return isinstance(value, SolvikExceptionValue)
        if base.name == "func":
            # Function types are invariant: the signature must match exactly,
            # except UNKNOWN entries which defer to the call itself.
            return same_func_type(function_value_type(value), base)
        if base.name == "list":
            return isinstance(value, list) and (not base.args or all(self.value_matches_type(v, base.args[0], package) for v in value))
        if base.name == "stack":
            return isinstance(value, StackValue) and (not base.args or all(self.value_matches_type(v, base.args[0], package) for v in value.items))
        if base.name == "map":
            return isinstance(value, dict) and (len(base.args) != 2 or all(self.value_matches_type(k, base.args[0], package) and self.value_matches_type(v, base.args[1], package) for k,v in value.items()))
        if type_key(base.name, package) in self.structs:
            if not isinstance(value, StructValue) or value.type_name != base.name:
                return False
            return not base.args or not value.type_args or base.args == value.type_args
        if type_key(base.name, package) in self.enums:
            if not isinstance(value, EnumValue) or value.enum_name != base.name:
                return False
            # Generic enum instantiations are invariant, like structs.
            return not base.args or not value.type_args or base.args == value.type_args
        if self.traits.get(type_key(base.name, package)) or self.core_traits.get(base.name):
            return self.type_satisfies_trait(value_type_ref(value), base, package)
        return False

    def coerce_for_type(self, value: Any, typ: TypeRef, package: str) -> Any:
        typ = self.resolve_runtime_type(typ)
        base = typ.nonnull()
        arity = self.generic_arity_of(base, package)
        if arity is not None and base.args and len(base.args) != arity:
            raise runtime_error(f"type '{base.name}' requires {arity} type argument(s), found {len(base.args)}", "E067")
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

    # ---- statement execution --------------------------------------------------
    def exec_block(self, block: Block, env: Env, package: str, new_scope: bool = True, receiver: Optional[StructValue] = None, receiver_mutable: bool = False) -> None:
        local = Env(env) if new_scope else env
        for s in block.statements:
            self.exec_stmt(s, local, package, receiver, receiver_mutable)

    def exec_stmt(self, s: Any, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> None:
        if isinstance(s, Block): self.exec_block(s, env, package, True, receiver, receiver_mutable); return
        if isinstance(s, VarDecl):
            self.seed_expected_type(s.value, s.type)
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
            if len(s.names) == 2:
                if not isinstance(source, dict): raise runtime_error("two-binding for loop requires a map")
                seq = list(source.items())
            else:
                seq = self.iterable_values(source, package)
            for item in seq:
                loop_env = Env(env)
                if len(s.names) == 2:
                    loop_env.declare(s.names[0], copy_value(item[0]), ANY_T, False); loop_env.declare(s.names[1], copy_value(item[1]), ANY_T, False)
                else:
                    loop_env.declare(s.names[0], copy_value(item), ANY_T, False)
                try: self.exec_block(s.body, loop_env, package, False, receiver, receiver_mutable)
                except ContinueSignal: continue
                except BreakSignal: break
            return
        if isinstance(s, SwitchStmt):
            value = self.eval_expr(s.value, env, package, receiver, receiver_mutable)
            for c in s.cases:
                matched = c.expr is None
                if c.expr is not None:
                    shape = enum_pattern_shape(c.expr)
                    if shape is not None:
                        kind, enum_expr, case_name, elements = shape
                        enum_type: Optional[EnumTypeValue] = None
                        if kind == "qualified":
                            obj = self.eval_expr(enum_expr, env, package, receiver, receiver_mutable)
                            if isinstance(obj, EnumTypeValue) and case_name in obj.members:
                                enum_type = obj
                        elif isinstance(value, EnumValue):
                            et = self.enums.get(type_key(value.enum_name, package))
                            if et is not None and case_name in et.members:
                                enum_type = et
                        if enum_type is not None:
                            matched, bindings = self.match_enum_pattern(value, enum_type, case_name, elements, env, package, receiver, receiver_mutable)
                            if matched:
                                local = Env(env)
                                for name, (bval, btype) in bindings.items():
                                    local.declare(name, bval, btype, False)
                                self.exec_block(c.body, local, package, False, receiver, receiver_mutable)
                                break
                            continue
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
            if s.value is not None and self.expected_return_stack:
                self.seed_expected_type(s.value, self.expected_return_stack[-1])
            v = None if s.value is None else self.eval_expr(s.value, env, package, receiver, receiver_mutable)
            raise ReturnSignal(copy_value(v))
        if isinstance(s, BreakStmt): raise BreakSignal()
        if isinstance(s, ContinueStmt): raise ContinueSignal()
        raise SolvikError(f"unhandled statement node {type(s).__name__}")

    def iterable_values(self, source: Any, package: str) -> list[Any]:
        native = builtin_method(source, "iterator")
        if native is not None:
            values = self.call_value(native, [], False)
            if isinstance(values, list):
                return values
        if isinstance(source, StructValue):
            decl = self.structs.get(type_key(source.type_name, package))
            method = next((m for m in decl.methods if m.public and m.name == "iterator"), None) if decl else None
            if method is not None:
                result = self.call_value(BoundMethod(source, UserFunction(method, package), False), [], False)
                if isinstance(result, list):
                    return result
                raise runtime_error(f"iterator() on {source.type_name} must return list<T>")
        raise runtime_error(f"value of type {type_name_of(source)} is not iterable")

    # ---- expression evaluation ------------------------------------------------
    def eval_expr(self, e: Any, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> Any:
        if isinstance(e, Literal): return e.value
        if isinstance(e, Name): return self.resolve_name(e.name, env, package, receiver, receiver_mutable)
        if isinstance(e, FuncExpr):
            # Capture the lexical environment at the point of definition.
            signature = TypeRef(
                "func",
                tuple(self.resolve_runtime_type(p.type) for p in e.params)
                + (self.resolve_runtime_type(e.return_type),),
            )
            decl = FunctionDecl("<closure>", e.params, e.return_type, e.body, e.pos)
            return ClosureValue(decl, env, package, receiver, receiver_mutable, signature)
        if isinstance(e, ListExpr): return [copy_value(self.eval_expr(x, env, package, receiver, receiver_mutable)) for x in e.items]
        if isinstance(e, MapExpr): return {self.eval_expr(k, env, package, receiver, receiver_mutable): copy_value(self.eval_expr(v, env, package, receiver, receiver_mutable)) for k,v in e.items}
        if isinstance(e, StructExpr):
            decl = self.structs.get(type_key(e.type_name, package))
            if not decl: raise runtime_error(f"unknown struct {e.type_name}")
            supplied = dict(e.fields); values: dict[str, Any] = {}
            if set(supplied) != {f.name for f in decl.fields}: raise runtime_error(f"struct literal for {e.type_name} must initialize every field exactly once")
            if e.type_args and len(e.type_args) != len(decl.type_params):
                raise runtime_error(f"{decl.name} requires {len(decl.type_params)} type argument(s), found {len(e.type_args)}", "E067")
            raw = {name: self.eval_expr(expr, env, package, receiver, receiver_mutable) for name, expr in supplied.items()}
            variables = {p.name for p in decl.type_params}
            type_bindings: dict[str, TypeRef] = {}
            for p, a in zip(decl.type_params, e.type_args):
                # Resolve through ambient bindings so `Box<T> { ... }` inside a
                # generic function instantiates with the caller's argument type.
                type_bindings[p.name] = self.resolve_runtime_type(a)
            expected = getattr(e, "expected_type", None)
            if expected is not None and expected.name.rsplit(".", 1)[-1] == decl.name and len(expected.args) == len(decl.type_params):
                for p, a in zip(decl.type_params, expected.args):
                    type_bindings.setdefault(p.name, self.resolve_runtime_type(a))
            for f in decl.fields:
                actual = value_type_ref(raw[f.name])
                if actual.name == "null":
                    hint = self.declared_type_of(supplied[f.name], env, receiver, package)
                    if hint is not None:
                        actual = hint
                bind_type_pattern(f.type, actual, variables, type_bindings)
            for p in decl.type_params:
                actual = type_bindings.get(p.name)
                if actual is None or actual == UNKNOWN_T:
                    raise runtime_error(
                        f"cannot infer type parameter {p.name} for struct {decl.name}; "
                        f"annotate the declaration or use explicit type arguments like {decl.name}<...>",
                        "E067",
                    )
                for constraint in p.constraints:
                    need = substitute_type(constraint, type_bindings)
                    if not self.type_satisfies_trait(actual, need, package):
                        raise runtime_error(f"type {actual} does not satisfy generic constraint {need}", "E067")
            for f in decl.fields:
                values[f.name] = self.coerce_for_type(raw[f.name], substitute_type(f.type, type_bindings), package)
            type_args = tuple(type_bindings[p.name] for p in decl.type_params)
            return StructValue(e.type_name, values, type_args)
        if isinstance(e, Unary):
            v = self.eval_expr(e.expr, env, package, receiver, receiver_mutable)
            v = numeric_value(v)
            if e.op == "!": return not truth(v)
            if e.op == "-": return -v
            if e.op == "+": return +v
            if e.op == "~": return ~int(v)
        if isinstance(e, Binary): return self.eval_binary(e, env, package, receiver, receiver_mutable)
        if isinstance(e, Assign):
            self.seed_assignment(e.target, e.value, env, package, receiver)
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
            hints: list[Optional[TypeRef]] = []
            for a in e.args:
                v = self.eval_expr(a.expr, env, package, receiver, receiver_mutable)
                if a.spread:
                    if not isinstance(v, list): raise runtime_error("spread requires a list")
                    args.extend(copy_value(v)); hints.extend([None] * len(v))
                else:
                    args.append(v)
                    hints.append(self.declared_type_of(a.expr, env, receiver, package) if v is None else None)
            if isinstance(callee, CaseConstructor):
                return self.construct_enum_case(callee, args, getattr(e, "expected_type", None), hints)
            return self.call_value(callee, args, mutable, e.type_args, hints)
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
            decl = self.structs.get(type_key(actual.type_name, package))
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
            decl = self.structs.get(type_key(receiver.type_name, package))
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
            member = next((m for m in obj.decl.members if m.name == e.name), None)
            type_args = tuple(self.resolve_runtime_type(a) for a in getattr(e.obj, "type_args", ()))
            if member is not None and member.payload_types:
                return CaseConstructor(obj.canonical_name, e.name, member.payload_types, type_args, package)
            return EnumValue(obj.canonical_name, e.name, obj.members[e.name].value, (), type_args)
        if isinstance(obj, SolvikExceptionValue):
            if e.name == "message": return obj.message
            if e.name == "code": return obj.code
            if e.name == "trace": return obj.trace
            raise runtime_error(f"exception has no member {e.name}")
        if isinstance(obj, StructValue):
            if e.name in obj.fields:
                decl = self.structs.get(type_key(obj.type_name, package))
                if decl is not None:
                    field = next((f for f in decl.fields if f.name == e.name), None)
                    if field is not None and not field.public and split_type_name(obj.type_name)[0] != package:
                        raise runtime_error(f"field '{e.name}' of '{obj.type_name}' is private", "E070")
                return copy_value(obj.fields[e.name])
            decl = self.structs.get(type_key(obj.type_name, package))
            if decl:
                m = next((m for m in decl.methods if m.name == e.name), None)
                if m:
                    if not m.public and split_type_name(obj.type_name)[0] != package:
                        raise runtime_error(f"method '{e.name}' of '{obj.type_name}' is private", "E070")
                    return BoundMethod(obj, UserFunction(m, package), self.target_is_mutable(e.obj, env, receiver, receiver_mutable))
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

    def seed_expected_type(self, value_expr: Any, typ: TypeRef) -> None:
        """Give a struct literal or enum construction the instantiation named
        by its context.

        Declarations, assignments, and returns that name a concrete generic
        instantiation (`b: Box<int?> = Box { value: null }`,
        `r: Result<int, string> = Result.Ok(5)`) seed type inference so the
        value does not have to restate the type.
        """
        if isinstance(value_expr, StructExpr) and not value_expr.type_args \
                and typ.name == value_expr.type_name and typ.args:
            value_expr.expected_type = typ.nonnull()
            return
        if (isinstance(value_expr, Call) and not value_expr.type_args
                and isinstance(value_expr.callee, Member)
                and dotted_expression_name(value_expr.callee.obj) is not None
                and typ.name.rsplit(".", 1)[-1] == (dotted_expression_name(value_expr.callee.obj) or "").rsplit(".", 1)[-1]
                and typ.args):
            value_expr.expected_type = typ.nonnull()
            value_expr.expected_type = typ.nonnull()

    def target_declared_type(self, target: Any, env: Env, package: str, receiver: Optional[StructValue]) -> Optional[TypeRef]:
        if isinstance(target, Name):
            try:
                return env.get_binding(target.name).declared_type
            except RuntimeSignal:
                pass
            if receiver is not None and target.name in receiver.fields:
                hint = self.declared_type_of(target, env, receiver, package)
                if hint is not None:
                    return hint
            return None
        if isinstance(target, Member) and isinstance(target.obj, Name):
            if target.obj.name == "self" and receiver is not None:
                hint = self.declared_type_of(Name(target.name, target.pos), env, receiver, package)
                if hint is not None:
                    return hint
                return None
            try:
                base = env.get_binding(target.obj.name).declared_type
            except RuntimeSignal:
                return None
            decl = self.structs.get(type_key(base.name, package))
            if decl is None:
                return None
            field = next((f for f in decl.fields if f.name == target.name), None)
            if field is None:
                return None
            bindings = {p.name: a for p, a in zip(decl.type_params, base.args)}
            return substitute_type(field.type, bindings)
        return None

    def seed_assignment(self, target: Any, value_expr: Any, env: Env, package: str, receiver: Optional[StructValue]) -> None:
        typ = self.target_declared_type(target, env, package, receiver)
        if typ is not None:
            self.seed_expected_type(value_expr, typ)

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
                decl = self.structs[type_key(receiver.type_name, package)]; fd = next(f for f in decl.fields if f.name == target.name)
                if not fd.mutable: raise runtime_error(f"field {target.name} is immutable")
                receiver.fields[target.name] = self.coerce_for_type(value, fd.type, package); return copy_value(receiver.fields[target.name])
            raise runtime_error(f"undefined assignment target {target.name}")
        if isinstance(target, Member):
            obj = self.eval_expr(target.obj, env, package, receiver, receiver_mutable)
            if not isinstance(obj, StructValue): raise runtime_error("member assignment requires struct")
            decl = self.structs[type_key(obj.type_name, package)]; fd = next((f for f in decl.fields if f.name == target.name), None)
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
        if isinstance(a, (UserFunction, ClosureValue, BoundMethod, NativeFunction)) or isinstance(b, (UserFunction, ClosureValue, BoundMethod, NativeFunction)):
            # Callable values compare by identity: two references to the same
            # function are equal; distinct closures never are.
            return a is b
        if isinstance(a, StructValue) and isinstance(b, StructValue): return a.type_name == b.type_name and a.fields == b.fields
        if isinstance(a, EnumValue) or isinstance(b, EnumValue):
            if not (isinstance(a, EnumValue) and isinstance(b, EnumValue)):
                return False
            if a.enum_name != b.enum_name or a.member_name != b.member_name:
                return False
            if a.payload or b.payload:
                if len(a.payload) != len(b.payload):
                    return False
                return all(self.equal(x, y) for x, y in zip(a.payload, b.payload))
            return a.value == b.value
        return numeric_value(a) == numeric_value(b)

    # ---- function calls --------------------------------------------------------
    def declared_type_of(self, expr: Any, env: Env, receiver: Optional[StructValue], package: str) -> Optional[TypeRef]:
        """Declared type of a simple-name expression, for null-value inference.

        A null value carries no runtime type evidence; when the expression is a
        binding or receiver field with a known declared type, that type
        participates in generic inference.
        """
        if not isinstance(expr, Name):
            return None
        try:
            return env.get_binding(expr.name).declared_type
        except RuntimeSignal:
            pass
        if receiver is not None and expr.name in receiver.fields:
            decl = self.structs.get(type_key(receiver.type_name, package))
            if decl is not None:
                field = next((f for f in decl.fields if f.name == expr.name), None)
                if field is not None:
                    bindings = {p.name: a for p, a in zip(decl.type_params, receiver.type_args)}
                    return substitute_type(field.type, bindings)
        return None
    def call_value(self, callee: Any, args: list[Any], receiver_mutable: bool, type_args: tuple[TypeRef, ...] = (), arg_type_hints: Optional[list[Optional[TypeRef]]] = None) -> Any:
        if callee is None:
            raise runtime_error("null reference", "E031")
        if isinstance(callee, CallableNamespace):
            if type_args:
                raise runtime_error(f"package function does not accept type arguments", "E067")
            try: return callee.call(*[copy_value(a) for a in args])
            except RuntimeSignal: raise
            except Exception as ex: raise runtime_error(str(ex))
        if isinstance(callee, NativeFunction):
            if type_args:
                raise runtime_error(f"built-in function does not accept type arguments", "E067")
            try: return callee(*[copy_value(a) for a in args])
            except RuntimeSignal: raise
            except Exception as ex: raise runtime_error(str(ex))
        if isinstance(callee, BoundMethod):
            if callee.function.decl.mutating and not callee.receiver_mutable: raise runtime_error(f"mutating method {callee.function.decl.name} requires mutable receiver")
            return self.call_user(callee.function, args, callee.receiver, callee.receiver_mutable, type_args, arg_type_hints)
        if isinstance(callee, ClosureValue):
            if type_args: raise runtime_error("closures do not accept type arguments", "E067")
            return self.call_closure(callee, args)
        if isinstance(callee, UserFunction): return self.call_user(callee, args, None, False, type_args, arg_type_hints)
        if isinstance(callee, StructTypeValue): raise runtime_error("structs use named-field literals, not call syntax")
        raise runtime_error(f"value of type {type_name_of(callee)} is not callable")

    def construct_enum_case(self, ctor: CaseConstructor, args: list[Any], expected: Optional[TypeRef], hints: Optional[list[Optional[TypeRef]]] = None) -> EnumValue:
        """Build an enum value for a payload (or empty) case.

        Type arguments come from explicit `Enum<Args>.Case`, an expected
        instantiation seeded by the surrounding declaration/assignment/return,
        and inference from payload values, in that order of precedence."""
        et = self.enums.get(type_key(ctor.enum_name, ctor.package))
        if et is None:
            raise runtime_error(f"unknown enum {ctor.enum_name}", "E069")
        decl = et.decl
        member = next((m for m in decl.members if m.name == ctor.case_name), None)
        if member is None:
            raise runtime_error(f"enum {decl.name} has no case {ctor.case_name}", "E069")
        if len(args) != len(ctor.payload_types):
            raise runtime_error(f"case {ctor.case_name} expects {len(ctor.payload_types)} payload value(s), found {len(args)}", "E068")
        variables = {p.name for p in decl.type_params}
        type_bindings: dict[str, TypeRef] = {}
        for p, a in zip(decl.type_params, ctor.type_args):
            type_bindings[p.name] = self.resolve_runtime_type(a)
        if expected is not None and expected.name.rsplit(".", 1)[-1] == decl.name and len(expected.args) == len(decl.type_params):
            for p, a in zip(decl.type_params, expected.args):
                type_bindings.setdefault(p.name, self.resolve_runtime_type(a))
        for i, (ptype, arg) in enumerate(zip(member.payload_types, args)):
            actual = value_type_ref(arg)
            if actual.name == "null" and hints is not None and i < len(hints) and hints[i] is not None:
                actual = hints[i]
            bind_type_pattern(ptype, actual, variables, type_bindings)
        for p in decl.type_params:
            actual = type_bindings.get(p.name)
            if actual is None or actual == UNKNOWN_T:
                raise runtime_error(
                    f"cannot infer type parameter {p.name} for enum {decl.name}; "
                    f"use explicit type arguments like {decl.name}<...> or annotate the value's type",
                    "E067",
                )
            for constraint in p.constraints:
                need = substitute_type(constraint, type_bindings)
                if not self.type_satisfies_trait(actual, need, ctor.package):
                    raise runtime_error(f"type {actual} does not satisfy generic constraint {need}", "E067")
        payload = tuple(
            self.coerce_for_type(v, substitute_type(ptype, type_bindings), ctor.package)
            for ptype, v in zip(member.payload_types, args)
        )
        type_args = tuple(type_bindings[p.name] for p in decl.type_params)
        return EnumValue(et.canonical_name, member.name, et.members[member.name].value, payload, type_args)

    def resolve_pattern_enum(self, call: Any, env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool, enclosing: EnumTypeValue) -> Optional[tuple[EnumTypeValue, str, list[Any]]]:
        """Resolve a nested pattern call to (enum, case, elements)."""
        if not isinstance(call, Call):
            return None
        if isinstance(call.callee, Member) and isinstance(call.callee.obj, Name):
            obj = self.eval_expr(call.callee.obj, env, package, receiver, receiver_mutable)
            if isinstance(obj, EnumTypeValue) and call.callee.name in obj.members:
                return (obj, call.callee.name, [a.expr for a in call.args])
        if isinstance(call.callee, Name) and call.callee.name in enclosing.members:
            return (enclosing, call.callee.name, [a.expr for a in call.args])
        return None

    def match_enum_pattern(self, value: Any, enum_type: EnumTypeValue, case_name: str, elements: list[Any], env: Env, package: str, receiver: Optional[StructValue], receiver_mutable: bool) -> tuple[bool, dict[str, tuple[Any, TypeRef]]]:
        """Match a switch value against an enum-case pattern; return bindings."""
        if not isinstance(value, EnumValue) or value.enum_name != enum_type.canonical_name or value.member_name != case_name:
            return False, {}
        member = next((m for m in enum_type.decl.members if m.name == case_name), None)
        if member is None:
            raise runtime_error(f"enum {enum_type.decl.name} has no case {case_name}", "E069")
        if len(elements) != len(member.payload_types):
            raise runtime_error(f"case {case_name} expects {len(member.payload_types)} payload value(s), found {len(elements)}", "E068")
        bindings: dict[str, tuple[Any, TypeRef]] = {}
        type_bindings = {p.name: a for p, a in zip(enum_type.decl.type_params, value.type_args)}
        for element, ptype, payload in zip(elements, member.payload_types, value.payload):
            concrete = substitute_type(ptype, type_bindings)
            if isinstance(element, Name):
                if element.name == "_":
                    continue
                bindings[element.name] = (copy_value(payload), concrete)
            elif isinstance(element, Literal):
                if not self.equal(payload, element.value):
                    return False, {}
            elif isinstance(element, Call):
                nested = self.resolve_pattern_enum(element, env, package, receiver, receiver_mutable, enum_type)
                if nested is None:
                    raise runtime_error("invalid nested pattern element", "E069")
                sub_et, sub_case, sub_elements = nested
                ok, sub_bindings = self.match_enum_pattern(payload, sub_et, sub_case, sub_elements, env, package, receiver, receiver_mutable)
                if not ok:
                    return False, {}
                bindings.update(sub_bindings)
            else:
                raise runtime_error("invalid pattern element", "E069")
        return True, bindings

    def call_closure(self, closure: ClosureValue, args: list[Any]) -> Any:
        """Execute a closure in its captured environment.

        Parameters are declared in a child of the captured environment, so they
        shadow captured names. Arguments are coerced to the declared parameter
        types and results to the declared return type, exactly like a named
        function call."""
        d = closure.decl
        fixed = len(d.params) - (1 if _is_variadic(d) else 0)
        if len(args) < fixed or (not _is_variadic(d) and len(args) != len(d.params)):
            raise runtime_error(f"closure expects {len(d.params)} argument(s), found {len(args)}", "E068")
        env = Env(closure.env)
        for i, p in enumerate(d.params):
            if p.variadic:
                val = [self.coerce_for_type(x, p.type, closure.package) for x in args[i:]]
                env.declare(p.name, val, TypeRef("list", (p.type,)), False)
            else:
                env.declare(p.name, self.coerce_for_type(args[i], p.type, closure.package), p.type, False)
        try:
            self.exec_block(d.body, env, closure.package, False, closure.receiver, closure.receiver_mutable)
        except (BreakSignal, ContinueSignal):
            raise runtime_error("break/continue outside of loop", "E068")
        except ReturnSignal as r:
            return None if d.return_type.name == "void" else self.coerce_for_type(r.value, d.return_type, closure.package)
        if d.return_type.name != "void":
            raise runtime_error(f"closure reached end without returning {d.return_type}", "E068")
        return None

    def call_user(self, fn: UserFunction, args: list[Any], receiver: Optional[StructValue], receiver_mutable: bool, type_args: tuple[TypeRef, ...] = (), arg_type_hints: Optional[list[Optional[TypeRef]]] = None) -> Any:
        d = fn.decl; env = Env()
        fixed = len(d.params) - (1 if d.params and d.params[-1].variadic else 0)
        if len(args) < fixed or (not d.params or not d.params[-1].variadic) and len(args) != len(d.params):
            raise runtime_error(f"{d.name} argument count mismatch")
        if type_args and len(type_args) != len(d.type_params):
            raise runtime_error(f"{d.name} requires {len(d.type_params)} type argument(s), found {len(type_args)}", "E067")

        type_bindings: dict[str, TypeRef] = {}
        if receiver is not None and d.owner_struct:
            owner = self.structs.get(type_key(d.owner_struct, fn.package))
            if owner:
                type_bindings.update({p.name: a for p, a in zip(owner.type_params, receiver.type_args)})
        for p, a in zip(d.type_params, type_args):
            # Resolve through ambient bindings so calls like `first<T>(xs, v)`
            # inside a generic function instantiate with the caller's type.
            type_bindings[p.name] = self.resolve_runtime_type(a)
        variables = {p.name for p in d.type_params}
        for i, (p, arg) in enumerate(zip(d.params, args)):
            if p.variadic:
                continue
            actual = value_type_ref(arg)
            if actual.name == "null" and arg_type_hints is not None and i < len(arg_type_hints):
                hint = arg_type_hints[i]
                if hint is not None:
                    actual = hint
            bind_type_pattern(p.type, actual, variables, type_bindings)
        for p in d.type_params:
            actual = type_bindings.get(p.name)
            if actual is None or actual == UNKNOWN_T:
                # Constraint solving may still bind parameters that appear only
                # in constraints (checked below before the inference verdict).
                continue
            for constraint in p.constraints:
                need = substitute_type(constraint, type_bindings)
                unbound = frozenset(n for n in variables if type_bindings.get(n, UNKNOWN_T) == UNKNOWN_T)
                ok, solved = self.trait_satisfaction(actual, need, fn.package, unbound)
                if not ok:
                    raise runtime_error(f"type {actual} does not satisfy generic constraint {need}", "E067")
                type_bindings.update(solved)
        for p in d.type_params:
            actual = type_bindings.get(p.name)
            if actual is None or actual == UNKNOWN_T:
                raise runtime_error(
                    f"cannot infer type parameter {p.name} for function {d.name}; "
                    "pass a non-null value, use explicit type arguments, or annotate the value's type",
                    "E067",
                )

        self.type_bindings_stack.append(type_bindings)
        self.expected_return_stack.append(substitute_type(d.return_type, type_bindings))
        try:
            for i, p in enumerate(d.params):
                concrete = substitute_type(p.type, type_bindings)
                if p.variadic:
                    val = [self.coerce_for_type(x, concrete, fn.package) for x in args[i:]]
                    env.declare(p.name, val, TypeRef("list", (concrete,)), False)
                else:
                    env.declare(p.name, self.coerce_for_type(args[i], concrete, fn.package), concrete, False)
            try:
                assert d.body is not None
                self.exec_block(d.body, env, fn.package, False, receiver, receiver_mutable and d.mutating)
            except ReturnSignal as r:
                result_type = substitute_type(d.return_type, type_bindings)
                return None if result_type.name == "void" else self.coerce_for_type(r.value, result_type, fn.package)
            result_type = substitute_type(d.return_type, type_bindings)
            if result_type.name != "void": raise runtime_error(f"function {d.name} reached end without returning {result_type}")
            return None
        finally:
            self.expected_return_stack.pop()
            self.type_bindings_stack.pop()


# =============================================================================
# Built-ins and standard library
# =============================================================================

def nf(name: str, fn: Callable[..., Any]) -> NativeFunction: return NativeFunction(name, fn)

PROGRAM_ARGS: list[str] = []
"""Command-line arguments after the source file, exposed as `process.args()`."""


def invoke_callable(fn: Any, args: list[Any]) -> Any:
    """Invoke a Solvik callable value (closure/function/method) from a built-in."""
    if INTERP is None:
        raise runtime_error("interpreter unavailable")
    return INTERP.call_value(fn, [copy_value(a) for a in args], False)


def same_func_type(a: TypeRef, b: TypeRef) -> bool:
    """Function-type equality tolerating UNKNOWN type variables on either side
    (used by higher-order collection methods whose element transforms are
    typed with unbound parameters)."""
    if a.name != "func" or b.name != "func":
        return a == b
    if len(a.args) != len(b.args):
        return False
    return all(x == y or x == UNKNOWN_T or y == UNKNOWN_T for x, y in zip(a.args, b.args))


def _list_reduce(xs: list[Any], combine: Any) -> Any:
    if not xs:
        raise runtime_error("reduce of an empty list", "E072")
    mut = copy_value(xs[0])
    for x in xs[1:]:
        mut = invoke_callable(combine, [mut, x])
    return mut


def _list_fold(xs: list[Any], initial: Any, combine: Any) -> Any:
    mut = copy_value(initial)
    for x in xs:
        mut = invoke_callable(combine, [mut, x])
    return mut


def _list_sort(xs: list[Any], compare: Any) -> list[Any]:
    def cmp(a: Any, b: Any) -> int:
        return int(numeric_value(invoke_callable(compare, [a, b])))
    return [copy_value(x) for x in sorted(xs, key=functools.cmp_to_key(cmp))]


def _json_parse(s: Any) -> Any:
    try:
        return py_json.loads(str(s))
    except (ValueError, TypeError) as ex:
        raise runtime_error(f"json parse error: {ex}", "E072")


def _json_stringify(v: Any) -> str:
    def default(o: Any) -> Any:
        if isinstance(o, (EnumValue, StructValue, SolvikExceptionValue)):
            return solvik_string(o)
        raise runtime_error(f"value of type {type_name_of(o)} is not representable as JSON", "E072")
    try:
        return py_json.dumps(v, default=default)
    except (TypeError, ValueError) as ex:
        raise runtime_error(f"json stringify error: {ex}", "E072")


def _http_request(method: str, url: str, body: Optional[str], headers: Optional[dict[str, str]]) -> dict[str, Any]:
    data = body.encode("utf-8") if body is not None else None
    request = urllib.request.Request(str(url), data=data, headers=dict(headers or {}), method=str(method))
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return {
                "status": int(response.status),
                "body": response.read().decode("utf-8"),
                "headers": {k: v for k, v in response.headers.items()},
            }
    except Exception as ex:
        raise runtime_error(f"http request failed: {ex}", "E072")


def _process_capture(command: str, *args: str) -> dict[str, Any]:
    try:
        result = subprocess.run([str(command), *[str(a) for a in args]], capture_output=True, text=True, check=False)
        return {"status": result.returncode, "stdout": result.stdout, "stderr": result.stderr}
    except Exception as ex:
        raise runtime_error(f"process capture failed: {ex}", "E072")


def _test_assert(cond: Any, msg: Any) -> None:
    if not bool(cond):
        raise runtime_error(f"assertion failed: {msg if msg is not None else ''}", "E071")


def _test_eq(a: Any, b: Any, msg: Any, expect_equal: bool) -> None:
    same = INTERP.equal(a, b) if INTERP else a == b
    if same != expect_equal:
        relation = "equal to" if expect_equal else "not equal to"
        raise runtime_error(f"assertion failed: expected {a} {relation} {b} {msg if msg is not None else ''}", "E071")


def _test_null(v: Any, msg: Any) -> None:
    if v is not None:
        raise runtime_error(f"assertion failed: expected null but got {type_name_of(v)} {msg if msg is not None else ''}", "E071")


def builtin_method(obj: Any, name: str) -> Optional[NativeFunction]:
    """Intrinsic value-type methods. The signature table above is the type-level contract."""
    if isinstance(obj, (bool, ByteValue, int, float, CharValue, str, list, dict, StackValue)):
        if name == "string": return nf(f"{type_name_of(obj)}.string", lambda: solvik_string(obj))
        if name == "equals": return nf(f"{type_name_of(obj)}.equals", lambda other: _builtin_equal(obj, other))
    if isinstance(obj, (UserFunction, ClosureValue, BoundMethod, NativeFunction)):
        # Callable values are Stringable/Equatable like every other value.
        if name == "string": return nf("function.string", lambda: solvik_string(obj))
        if name == "equals": return nf("function.equals", lambda other: INTERP.equal(obj, other) if INTERP else obj is other)
    if isinstance(obj, (ByteValue, int, float)) and not isinstance(obj, bool):
        if name == "abs": return nf(f"{type_name_of(obj)}.abs", lambda: abs(numeric_value(obj)))
    if isinstance(obj, (ByteValue, int, float, CharValue, str)) and not isinstance(obj, bool):
        if name == "compare": return nf(f"{type_name_of(obj)}.compare", lambda other: _builtin_compare(obj, other))
        if name == "hash": return nf(f"{type_name_of(obj)}.hash", lambda: _stable_hash(obj))
    if isinstance(obj, bool) and name == "hash": return nf("bool.hash", lambda: 1 if obj else 0)
    if isinstance(obj, str) and not isinstance(obj, CharValue):
        methods = {
            "len": lambda: len(obj), "isEmpty": lambda: not obj,
            "byteLength": lambda: len(obj.encode("utf-8")),
            "charAt": lambda i: _char_at(obj, i), "substring": lambda a,b: _substring(obj,a,b),
            "contains": lambda s: s in obj, "startsWith": lambda s: obj.startswith(s),
            "endsWith": lambda s: obj.endswith(s), "indexOf": lambda s: obj.find(s),
            "toUpper": lambda: obj.upper(), "toLower": lambda: obj.lower(), "trim": lambda: obj.strip(),
            "split": lambda s: obj.split(s), "iterator": lambda: [CharValue(c) for c in obj],
        }
        if name in methods: return nf(f"string.{name}", methods[name])
    if isinstance(obj, list):
        methods = {
            "len": lambda: len(obj), "isEmpty": lambda: not obj,
            "contains": lambda v: any(_builtin_equal(x, v) for x in obj),
            "iterator": lambda: copy_value(obj),
            "map": lambda f: [copy_value(invoke_callable(f, [x])) for x in obj],
            "filter": lambda f: [copy_value(x) for x in obj if bool(invoke_callable(f, [x]))],
            "reduce": lambda f: _list_reduce(obj, f),
            "fold": lambda initial, f: _list_fold(obj, initial, f),
            "find": lambda f: next((copy_value(x) for x in obj if bool(invoke_callable(f, [x]))), None),
            "any": lambda f: any(bool(invoke_callable(f, [x])) for x in obj),
            "all": lambda f: all(bool(invoke_callable(f, [x])) for x in obj),
            "first": lambda: copy_value(obj[0]) if obj else None,
            "last": lambda: copy_value(obj[-1]) if obj else None,
            "reverse": lambda: [copy_value(x) for x in reversed(obj)],
            "sort": lambda f: _list_sort(obj, f),
        }
        if name in methods: return nf(f"list.{name}", methods[name])
    if isinstance(obj, dict):
        methods = {
            "len": lambda: len(obj), "isEmpty": lambda: not obj,
            "contains": lambda k: k in obj, "iterator": lambda: [copy_value(k) for k in obj.keys()],
        }
        if name in methods: return nf(f"map.{name}", methods[name])
    if isinstance(obj, StackValue):
        if name == "push": return nf("stack.push", lambda v: obj.items.append(copy_value(v)))
        if name == "pop": return nf("stack.pop", lambda: _stack_pop(obj))
        if name == "peek": return nf("stack.peek", lambda: _stack_peek(obj))
        if name == "len": return nf("stack.len", lambda: len(obj.items))
        if name == "isEmpty": return nf("stack.isEmpty", lambda: not obj.items)
        if name == "contains": return nf("stack.contains", lambda v: any(_builtin_equal(x, v) for x in obj.items))
        if name == "iterator": return nf("stack.iterator", lambda: copy_value(obj.items))
    if isinstance(obj, SolvikExceptionValue):
        if name == "message": return None
    return None


def _builtin_equal(a: Any, b: Any) -> bool:
    if isinstance(a, ByteValue): a = a.value
    if isinstance(b, ByteValue): b = b.value
    return a == b


def _builtin_compare(a: Any, b: Any) -> int:
    av = ord(a) if isinstance(a, CharValue) else numeric_value(a)
    bv = ord(b) if isinstance(b, CharValue) else numeric_value(b)
    if type(av) is not type(bv) and not (isinstance(av, (int, float)) and isinstance(bv, (int, float))):
        raise runtime_error(f"cannot compare {type_name_of(a)} and {type_name_of(b)}")
    return -1 if av < bv else (1 if av > bv else 0)


def _stable_hash(value: Any) -> int:
    data = (type_name_of(value) + ":" + solvik_string(value)).encode("utf-8")
    raw = hashlib.sha256(data).digest()[:8]
    return int.from_bytes(raw, "big", signed=False) & 0x7FFF_FFFF_FFFF_FFFF


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
        if isinstance(v, EnumValue):
            if v.payload:
                raise runtime_error(f"cannot convert payload enum value {v.enum_name}.{v.member_name} to int", "E066")
            return v.value
        if isinstance(v, CharValue): return ord(v)
        if isinstance(v, ByteValue): return v.value
        try:
            return int(v)
        except (ValueError, TypeError):
            raise runtime_error(f"cannot convert '{v}' to int", "E073")
    def to_float(v: Any) -> float:
        try:
            return float(numeric_value(v))
        except (ValueError, TypeError):
            raise runtime_error(f"cannot convert '{v}' to float", "E073")
    def to_byte(v: Any) -> ByteValue:
        try:
            n = int(float(v)) if isinstance(v, str) else int(numeric_value(v))
        except (ValueError, TypeError):
            raise runtime_error(f"cannot convert '{v}' to byte", "E073")
        if not 0 <= n <= 255: raise runtime_error("byte conversion out of range", "E073")
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
    core["string"] = CallableNamespace("string", solvik_string, {
        "join": nf("string.join", lambda xs, sep: sep.join(xs)),
        "convert": nf("string.convert", solvik_string),
        "repeat": nf("string.repeat", lambda s, n: str(s) * int(n)),
        "padStart": nf("string.padStart", lambda s, w, pad=" ": str(s).rjust(int(w), str(pad))),
        "padEnd": nf("string.padEnd", lambda s, w, pad=" ": str(s).ljust(int(w), str(pad))),
    })

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
            "list": nf("file.list", lambda p: [str(x.name) for x in pathlib.Path(p).iterdir()]),
            "mkdir": nf("file.mkdir", lambda p: pathlib.Path(p).mkdir(parents=True, exist_ok=True)),
            "isFile": nf("file.isFile", lambda p: pathlib.Path(p).is_file()),
            "isDir": nf("file.isDir", lambda p: pathlib.Path(p).is_dir()),
            "size": nf("file.size", lambda p: pathlib.Path(p).stat().st_size),
            "rename": nf("file.rename", lambda a, b: pathlib.Path(a).rename(b)),
            "remove": nf("file.remove", lambda p: pathlib.Path(p).unlink(missing_ok=True)),
        }),
        "process": Namespace("process", {
            "run": nf("process.run", _process_run),
            "capture": nf("process.capture", _process_capture),
            "args": nf("process.args", lambda: list(PROGRAM_ARGS)),
        }),
        "time": Namespace("time", {
            "now": nf("time.now", lambda: int(py_time.time()*1000)),
            "sleep": nf("time.sleep", lambda ms: py_time.sleep(ms/1000.0)),
            "iso": nf("time.iso", lambda ms: py_datetime.datetime.fromtimestamp(int(ms)/1000.0, tz=py_datetime.timezone.utc).isoformat().replace("+00:00", "Z")),
            "parse": nf("time.parse", lambda s: int(py_datetime.datetime.fromisoformat(str(s).replace("Z", "+00:00")).timestamp()*1000)),
        }),
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
        "json": Namespace("json", {
            "parse": nf("json.parse", _json_parse),
            "stringify": nf("json.stringify", _json_stringify),
        }),
        "http": Namespace("http", {
            "get": nf("http.get", lambda url: _http_request("GET", url, None, {})),
            "post": nf("http.post", lambda url, body: _http_request("POST", url, body, {})),
            "request": nf("http.request", lambda method, url, body, headers: _http_request(method, url, body, headers)),
        }),
        "test": Namespace("test", {
            "assert": nf("test.assert", lambda cond, msg="": _test_assert(cond, msg)),
            "assertTrue": nf("test.assertTrue", lambda v, msg="": _test_assert(v, msg)),
            "assertFalse": nf("test.assertFalse", lambda v, msg="": _test_assert(not bool(v), msg)),
            "assertEq": nf("test.assertEq", lambda a, b, msg="": _test_eq(a, b, msg, True)),
            "assertNe": nf("test.assertNe", lambda a, b, msg="": _test_eq(a, b, msg, False)),
            "assertNull": nf("test.assertNull", lambda v, msg="": _test_null(v, msg)),
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
        self.check_package_name(program, path)
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

    def check_package_name(self, program: Program, path: pathlib.Path) -> None:
        """A dependency package may not reuse a built-in namespace name (C121)."""
        builtin_namespaces = {"string", "math", "env", "file", "process", "time", "random", "path", "hash", "secrets", "base64"}
        if program.package in builtin_namespaces:
            raise DiagnosticError(
                "C121",
                SourcePos(str(path), 1, 1),
                f"package name '{program.package}' conflicts with a built-in namespace; choose a different name",
            )

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


def run_file(path: str, program_args: Optional[list[str]] = None) -> int:
    global PROGRAM_ARGS
    try:
        PROGRAM_ARGS = list(program_args or [])
        interp = Interpreter(); loader = Loader(interp); entry = loader.load_entry(path)
        return interp.run(entry.package)
    except RuntimeSignal as ex:
        code = f" [{ex.value.code}]" if ex.value.code else ""
        print(f"uncaught exception{code}: {ex.value.message}", file=sys.stderr)
        return 2
    except SolvikError as ex:
        print(str(ex), file=sys.stderr)
        return 1
    except OSError as ex:
        print(f"error: cannot read source file: {ex}", file=sys.stderr)
        return 1


def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Solvik semantic reference interpreter")
    ap.add_argument("file", nargs="?", help="Solvik source file")
    ap.add_argument("args", nargs="*", help="program arguments (available as process.args())")
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
    return run_file(args.file, args.args)


if __name__ == "__main__":
    raise SystemExit(main())
