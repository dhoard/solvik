package dev.solvik.compiler.jir;

import java.util.List;
import java.util.Objects;

/** A structured Java statement. */
public sealed interface JStmt {

    /** A sequence of statements rendered in their own block. */
    record Block(List<JStmt> statements) implements JStmt {
        public Block {
            statements = List.copyOf(statements);
        }
    }

    /** A local variable declaration. */
    record VarDecl(JType type, String name, boolean isFinal, JExpr initializer,
                   List<JDecl.Annotation> annotations) implements JStmt {
        public VarDecl {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            annotations = List.copyOf(annotations);
        }
    }

    /** An expression evaluated for its side effect. */
    record ExpressionStatement(JExpr expression) implements JStmt {
        public ExpressionStatement {
            Objects.requireNonNull(expression, "expression");
        }
    }

    /** An {@code if} statement with an optional {@code else} branch. */
    record If(JExpr condition, JStmt thenBranch, JStmt elseBranch) implements JStmt {
        public If {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(thenBranch, "then branch");
        }
    }

    /**
     * A {@code while} loop.
     *
     * @param label the Java label, or {@code null} for an unlabeled loop
     */
    record While(String label, JExpr condition, JStmt body) implements JStmt {
        public While {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(body, "body");
        }
    }

    /**
     * An enhanced {@code for} loop over an iterable or array.
     *
     * @param label the Java label, or {@code null} for an unlabeled loop
     */
    record ForEach(String label, JType elementType, String name, JExpr iterable, JStmt body)
            implements JStmt {
        public ForEach {
            Objects.requireNonNull(elementType, "element type");
            Objects.requireNonNull(name, "loop variable");
            Objects.requireNonNull(iterable, "iterable");
            Objects.requireNonNull(body, "body");
        }
    }

    /**
     * A counted {@code for} loop.
     *
     * @param label  the Java label, or {@code null} for an unlabeled loop
     * @param init   the initialization statements
     * @param update the update statements
     */
    record For(String label, List<JStmt> init, JExpr condition, List<JStmt> update, JStmt body)
            implements JStmt {
        public For {
            init = List.copyOf(init);
            Objects.requireNonNull(condition, "condition");
            update = List.copyOf(update);
            Objects.requireNonNull(body, "body");
        }
    }

    /**
     * One arrow arm of a switch statement.
     *
     * @param label       the constant label expression, or {@code null} for a pattern arm
     * @param patternType the pattern type, or {@code null} for a constant arm
     * @param patternName the pattern variable, or {@code null} for a constant arm
     * @param body        the arm's statements
     */
    record SwitchArm(JExpr label, JType patternType, String patternName, List<JStmt> body) {
        public SwitchArm {
            if (label == null && patternType == null) {
                throw new IllegalArgumentException("a switch arm needs a label or a pattern type");
            }
            body = List.copyOf(body);
        }

        public static SwitchArm of(JExpr label, List<JStmt> body) {
            return new SwitchArm(label, null, null, body);
        }
    }

    /**
     * A switch statement with arrow arms and an optional default arm.
     *
     * <p>Every arm has exactly one body, which is how Solvik's no-fall-through rule is preserved
     * structurally.
     */
    record Switch(JExpr selector, List<SwitchArm> arms, JStmt defaultBody) implements JStmt {
        public Switch {
            Objects.requireNonNull(selector, "selector");
            arms = List.copyOf(arms);
        }
    }

    /** A {@code return}; the value is {@code null} for a bare return. */
    record Return(JExpr value) implements JStmt {
    }

    /** A {@code break} of an enclosing loop, optionally by label. */
    record Break(String label) implements JStmt {
    }

    /** A {@code continue} of an enclosing loop, optionally by label. */
    record Continue(String label) implements JStmt {
    }

    /** An explicit {@code super(...)} invocation, which must open a constructor body. */
    record SuperCall(List<JExpr> arguments) implements JStmt {
        public SuperCall {
            arguments = List.copyOf(arguments);
        }
    }

    /** A {@code throw}. */
    record Throw(JExpr exception) implements JStmt {
        public Throw {
            Objects.requireNonNull(exception, "exception");
        }
    }

    /**
     * A {@code try} statement with at most one {@code catch} and an optional {@code finally}.
     *
     * <p>At least one of the two clauses is present, because a {@code try} with neither guards
     * nothing. The {@code finally} body runs on every way out of the {@code try} body: normal
     * completion, {@code throw}, {@code return}, and {@code break}/{@code continue} (§7.4).
     *
     * @param body        the guarded statements
     * @param catches     the {@code catch} clauses, possibly empty when a {@code finally} is present
     * @param finallyBody the {@code finally} body, or {@code null} when absent
     */
    record Try(JStmt body, List<Catch> catches, JStmt finallyBody) implements JStmt {
        public Try {
            Objects.requireNonNull(body, "try body");
            catches = List.copyOf(catches);
            if (catches.isEmpty() && finallyBody == null) {
                throw new IllegalArgumentException(
                        "a try statement needs a catch or a finally clause");
            }
        }

        /** A {@code try}/{@code catch} without a {@code finally}, such as a native call wrapper. */
        public Try(JStmt body, List<Catch> catches) {
            this(body, catches, null);
        }
    }

    /** One {@code catch} clause. */
    record Catch(JType type, String name, JStmt body) {
        public Catch {
            Objects.requireNonNull(type, "catch type");
            Objects.requireNonNull(name, "catch variable");
            Objects.requireNonNull(body, "catch body");
        }
    }

    /**
     * A {@code synchronized} statement: holds {@code monitor}'s Java monitor for the whole body.
     *
     * <p>The release is the Java statement's own behavior on every way out — normal completion,
     * {@code return}, {@code break}, {@code continue}, and {@code throw} — so no support member and
     * no generated {@code finally} takes part in it. Java monitors are reentrant, so a nested
     * statement on the same value runs instead of deadlocking (§7.5).
     */
    record Synchronized(JExpr monitor, JStmt body) implements JStmt {
        public Synchronized {
            Objects.requireNonNull(monitor, "monitor");
            Objects.requireNonNull(body, "body");
        }
    }
}
