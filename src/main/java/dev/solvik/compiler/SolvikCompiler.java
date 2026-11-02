package dev.solvik.compiler;

import dev.solvik.compiler.bind.JavaClasspath;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.diagnostic.SolvikException;
import dev.solvik.compiler.load.ProgramLoader;
import dev.solvik.compiler.report.DiagnosticReporter;
import dev.solvik.compiler.transpile.Transpiler;
import java.util.Map;

/**
 * Front door for a single compilation: source text in, Java files out.
 *
 * <p>The pipeline is layered as the specification requires (§11): lexing, parsing, name resolution
 * and type checking, then Java generation. Each phase gates the next, so a failure in one produces
 * diagnostics and nothing further.
 */
public final class SolvikCompiler {

    private final String fileName;
    private final String displayName;
    private final JavaClasspath classpath;

    public SolvikCompiler(String fileName) { this(fileName, JavaClasspath.empty()); }

    public SolvikCompiler(String fileName, JavaClasspath classpath) {
        this.classpath = classpath;
        this.fileName = fileName;
        this.displayName = DiagnosticReporter.displayName(fileName);
    }

    /**
     * Transpiles Solvik source into Java source files keyed by file name.
     *
     * @throws CompilationFailure when any phase reported an error
     */
    public Map<String, String> transpile(String source) {
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).loadSource(source, fileName);
        if (diagnostics.hasErrors()) throw new CompilationFailure(new DiagnosticReporter()
                .report(loaded.sources(), displayName, diagnostics.all()), null);
        return transpile(loaded);
    }

    /**
     * Transpiles an already-loaded program: every file's declarations, the entry file's statements.
     *
     * @throws CompilationFailure when any phase reported an error, rendered against the file each
     *                           diagnostic came from
     */
    public Map<String, String> transpile(ProgramLoader.Loaded loaded) {
        Diagnostics diagnostics = new Diagnostics();
        try (var loader = classpath.openLoader()) {
            TypeChecker.Result types = TypeChecker.check(loaded.program(), diagnostics, loader,
                    loaded.standardLibraryModules(), loaded.packagedTypes());
            diagnostics.throwIfErrors();
            return new Transpiler().transpile(loaded.program(), types, diagnostics,
                    loaded.packagedTypes(), loaded.reachedPackagedTypeNames()).files();
        } catch (SolvikException failure) {
            throw new CompilationFailure(render(failure, loaded), failure);
        } catch (java.io.IOException failure) {
            throw new CompilationFailure("cannot open native classpath: " + failure.getMessage(), failure);
        }
    }

    private String render(SolvikException failure, ProgramLoader.Loaded loaded) {
        return new DiagnosticReporter().report(loaded.sources(), displayName,
                failure.diagnostics());
    }

    /** Thrown when the source does not compile; the message is the rendered diagnostics. */
    public static final class CompilationFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public CompilationFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
