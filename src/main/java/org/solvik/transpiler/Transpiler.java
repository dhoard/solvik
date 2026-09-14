package org.solvik.transpiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.solvik.transpiler.backend.JavaEmitter;
import org.solvik.transpiler.backend.JavaProgram;

/**
 * Reusable Solvik-to-Java compilation service and explicit phase pipeline.
 *
 * <p>The service orchestrates the compiler phases and owns no language or
 * backend semantics of its own:</p>
 *
 * <pre>
 * source -&gt; Lexer -&gt; Parser -&gt; AST -&gt; SemanticAnalyzer -&gt; SolvikLowerer
 *        -&gt; typed Solvik IR -&gt; IrOptimizer -&gt; JavaLowerer/JavaProgram
 *        -&gt; JavaEmitter -&gt; Java 17 source
 * </pre>
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
        SolvikProgram solvikProgram = new SolvikLowerer(model).lower();
        long afterLower = System.nanoTime();
        SolvikProgram optimized = new IrOptimizer().optimize(solvikProgram);
        long afterOptimize = System.nanoTime();
        JavaProgram javaProgram = new JavaProgram(optimized, className, fileName);
        long afterJavaLower = System.nanoTime();
        String javaSource = new JavaEmitter(javaProgram).emit();
        long afterEmit = System.nanoTime();
        if (listener != null) {
            listener.phase("lexer", afterLexer - start);
            listener.phase("parser", afterParser - afterLexer);
            listener.phase("semantic", afterSemantic - afterParser);
            listener.phase("solvik lowering", afterLower - afterSemantic);
            listener.phase("ir optimization", afterOptimize - afterLower);
            listener.phase("java lowering", afterJavaLower - afterOptimize);
            listener.phase("java emission", afterEmit - afterJavaLower);
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
