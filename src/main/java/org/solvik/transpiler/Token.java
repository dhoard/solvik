package org.solvik.transpiler;

public record Token(TokenKind kind, String text, Span span) {}
