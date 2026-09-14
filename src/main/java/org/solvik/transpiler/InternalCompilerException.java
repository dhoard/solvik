package org.solvik.transpiler;

/**
 * Signals a broken compiler invariant rather than a mistake in the Solvik
 * source program.
 *
 * <p>It is thrown only for states that semantic analysis should have made
 * impossible, such as an unresolved typed-IR node or an unhandled IR variant.
 * The CLI reports it distinctly from a source diagnostic and exits with the
 * internal-error code so that a compiler bug is never misreported as a user
 * error.</p>
 */
public final class InternalCompilerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InternalCompilerException(String message) {
        super(message);
    }

    public InternalCompilerException(String message, Throwable cause) {
        super(message, cause);
    }
}
