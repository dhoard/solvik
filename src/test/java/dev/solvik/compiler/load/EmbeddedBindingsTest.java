package dev.solvik.compiler.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The packaged library contract (LANGUAGE.md §11): every {@code .sol} file the compiler packages is
 * part of every program, loaded before the program's own files, and an import names a file.
 */
class EmbeddedBindingsTest {

    /** The collection bindings, in the sorted path order the packaged scan uses. */
    private static final List<String> RESOURCES = List.of(
            "solvik/collections/List.sol", "solvik/collections/Map.sol", "solvik/collections/Stack.sol");
    private static final String UUID_RESOURCE = "solvik/util/UUID.sol";
    private static final String TASK_RESOURCE = "solvik/concurrency/Task.sol";
    private static final String THREAD_RESOURCE = "solvik/concurrency/Thread.sol";
    private static final List<String> IO_RESOURCES = List.of(
            "solvik/io/ByteReader.sol", "solvik/io/ByteWriter.sol", "solvik/io/Channel.sol",
            "solvik/io/File.sol", "solvik/io/IoError.sol", "solvik/io/Stdio.sol",
            "solvik/io/TextReader.sol", "solvik/io/TextWriter.sol");

    @Test
    @DisplayName("each collection resource declares one native class binding")
    void each_resource_declares_one_native_class_binding() throws Exception {
        List<String> names = List.of("List", "Map", "Stack");
        List<String> targets = List.of("java.util.ArrayList", "java.util.HashMap", "java.util.Stack");
        for (int i = 0; i < RESOURCES.size(); i++) {
            String resource = RESOURCES.get(i);
            try (var stream = getClass().getResourceAsStream("/" + resource)) {
                assertThat(stream).describedAs(resource).isNotNull();
                String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                Diagnostics diagnostics = new Diagnostics();
                Ast.Program program = new Parser(new Lexer(source, diagnostics, resource).tokenize(),
                        diagnostics).parseProgram();
                assertThat(diagnostics.all()).isEmpty();
                assertThat(program.imports()).isEmpty();
                assertThat(program.statements()).isEmpty();
                assertThat(program.types()).isEmpty();
                assertThat(program.functions()).isEmpty();
                assertThat(program.modules()).hasSize(1);
                Ast.ModuleDecl root = program.modules().getFirst();
                assertThat(root.name()).isEqualTo("solvik");
                assertThat(root.types()).isEmpty();
                assertThat(root.functions()).isEmpty();
                assertThat(root.modules()).hasSize(1);
                Ast.ModuleDecl collections = root.modules().getFirst();
                assertThat(collections.name()).isEqualTo("collections");
                assertThat(collections.functions()).isEmpty();
                assertThat(collections.modules()).isEmpty();
                assertThat(collections.types()).hasSize(1);
                Ast.TypeDecl binding = collections.types().getFirst();
                assertThat(binding.name()).isEqualTo(names.get(i));
                assertThat(binding.nativeClass()).isEqualTo(targets.get(i));
                assertThat(binding.isPublic()).isTrue();
                assertThat(binding.pos().file()).isEqualTo(resource);
            }
        }
        assertThat(getClass().getResource("/stdlib/java.sol")).isNull();
        for (String name : names) {
            assertThat(getClass().getResource("/java/collections/" + name + ".sol")).isNull();
        }
    }

    @Test
    void concurrency_resources_declare_the_exact_public_surface() throws Exception {
        Ast.TypeDecl task = concurrencyType(TASK_RESOURCE);
        assertThat(task.name()).isEqualTo("Task");
        assertThat(task.isTrait()).isTrue();
        assertThat(task.nativeClass()).isEqualTo("java.lang.Runnable");
        assertThat(task.methods()).extracting(Ast.FuncDecl::name).containsExactly("run");

        Ast.TypeDecl thread = concurrencyType(THREAD_RESOURCE);
        assertThat(thread.name()).isEqualTo("Thread");
        assertThat(thread.isTrait()).isFalse();
        assertThat(thread.nativeClass()).isEqualTo("java.lang.Thread");
        assertThat(thread.methods()).extracting(Ast.FuncDecl::name)
                .containsExactly("start", "await", "await", "interrupt", "isAlive",
                        "isInterrupted");
        assertThat(thread.methods()).noneMatch(method -> method.name().equals("isVirtual"));
        assertThat(thread.methods().stream().filter(method -> method.name().equals("await"))
                .map(method -> method.params().size()).toList()).containsExactly(0, 1);
    }

