package dev.solvik.compiler;

/**
 * The Java release targeted by both the compiler build and the programs it generates.
 *
 * <p>Generated Solvik sources are passed to {@code javac} with {@code --release}, and the typed Java IR
 * backend renders class files for the same feature level, so the two must agree. Keep
 * {@link #RELEASE} in step with {@code maven.compiler.release} in {@code pom.xml}, which compiles
 * the compiler itself.
 */
public final class JavaTarget {

    /** The Java feature release generated programs are compiled for. */
    public static final int RELEASE = 25;

    /** {@link #RELEASE} as the argument of a {@code javac} {@code --release} option. */
    public static final String RELEASE_OPTION = Integer.toString(RELEASE);

    private JavaTarget() {
    }
}
