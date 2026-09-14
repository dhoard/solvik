package org.solvik.transpiler.language;

/**
 * Backend-neutral language model shared by every compiler phase.
 *
 * <p>Operators and literal kinds are properties of the Solvik language itself,
 * not of any particular syntax tree or backend. The parser AST, the typed
 * Solvik IR, the optimizer, and every backend all depend on this model, which
 * keeps the IR and backend code from having to depend on parser AST types just
 * to reuse an enum.</p>
 */
public final class Language {
    private Language() {}

    /** Solvik binary operators; {@code CONCAT} covers {@code ..} and {@code ..=}. */
    public enum BinaryOp { ADD, SUB, MUL, DIV, MOD, CONCAT, AND, OR, EQ, NE, LT, LE, GT, GE }

    /** Solvik unary operators. */
    public enum UnaryOp { NEG, NOT }

    /** Kinds of source literals; the text is stored verbatim on the AST/IR node. */
    public enum LiteralKind { INT, REAL, STRING, CHAR, BOOLEAN, NULL }
}
