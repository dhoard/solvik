package org.solvik.transpiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable Solvik-to-Java compilation service.
 *
 * <p>{@link SolvikTranspiler} remains a thin adapter that maps this service's
 * outcome onto the documented process exit codes; the service itself never
 * terminates the JVM, so tests can compile source text and inspect the result
 * directly.</p>
 */
public final class Transpiler {
    /** Receives one named phase timing in nanoseconds. */
    @FunctionalInterface
    public interface PhaseListener {
        void phase(String name, long nanos);
    }

    private Transpiler() {}

    /** Compiles Solvik source text to a package-free Java 17 source string. */
    public static String toJava(String fileName, String source, String className) throws CompileException {
        return toJava(fileName, source, className, null);
    }

    /** Compiles Solvik source text, optionally reporting per-phase timings. */
    public static String toJava(String fileName, String source, String className, PhaseListener listener)
            throws CompileException {
        long start = System.nanoTime();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Lexer lexer = new Lexer(fileName, source);
        List<Token> tokens = lexer.tokenize();
        diagnostics.addAll(lexer.diagnostics());
        long afterLexer = System.nanoTime();
        Parser parser = new Parser(tokens);
        Ast.CompilationUnit unit = parser.parse(fileName);
        diagnostics.addAll(parser.diagnostics());
        long afterParser = System.nanoTime();
        if (!diagnostics.isEmpty()) throw new CompileException(diagnostics);
        SemanticAnalyzer.Model model = new SemanticAnalyzer().analyze(unit);
        long afterSemantic = System.nanoTime();
        String javaSource = new JavaEmitter(model, className, fileName).emit();
        long afterEmit = System.nanoTime();
        if (listener != null) {
            listener.phase("lexer", afterLexer - start);
            listener.phase("parser", afterParser - afterLexer);
            listener.phase("semantic", afterSemantic - afterParser);
            listener.phase("java emit", afterEmit - afterSemantic);
            listener.phase("total", afterEmit - start);
        }
        return javaSource;
    }

    /** Writes {@code className.java} into {@code directory}, replacing atomically when possible. */
    public static Path writeJavaSource(Path directory, String className, String javaSource) throws IOException {
        Path target = directory.resolve(className + ".java");
        Path temporary = Files.createTempFile(directory, "." + className + ".", ".tmp");
        try {
            Files.writeString(temporary, javaSource, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