    private Ast.TypeDecl concurrencyType(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + resource)) {
            assertThat(stream).describedAs(resource).isNotNull();
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Diagnostics diagnostics = new Diagnostics();
            Ast.Program program = new Parser(new Lexer(source, diagnostics, resource).tokenize(),
                    diagnostics).parseProgram();
            assertThat(diagnostics.all()).describedAs("%s", diagnostics.format(resource)).isEmpty();
            Ast.ModuleDecl root = program.modules().getFirst();
            assertThat(root.name()).isEqualTo("solvik");
            Ast.ModuleDecl concurrency = root.modules().getFirst();
            assertThat(concurrency.name()).isEqualTo("concurrency");
            assertThat(concurrency.types()).hasSize(1);
            return concurrency.types().getFirst();
        }
    }

    @Test
    @DisplayName("the UUID resource is declaration-only solvik::util, written in Solvik")
    void the_uuid_resource_declares_its_own_namespace() throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + UUID_RESOURCE)) {
            assertThat(stream).describedAs(UUID_RESOURCE).isNotNull();
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Diagnostics diagnostics = new Diagnostics();
            Ast.Program program = new Parser(new Lexer(source, diagnostics, UUID_RESOURCE).tokenize(),
                    diagnostics).parseProgram();
            assertThat(diagnostics.all()).describedAs("%s", diagnostics.format(UUID_RESOURCE)).isEmpty();
            assertThat(program.imports()).isEmpty();
            assertThat(program.statements()).isEmpty();
            assertThat(program.types()).isEmpty();
            assertThat(program.functions()).isEmpty();
            assertThat(program.modules()).hasSize(1);
            Ast.ModuleDecl root = program.modules().getFirst();
            assertThat(root.name()).isEqualTo("solvik");
            assertThat(root.types()).isEmpty();
            assertThat(root.functions()).isEmpty();
            assertThat(root.modules()).hasSize(1);
            Ast.ModuleDecl util = root.modules().getFirst();
            assertThat(util.name()).isEqualTo("util");
            assertThat(util.functions()).isEmpty();
            assertThat(util.modules()).isEmpty();
            assertThat(util.types()).extracting(Ast.TypeDecl::name)
                    .containsExactly("Hex", "Clock", "Random", "UUID");
            assertThat(util.types().getLast().isPublic()).isTrue();
            assertThat(util.types().subList(0, 3).stream().allMatch(Ast.TypeDecl::isPublic)).isFalse();
            assertThat(util.types().getLast().pos().file()).isEqualTo(UUID_RESOURCE);
        }
    }

    @Test
    @DisplayName("packaged types are used with no import at all")
    void packaged_types_need_no_import() {
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).loadSource("""
                mut id: solvik::util::UUID? = solvik::util::UUID.parse("0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b")
                scores := solvik::collections::Map<String, Integer>()
                if id != Null {
                    scores.put("ann", id.toString().size())
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((scores.get("ann")) .. (" ") .. (id.timestamp()))
                }
                """, "t.sol");
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("t.sol")).isEmpty();
        assertThat(loaded.fileNames()).endsWith("t.sol");
        assertThat(new SolvikCompiler("t.sol").transpile(loaded)).containsKeys(
                        "solvik/collections/Map.java", "solvik/util/UUID.java",
                        "Solvik$Program.java")
                // Only a reached packaged type is emitted; the program never mentions these.
                .doesNotContainKeys("solvik/collections/Stack.java",
                        "solvik/collections/List.java");
    }

    @Test
    @DisplayName("the packaged library loads before the program's own files")
    void packaged_resources_load_before_program_files() {
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).loadSource("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n", "t.sol");
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("t.sol")).isEmpty();
        assertThat(loaded.fileNames()).startsWith(RESOURCES.toArray(String[]::new)).endsWith("t.sol");
        assertThat(loaded.fileNames()).contains(UUID_RESOURCE);
        assertThat(loaded.sources().keySet()).containsExactlyElementsOf(loaded.fileNames());
        assertThat(loaded.standardLibraryModules()).isNotEmpty();
    }

    @Test
    @DisplayName("every .sol file under the resource tree is packaged, with nothing registered")
    void the_packaged_set_is_the_resource_tree_itself() throws Exception {
        Path root = Path.of("src/main/resources").toAbsolutePath().normalize();
        assertThat(root).as("resource tree, resolved from the project root Maven runs in")
                .isDirectory();
        List<String> onDisk;
        try (Stream<Path> files = Files.walk(root)) {
            onDisk = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".sol"))
                    .map(root::relativize)
                    .map(file -> file.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        assertThat(onDisk).isNotEmpty().contains(UUID_RESOURCE);
        ProgramLoader.Loaded loaded = new ProgramLoader(new Diagnostics())
                .loadSource("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n", "t.sol");
        assertThat(loaded.fileNames()).containsExactlyElementsOf(
                Stream.concat(onDisk.stream(), Stream.of("t.sol")).toList());
    }

    @Test
    @DisplayName("an import names a file: a packaged name is not one, and neither is a resource path")
    void an_import_is_a_file_path() {
        for (String written : List.of("solvik::collections::Map", "solvik/collections/Map.sol",
                "solvik:collections:Map", "java:collections", "not/registered.sol")) {
            Diagnostics diagnostics = new Diagnostics();
            ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics)
                    .loadSource("import \"" + written + "\"\n", "t.sol");
            assertThat(diagnostics.all()).describedAs(written).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo(ProgramLoader.MISSING_FILE);
                assertThat(diagnostic.message()).contains(written);
                assertThat(diagnostic.pos().file()).isEqualTo("t.sol");
            });
            assertThat(loaded.fileNames()).endsWith("t.sol");
        }
    }

    @Test
    @DisplayName("a packaged resource cannot be reached as a file, even by its own path")
    void a_local_file_cannot_reopen_a_packaged_namespace(@TempDir Path directory) throws Exception {
        Path local = directory.resolve("solvik/collections/Map.sol");
        Files.createDirectories(local.getParent());
        Files.writeString(local, "module solvik { module collections { pub type Map { "
                + "pub func Map() { } } } }\n");
        Path entry = directory.resolve("app.sol");
        Files.writeString(entry, "import \"solvik/collections/Map.sol\"\n");
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).load(entry);
        assertThat(diagnostics.all()).isEmpty();
        assertThat(loaded.programFileNames()).containsExactly(entry.toString(), local.toString());
        assertThatThrownBy(() -> new SolvikCompiler("app.sol").transpile(loaded))
                .isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("a packaged resource is loaded once and keyed by its resource path")
    void packaged_sources_are_keyed_by_resource_path() {
        ProgramLoader.Loaded loaded = new ProgramLoader(new Diagnostics())
                .loadSource("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n", "t.sol");
        assertThat(loaded.sources().get(RESOURCES.get(1))).contains("pub type Map");
        assertThat(loaded.fingerprint()).contains(RESOURCES.get(1) + '\u0000'
                + loaded.sources().get(RESOURCES.get(1)) + '\u0000');
        assertThat(new ProgramLoader(new Diagnostics()).loadSource("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n", "t.sol")
                .fingerprint()).isEqualTo(loaded.fingerprint());
    }

    @Test
    @DisplayName("a packaged type is still named by its full path: nothing brings short names in")
    void packaged_types_stay_qualified() {
        for (String written : List.of("Map()", "collections::Map()", "util::UUID()",
                "UUID()", "Map<String, Integer>()", "TextWriter(Stdio.stdout())",
                "Channel.bytes()", "io::TextWriter(io::Stdio.stdout())")) {
            assertThatThrownBy(() -> new SolvikCompiler("t.sol").transpile(
                    "value := " + written + "\n"))
                    .describedAs(written)
                    .isInstanceOf(SolvikCompiler.CompilationFailure.class);
        }
        assertThat(new SolvikCompiler("t.sol").transpile(
                "value := solvik::collections::Map<String, Integer>()\n"))
                .containsKey("solvik/collections/Map.java");
        assertThat(new SolvikCompiler("t.sol").transpile(
                "out := solvik::io::TextWriter(solvik::io::Stdio.stdout())\n"))
                .containsKeys("solvik/io/TextWriter.java", "solvik/io/Stdio.java",
                        "solvik/io/Channel.java");
    }

    @Test
    @DisplayName("the I/O resources declare public types in solvik::io")
    void io_resources_declare_public_types_in_solvik_io() throws Exception {
        for (String resource : IO_RESOURCES) {
            try (var stream = getClass().getResourceAsStream("/" + resource)) {
                assertThat(stream).describedAs(resource).isNotNull();
                String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                Diagnostics diagnostics = new Diagnostics();
                Ast.Program program = new Parser(new Lexer(source, diagnostics, resource).tokenize(),
                        diagnostics).parseProgram();
                assertThat(diagnostics.all()).describedAs("%s", diagnostics.format(resource))
                        .isEmpty();
                assertThat(program.types()).as(resource).isEmpty();
                assertThat(program.modules()).as(resource).hasSize(1);
                Ast.ModuleDecl root = program.modules().getFirst();
                assertThat(root.name()).as(resource).isEqualTo("solvik");
                assertThat(root.modules()).as(resource).hasSize(1);
                Ast.ModuleDecl io = root.modules().getFirst();
                assertThat(io.name()).as(resource).isEqualTo("io");
                assertThat(io.modules()).as(resource).isEmpty();
                assertThat(io.types()).as(resource).hasSize(1);
                assertThat(io.types().getFirst().isPublic()).as(resource).isTrue();
            }
        }
    }

    @Test
    void user_code_cannot_reopen_the_standard_library_even_with_a_bundled_looking_filename() {
        String source = """
                module solvik {
                    module collections {
                        pub type Fake { pub func Fake() { } }
                    }
                }
                """;
        for (String name : List.of("t.sol", "solvik/collections/Map.sol")) {
            assertThatThrownBy(() -> new SolvikCompiler(name).transpile(source))
                    .isInstanceOf(SolvikCompiler.CompilationFailure.class)
                    .hasMessageContaining(TypeChecker.TYPE_ERROR).hasMessageContaining("reserved");
        }
    }

    @Test
    void imported_user_files_cannot_reopen_the_standard_library(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("fake.sol"), "module solvik { module collections { } }\n");
        Path entry = directory.resolve("app.sol");
        Files.writeString(entry, "import \"fake.sol\"\n");
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics).load(entry);
        assertThat(diagnostics.all()).isEmpty();
        assertThatThrownBy(() -> new SolvikCompiler("app.sol").transpile(loaded))
                .isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining("reserved").hasMessageContaining("fake.sol");
    }

    @Test
    void standalone_checking_does_not_trust_source_names() throws Exception {
        String source;
        try (var stream = getClass().getResourceAsStream("/" + RESOURCES.getFirst())) {
            assertThat(stream).isNotNull();
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics, RESOURCES.getFirst()).tokenize(),
                diagnostics).parseProgram();
        TypeChecker.check(program, diagnostics);
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
            assertThat(diagnostic.message()).contains("reserved");
        });
    }

    @Test
    void bundled_provenance_is_immutable_and_compares_module_identities() {
        ProgramLoader.Loaded loaded = new ProgramLoader(new Diagnostics())
                .loadSource("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n", "t.sol");
        Ast.ModuleDecl root = loaded.program().modules().getFirst();
        Ast.ModuleDecl copy = new Ast.ModuleDecl(root.name(), root.types(), root.functions(),
                root.modules(), root.pos());
        assertThat(copy).isEqualTo(root).isNotSameAs(root);
        assertThat(loaded.standardLibraryModules().contains(root)).isTrue();
        assertThat(loaded.standardLibraryModules().contains(copy)).isFalse();
        assertThatThrownBy(() -> loaded.standardLibraryModules().add(copy))
                .isInstanceOf(UnsupportedOperationException.class);

        Ast.Program program = loaded.program();
        Ast.Program forged = new Ast.Program(program.types(), program.functions(), program.statements(),
                List.of(copy), program.imports(), program.pos());
        Diagnostics diagnostics = new Diagnostics();
        TypeChecker.check(forged, diagnostics, ClassLoader.getPlatformClassLoader(),
                loaded.standardLibraryModules(), loaded.packagedTypes());
        assertThat(diagnostics.all()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(TypeChecker.TYPE_ERROR);
            assertThat(diagnostic.message()).contains("reserved");
        });
    }

    @Test
    @DisplayName("a resource the compiler lists but cannot read is a compiler failure, not a fallback")
    void a_missing_packaged_resource_is_not_falled_back_on() throws Exception {
        Throwable thrown = catchThrowable(
                () -> loadWithReplacedResource(RESOURCES.getFirst(), null));
        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        assertThat(thrown.getCause()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RESOURCES.getFirst());
    }

    @Test
    void packaged_resources_remain_declaration_only() throws Exception {
        for (String source : List.of("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n", "import \"solvik/collections/Map.sol\"\n")) {
            assertThat(loadWithReplacedResource(RESOURCES.getFirst(), source))
                    .contains(ProgramLoader.STATEMENTS_IN_IMPORT, RESOURCES.getFirst());
        }
    }

    /** Loads {@code source} through a fresh {@link ProgramLoader} living in {@code loader}. */
    private static String loadProgram(ClassLoader loader, String source) throws Exception {
        Class<?> diagnosticsClass = loader.loadClass(Diagnostics.class.getName());
        Object diagnostics = diagnosticsClass.getConstructor().newInstance();
        Class<?> programLoaderClass = loader.loadClass(ProgramLoader.class.getName());
        Object programLoader = programLoaderClass.getConstructor(diagnosticsClass).newInstance(diagnostics);
        programLoaderClass.getMethod("loadSource", String.class, String.class)
                .invoke(programLoader, source, "t.sol");
        return (String) diagnosticsClass.getMethod("format", String.class).invoke(diagnostics, "t.sol");
    }

    /**
     * Runs the loader with one packaged resource replaced by {@code source}, or absent when it is
     * {@code null}, while the compiler still lists the resource in its own tree.
     */
    private String loadWithReplacedResource(String resource, String source) throws Exception {
        URL classes = ProgramLoader.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes},
                ClassLoader.getPlatformClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (name.equals(resource)) {
                    return source == null ? null
                            : new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
                }
                return super.getResourceAsStream(name);
            }
        }) {
            return loadProgram(loader, "solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")\n");
        }
    }
}
