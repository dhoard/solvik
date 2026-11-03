package dev.solvik.compiler.testsupport;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.load.ProgramLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a hand-parsed AST in the same world a real program sees: the packaged library is part of
 * every program, so its types, including the bundled I/O types, resolve without an import.
 *
 * <p>A test that only has an {@link Ast.Program} uses this instead of calling the checker directly,
 * which would otherwise check the program without the standard library.
 */
public final class Checking {

    private Checking() {
    }

    /** Checks {@code program} together with the packaged standard library. */
    public static TypeChecker.Result check(Ast.Program program, Diagnostics diagnostics) {
        return check(program, diagnostics, ClassLoader.getPlatformClassLoader());
    }

    /** Checks {@code program} together with the packaged standard library and a native loader. */
    public static TypeChecker.Result check(Ast.Program program, Diagnostics diagnostics,
            ClassLoader loader) {
        ProgramLoader.Loaded packaged = new ProgramLoader(diagnostics).loadStandardLibrary();
        return TypeChecker.check(merge(program, packaged.program()), diagnostics, loader,
                packaged.standardLibraryModules(), packaged.packagedTypes());
    }

    /** The packaged declarations first, then the program's, matching the loader's order. */
    private static Ast.Program merge(Ast.Program program, Ast.Program packaged) {
        List<Ast.TypeDecl> types = new ArrayList<>(packaged.types());
        types.addAll(program.types());
        List<Ast.FuncDecl> functions = new ArrayList<>(packaged.functions());
        functions.addAll(program.functions());
        List<Ast.ModuleDecl> modules = new ArrayList<>(packaged.modules());
        modules.addAll(program.modules());
        return new Ast.Program(types, functions, program.statements(), modules,
                program.includes(), program.aliases(), program.pos());
    }
}
