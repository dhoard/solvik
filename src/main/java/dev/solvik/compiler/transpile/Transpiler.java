package dev.solvik.compiler.transpile;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.jir.JDecl;
import dev.solvik.compiler.jir.JavaSourceRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transpiles a checked Solvik program into standalone Java source files.
 *
 * <p>The pipeline is: checked AST to typed Java IR ({@link JavaLowerer}), support members selected
 * from the IR the program reaches ({@link GeneratedIntrinsics}), and deterministic source
 * rendering ({@link JavaSourceRenderer}). Follows {@code LANGUAGE.md} §11: one file per generated
 * class, named after the class, with the program's statements and functions in their own file.
 */
public final class Transpiler {

    /** Result of a successful transpilation: Java file name to file content. */
    public record Output(Map<String, String> files) {

        public String file(String name) {
            return files.get(name);
        }
    }

    /**
     * Lowers every Java file for {@code program}.
     *
     * <p>Each user-defined type becomes its own {@code <Type>.java} (§11, "one Java file per
     * Solvik type"), alongside the program class. No separate runtime classes are emitted.
     * Generated names contain {@code $}, so a type may be named {@code Solvik} or
     * {@code SolvikProgram} without colliding.
     */
    public Output transpile(Ast.Program program, TypeChecker.Result types, Diagnostics diagnostics) {
        return transpile(program, types, diagnostics, Set.of(), Set.of());
    }

    /**
     * Lowers every reached type, omitting a packaged type the program does not reach.
     *
     * <p>Packaged declarations are part of every program's namespace, but only a reached one is
     * emitted. That keeps a program that does no I/O free of the generated I/O adapter and its
     * exception support, while a type the program mentions pulls in what it references.
     *
     * @param packagedTypes       the declarations parsed from packaged resources, by identity
     * @param reachedPackagedNames the simple names of the packaged types the program reaches
     */
    public Output transpile(Ast.Program program, TypeChecker.Result types, Diagnostics diagnostics,
            Set<Ast.TypeDecl> packagedTypes, Set<String> reachedPackagedNames) {
        Map<String, Ast.TypeDecl> declared = qualifiedTypes(program);
        declared.entrySet().removeIf(entry -> packagedTypes.contains(entry.getValue())
                && !reachedPackagedNames.contains(entry.getValue().name()));
        JavaLowerer lowerer = new JavaLowerer(types, declared.keySet());
        JDecl.CompilationUnit programUnit = lowerer.lowerProgram(program);
        Map<String, JDecl.CompilationUnit> units = new LinkedHashMap<>();
        for (Map.Entry<String, List<Ast.FuncDecl>> entry : moduleFunctions(program).entrySet()) {
            units.put(GeneratedNames.moduleFunctionsPath(entry.getKey()),
                    lowerer.lowerModule(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Ast.TypeDecl> entry : declared.entrySet()) {
            units.put(GeneratedNames.sourcePath(entry.getKey()),
                    lowerer.lowerType(entry.getValue(), entry.getKey()));
        }
        // Support members live once in the program class, but every module and type file can call
        // them, so the reachable set is only known after every other unit has been lowered.
        JDecl.ClassDecl programClass = GeneratedIntrinsics.addSupport(programUnit.primary(),
                lowerer.usedIntrinsics(), concreteTypes(declared));
        JDecl.CompilationUnit completedProgram = new JDecl.CompilationUnit(
                programUnit.packageName(), List.of(programClass));

        JavaSourceRenderer renderer = new JavaSourceRenderer();
        Map<String, String> files = new LinkedHashMap<>();
        files.put(GeneratedNames.PROGRAM_CLASS + ".java", renderer.render(completedProgram));
        for (Map.Entry<String, JDecl.CompilationUnit> entry : units.entrySet()) {
            files.put(entry.getKey(), renderer.render(entry.getValue()));
        }
        if (lowerer.usesIo()) {
            // Emitted only when a bundled native binding was lowered: the adapter is generated
            // source, not an installed runtime, and its `$` name is not a Solvik identifier.
            files.put(GeneratedNames.IO_SOURCE_PATH, IoSupport.adapterSource());
        }
        // Insertion order is kept so generated file order is deterministic (§11, "Generated Java style").
        return new Output(Collections.unmodifiableMap(new LinkedHashMap<>(files)));
    }

    /** Every concrete declared type and its type-parameter count, in source order. */
    private static Map<String, Integer> concreteTypes(Map<String, Ast.TypeDecl> declared) {
        return declared.entrySet().stream()
                .filter(entry -> !entry.getValue().isTrait())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().typeParameters().size(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    /**
     * Every module's functions, merged across blocks of the same name (§2, "Modules").
     *
     * <p>Order follows first appearance, and functions keep their source order within the module.
     */
    private static Map<String, List<Ast.FuncDecl>> moduleFunctions(Ast.Program program) {
        Map<String, List<Ast.FuncDecl>> byModule = new LinkedHashMap<>();
        for (Ast.ModuleDecl module : program.modules()) {
            collectFunctions(module, "", byModule);
        }
        return byModule;
    }

    private static void collectFunctions(Ast.ModuleDecl module, String parent,
            Map<String, List<Ast.FuncDecl>> byModule) {
        String path = parent.isEmpty() ? module.name() : parent + "::" + module.name();
        if (!module.functions().isEmpty()) {
            byModule.computeIfAbsent(path, ignored -> new ArrayList<>())
                    .addAll(module.functions());
        }
        for (Ast.ModuleDecl nested : module.modules()) {
            collectFunctions(nested, path, byModule);
        }
    }

    /**
     * Each declared type keyed by its qualified name, in source order (§2, "Modules").
     *
     * <p>Generated file names come from this key, so a qualified type emits its own class rather
     * than overwriting a same-named type from another module.
     */
    private static Map<String, Ast.TypeDecl> qualifiedTypes(Ast.Program program) {
        Map<String, Ast.TypeDecl> types = new LinkedHashMap<>();
        for (Ast.TypeDecl type : program.types()) {
            types.put(type.name(), type);
        }
        for (Ast.ModuleDecl module : program.modules()) {
            collectTypes(module, "", types);
        }
        return types;
    }

    private static void collectTypes(Ast.ModuleDecl module, String parent,
            Map<String, Ast.TypeDecl> types) {
        String path = parent.isEmpty() ? module.name() : parent + "::" + module.name();
        for (Ast.TypeDecl type : module.types()) {
            types.put(path + "::" + type.name(), type);
        }
        for (Ast.ModuleDecl nested : module.modules()) {
            collectTypes(nested, path, types);
        }
    }
}
