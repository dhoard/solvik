package dev.solvik.compiler.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.testsupport.PackagedLibrary;
import dev.solvik.compiler.transpile.GeneratedNames;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.Arguments;

/** Multi-file modules form one namespace; only nonconflicting declarations may be combined. */
class ModuleMergeTest {
    @TempDir
    Path directory;

    private void write(String name, String source) throws Exception {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private ProgramLoader.Loaded load(String entry) {
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).load(directory.resolve(entry));
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format(entry)).isEmpty();
        return loaded;
    }

    private Map<String, String> transpile(String entry) {
            return new SolvikCompiler(entry).transpile(load(entry));
    }

    private String failure(String entry) {
        return assertThrows(SolvikCompiler.CompilationFailure.class,
                () -> new SolvikCompiler(entry).transpile(load(entry))).getMessage();
    }

    /** Compile against an empty dependency classpath, then load only generated classes and the JDK. */
    private String run(Map<String, String> files, String output) throws Exception {
        Path root = directory.resolve(output);
        Path sources = Files.createDirectories(root.resolve("src"));
        Path classes = Files.createDirectories(root.resolve("classes"));
        Path empty = Files.createDirectories(root.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-classpath",
                empty.toString(), "-d", classes.toString()));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = sources.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(target.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME).getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void entry_and_two_imports_share_types_private_functions_and_nested_modules()
            throws Exception {
        write("left.sol", """
                module tools {
                    type Seed {
                        pub value := 0
                        pub func Seed(value: Integer) { self.value = value }
                    }
                    func base() -> Integer { return 40 }
                    pub type Left {
                        pub func Left() {}
                        pub func value() -> Integer { return fromRight() }
                    }
                    module detail { pub func alpha() -> Integer { return 2 } }
                }
                """);
        write("right.sol", """
                module tools {
                    func fromRight() -> Integer { return Seed(base()).value + 2 }
                    pub func answer() -> Integer { return Left().value() + tools::detail::beta() }
                    pub type Right {
                        pub func Right() {}
                        pub func value() -> Integer { return base() + 1 }
                    }
                    module detail { pub func beta() -> Integer { return alpha() + 1 } }
                    module detail {}
                }
                module tools {}
                """);
        for (boolean reverse : new boolean[]{false, true}) {
            String entry = reverse ? "reverse.sol" : "app.sol";
            write(entry, (reverse ? "import \"right.sol\"\nimport \"left.sol\"\n"
                    : "import \"left.sol\"\nimport \"right.sol\"\n") + """
                    module tools {
                        pub func sum() -> Integer { return answer() + Right().value() }
                    }
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("start") .. "")
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((tools::sum()) .. (" ") .. (tools::detail::beta()))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("end") .. "")
                    """);
            Map<String, String> files = transpile(entry);
            assertThat(PackagedLibrary.programFiles(files)).containsExactlyInAnyOrder("Solvik$Program.java",
                    "tools/Solvik$Functions.java", "tools/detail/Solvik$Functions.java",
                    "tools/Seed.java", "tools/Left.java", "tools/Right.java");
            assertThat(files.get("tools/Solvik$Functions.java"))
                    .contains("sum(", "base(", "fromRight(", "answer(");
            Map<String, String> repeated = transpile(entry);
            assertThat(repeated).isEqualTo(files);
            assertThat(new ArrayList<>(repeated.keySet())).containsExactlyElementsOf(files.keySet());
            assertThat(run(files, "run-" + reverse)).isEqualTo("start\n86 3\nend\n");
        }
    }

    @Test
    void repeated_and_diamond_imports_do_not_duplicate_merged_declarations()
            throws Exception {
        write("shared.sol", "module common { pub func seed() -> Integer { return 2 } }");
        write("left.sol", "import \"shared.sol\"\nmodule common {"
                + "pub func left() -> Integer { return seed() + 1 } }");
        write("right.sol", "import \"./shared.sol\"\nmodule common {"
                + "pub func right() -> Integer { return seed() + 2 } }");
        write("app.sol", """
                import "left.sol"
                import "right.sol"
                import "shared.sol"
                import "./shared.sol"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((common::left()) .. (" ") .. (common::right()) .. (" ") .. (common::seed()))
                """);
        assertThat(load("app.sol").programFileNames()).hasSize(4);
        Map<String, String> files = transpile("app.sol");
        assertThat(PackagedLibrary.programFiles(files)).containsExactlyInAnyOrder(
                "common/Solvik$Functions.java", "Solvik$Program.java");
        assertThat(run(files, "diamond")).isEqualTo("3 4 2\n");
    }

    @Test
    void equal_simple_names_in_different_full_namespaces_stay_separate() throws Exception {
        write("left.sol", """
                module first { module inner { pub func value() -> Integer { return 1 } } }
                module second { module inner { pub func other() -> Integer { return value() } } }
                """);
        write("right.sol", """
                module second { module inner { pub func value() -> Integer { return 2 } } }
                module first { module inner { pub func other() -> Integer { return value() } } }
                """);
        write("app.sol", """
                import "left.sol"
                import "right.sol"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((first::inner::other()) .. (" ") .. (second::inner::other()))
                """);
        assertThat(run(transpile("app.sol"), "separate")).isEqualTo("1 2\n");
    }

    static Stream<Arguments> conflicts() {
        String type = "type Entry { left := 1 }";
        String function = "func Entry() -> Integer { return 1 }";
        String module = "module Entry {}";
        return Stream.of(
                new String[]{type, type},
                new String[]{type, "pub type Entry { right := 2 }"},
                new String[]{function, function},
                new String[]{type, function},
                new String[]{type, module},
                new String[]{function, module}
        ).map(pair -> Arguments.of(pair[0], pair[1], false));
    }

    @ParameterizedTest
    @MethodSource("conflicts")
    void conflicting_members_are_not_overloads_partial_types_or_overrides(
            String first, String second, boolean reverse) throws Exception {
        write("left.sol", "module shared { " + first + " }");
        write("right.sol", "module shared { " + second + " }");
        write("app.sol", reverse ? "import \"right.sol\"\nimport \"left.sol\""
                : "import \"left.sol\"\nimport \"right.sol\"");
        assertThat(failure("app.sol")).contains("SOL-T001", "shared::Entry", "left.sol", "right.sol",
                "conflicts with", "declared at")
                .containsPattern("declared at [^\\r\\n]*\\.sol:\\d+:\\d+");
    }

    @Test
    void distinct_function_signatures_merge_as_overloads() throws Exception {
        write("left.sol", "module shared { pub func value() -> Integer { return 1 } }");
        write("right.sol", "module shared { pub func value(n: Integer) -> Integer { return n } }");
        write("app.sol", "import \"left.sol\"\nimport \"right.sol\"\n"
                + "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((shared::value()) .. (\" \") .. (shared::value(2)))");
        assertThat(run(transpile("app.sol"), "merged-overloads")).isEqualTo("1 2\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "type Entry {}\nfunc Entry() -> Integer { return 1 }",
            "module Entry {}\ntype Entry {}",
            "module Entry {}\nfunc Entry() -> Integer { return 1 }"
    })
    void single_source_namespaces_have_the_same_conflict_rules(String declarations) {
        for (boolean inModule : new boolean[]{false, true}) {
            String source = inModule ? "module shared {" + declarations + "}" : declarations;
            String message = assertThrows(SolvikCompiler.CompilationFailure.class,
                    () -> new SolvikCompiler("app.sol").transpile(source)).getMessage();
            assertThat(message).contains("SOL-T001", inModule ? "shared::Entry" : "Entry",
                    "declared at", "app.sol:");
        }
    }

    @Test
    void all_namespace_conflicts_are_collected_before_bodies_are_checked() throws Exception {
        write("left.sol", """
                module shared {
                    type A {}
                    type B {}
                    func broken() -> Integer { return "wrong" }
                }
                """);
        write("right.sol", "module shared { type A {} type B {} }");
        write("app.sol", "import \"left.sol\"\nimport \"right.sol\"");
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = load("app.sol");
        // The packaged modules are approved by provenance, so only the two files conflict here.
        TypeChecker.check(loaded.program(), diagnostics, ClassLoader.getPlatformClassLoader(),
                loaded.standardLibraryModules(), loaded.packagedTypes());
        assertThat(diagnostics.all()).hasSize(2).allSatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
            assertThat(diagnostic.message()).contains("conflicts with", "declared at", "left.sol:");
            assertThat(diagnostic.pos().file()).endsWith("right.sol");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((shared::secret()) .. \"\")",
            "hidden := shared::Hidden()",
            "module shared { module child { pub func value() -> Integer { return shared::secret() } } }"
    })
    void sharing_a_module_does_not_publish_its_private_members(String outside) throws Exception {
        write("left.sol", """
                module shared {
                    type Hidden { pub func Hidden() {} }
                    func secret() -> Integer { return 7 }
                }
                """);
        write("right.sol", "module shared { pub func value() -> Integer { return secret() } }");
        write("app.sol", "import \"left.sol\"\nimport \"right.sol\"\n" + outside);
        assertThat(failure("app.sol")).contains(TypeChecker.PRIVATE_DECLARATION);
    }

    @Test
    void public_signatures_cannot_expose_private_types_from_another_module_file() throws Exception {
        write("left.sol", "module shared { type Hidden { pub func Hidden() {} } }");
        write("right.sol", "module shared { pub func leak() -> Hidden { return Hidden() } }");
        write("app.sol", "import \"left.sol\"\nimport \"right.sol\"");
        assertThat(failure("app.sol")).contains(TypeChecker.PRIVATE_EXPOSURE, "shared::Hidden");
    }

    @Test
    void module_reopening_does_not_discover_unimported_files() throws Exception {
        write("left.sol", "module shared { pub func value() -> Integer { return 1 } }");
        write("unimported.sol", "module shared { pub func extra() -> Integer { return 2 } }");
        write("app.sol", "import \"left.sol\"\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((shared::extra()) .. \"\")");
        assertThat(load("app.sol").programFileNames()).hasSize(2);
        assertThat(failure("app.sol")).contains("shared", "extra", "declares no type or function");
    }
}
