package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.load.ProgramLoader;
import dev.solvik.compiler.report.DiagnosticReporter;
import dev.solvik.compiler.testsupport.GeneratedProgram;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * File-local alias semantics (LANGUAGE.md §3, "Aliases").
 *
 * <p>An alias names an existing canonical module, type, or trait and is visible only in the file
 * that declares it. It adds no nominal identity: every alias resolves to the canonical target, so
 * generated Java is identical to a program written without aliases. These tests compare the two
 * programs' observable output, prove that two files may use one spelling for unrelated targets, and
 * pin each rejection with its stable diagnostic.
 */
@DisplayName("aliases")
class AliasTest {

    @TempDir
    Path directory;

    private void write(String name, String source) throws IOException {
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

    private String run(String entry) throws Exception {
        return GeneratedProgram.run(transpile(entry), directory.resolve("out-" + entry));
    }

    /** Compiles {@code entry} expecting failure, and returns the rendered diagnostics. */
    private String failure(String entry) {
        return assertThrows(SolvikCompiler.CompilationFailure.class,
                () -> new SolvikCompiler(entry).transpile(load(entry))).getMessage();
    }

    /** A library of aliased targets: a type, a trait, a generic type, and a nested module. */
    private void writeLibrary() throws IOException {
        write("lib.sol", """
                module alpha {
                    pub type Parser {
                        pub id := 0

                        pub func Parser(id: Integer) {
                            self.id = id
                        }

                        static pub func label() -> String {
                            return "static-parser"
                        }

                        pub func parse(text: String) -> Integer {
                            return text.size()
                        }
                    }

                    pub type Ticket {
                        pub n := 0

                        pub func Ticket(n: Integer) {
                            self.n = n
                        }

                        pub func toString() -> String {
                            return "#" .. n
                        }
                    }

                    pub type Box<T> {
                        mut value: T? = Null

                        pub func Box(value: T) {
                            self.value = value
                        }

                        pub func get() -> T? {
                            return value
                        }
                    }

                    pub trait Reader {
                        pub func read() -> String {
                            return "read"
                        }
                    }

                    module nested {
                        pub type Deep {
                            pub func Deep() { }

                            pub func name() -> String {
                                return "deep"
                            }
                        }
                    }

                    pub func make() -> Integer {
                        return 7
                    }
                }
                """);
    }

    @Nested
    @DisplayName("accepted targets")
    class Accepted {

        @Test
        @DisplayName("a type, trait, generic type, and module alias run like the canonical spellings")
        void aliased_and_canonical_programs_have_the_same_output() throws Exception {
            writeLibrary();
            write("aliased.sol", """
                    include { "lib.sol" }

                    alias {
                        alpha::Parser Parser
                        alpha::Ticket Tkt
                        alpha::Box Box
                        alpha::Reader Source
                        alpha::nested nested
                        alpha landing
                        solvik::collection::Map Registry
                    }

                    parser := Parser(1)
                    maybe: Parser? = Null
                    held: Parser[2] = Parser[2] { Parser(2), Parser(3) }
                    registry := Registry<String, Tkt>()
                    registry.put("a", Tkt(9))
                    ticket := registry.get("a")
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("type: ") .. (parser.id) .. (" ") .. (Parser.label()) .. (" ") .. (parser.parse("abcd")))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("array: ") .. (held[0].id) .. (" ") .. (held.size))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("nullable: ") .. (maybe == Null))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("generic: ") .. (Box("boxed").get()) .. (" ") .. (ticket))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("module: ") .. (nested::Deep().name()) .. (" ") .. (landing::make()))
                    """);
            write("plain.sol", """
                    include { "lib.sol" }

                    parser := alpha::Parser(1)
                    maybe: alpha::Parser? = Null
                    held: alpha::Parser[2] = alpha::Parser[2] { alpha::Parser(2), alpha::Parser(3) }
                    registry := solvik::collection::Map<String, alpha::Ticket>()
                    registry.put("a", alpha::Ticket(9))
                    ticket := registry.get("a")
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("type: ") .. (parser.id) .. (" ") .. (alpha::Parser.label()) .. (" ") .. (parser.parse("abcd")))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("array: ") .. (held[0].id) .. (" ") .. (held.size))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("nullable: ") .. (maybe == Null))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("generic: ") .. (alpha::Box("boxed").get()) .. (" ") .. (ticket))
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("module: ") .. (alpha::nested::Deep().name()) .. (" ") .. (alpha::make()))
                    """);

            String aliased = run("aliased.sol");
            assertThat(aliased).isEqualTo(run("plain.sol"));
            assertThat(aliased).isEqualTo("""
                    type: 1 static-parser 4
                    array: 2 2
                    nullable: true
                    generic: boxed #9
                    module: deep 7
                    """);
        }

        @Test
        @DisplayName("a trait alias works in implements and in the qualified default form")
        void a_trait_alias_is_usable_where_the_trait_is() throws Exception {
            writeLibrary();
            write("trait.sol", """
                    include { "lib.sol" }

                    alias { alpha::Reader Source }

                    type ReaderImpl implements Source {
                        pub func ReaderImpl() { }

                        pub func read() -> String {
                            return "own:" .. Source.read(self)
                        }
                    }

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((ReaderImpl().read()))
                    """);

            assertThat(run("trait.sol")).isEqualTo("own:read\n");
        }

        @Test
        @DisplayName("a module alias reaches static members and written native type arguments")
        void a_module_alias_heads_a_static_call_and_a_written_type_argument_list() throws Exception {
            writeLibrary();
            write("module.sol", """
                    include { "lib.sol" }

                    alias { alpha landing; solvik::collection Registry }

                    scores := Registry::Map<String, Integer>()
                    scores.put("a", 1)

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((landing::Parser.label()) .. (" ") .. (landing::nested::Deep().name()) .. (" ") .. (scores.get("a")))
                    """);

            assertThat(run("module.sol")).isEqualTo("static-parser deep 1\n");
        }

        @Test
        @DisplayName("an alias declared after its first use is valid: blocks are order independent")
        void an_alias_block_may_follow_its_first_use() throws Exception {
            writeLibrary();
            write("late.sol", """
                    include { "lib.sol" }

                    first := Parser(1)
                    alias { alpha::Parser Parser }
                    second := Parser(2)

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((first.id) .. (" ") .. (second.id))
                    """);

            assertThat(run("late.sol")).isEqualTo("1 2\n");
        }

        @Test
        @DisplayName("two files may use one alias spelling for unrelated targets in one module")
        void each_file_resolves_its_own_alias_environment() throws Exception {
            write("shared.sol", """
                    module shared {
                        pub type LeftParser {
                            pub func LeftParser() { }

                            pub func kind() -> String {
                                return "left"
                            }
                        }

                        pub type RightParser {
                            pub func RightParser() { }

                            pub func kind() -> String {
                                return "right"
                            }
                        }
                    }
                    """);
            write("left.sol", """
                    include { "shared.sol" }

                    alias { shared::LeftParser P }

                    module shared {
                        pub func leftMake() -> String {
                            return P().kind()
                        }
                    }
                    """);
            write("right.sol", """
                    include { "shared.sol" }

                    alias { shared::RightParser P }

                    module shared {
                        pub func rightMake() -> String {
                            return P().kind()
                        }
                    }
                    """);
            write("app.sol", """
                    include { "left.sol"; "right.sol" }

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((shared::leftMake()) .. (" ") .. (shared::rightMake()))
                    """);

            assertThat(run("app.sol")).isEqualTo("left right\n");
        }

        @Test
        @DisplayName("an alias is private to its file: neither direction leaks")
        void aliases_do_not_cross_a_file_boundary() throws Exception {
            writeLibrary();
            // The included file declares an alias the entry file cannot see.
            write("included.sol", """
                    include { "lib.sol" }

                    alias { alpha::Parser LibParser }

                    module lib {
                        pub func libParserId() -> Integer {
                            return LibParser(1).id
                        }
                    }
                    """);
            write("visible.sol", """
                    include { "included.sol" }

                    leaked := LibParser(2)
                    """);
            assertThat(failure("visible.sol")).contains("unknown function 'LibParser'");

            // The entry file declares an alias the included file cannot see.
            write("dependent.sol", """
                    module dependent {
                        pub func broken() -> Integer {
                            return EntryParser(1).id
                        }
                    }
                    """);
            write("hidden.sol", """
                    include { "lib.sol"; "dependent.sol" }

                    alias { alpha::Parser EntryParser }

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((EntryParser(1).id) .. "")
                    """);
            assertThat(failure("hidden.sol")).contains("unknown function 'EntryParser'");
        }
    }

    @Nested
    @DisplayName("generated Java")
    class GeneratedJava {

        @Test
        @DisplayName("an alias emits the canonical type and no alias artifact")
        void an_alias_adds_no_class_and_names_the_canonical_type() throws Exception {
            writeLibrary();
            write("app.sol", """
                    include { "lib.sol" }

                    alias { alpha::Ticket Tkt; alpha::Reader Source }

                    type ReaderImpl implements Source {
                        pub func ReaderImpl() { }
                    }

                    ticket := Tkt(4)
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((ticket) .. "")
                    """);

            Map<String, String> files = transpile("app.sol");

            assertThat(files).containsKey("alpha/Ticket.java");
            assertThat(files.keySet()).noneMatch(name -> name.contains("Tkt"));
            assertThat(files.get("Solvik$Program.java")).contains("new Ticket(");
            assertThat(files.get("ReaderImpl.java")).contains("implements Reader ");
            assertThat(files.values()).noneMatch(java -> java.contains("Tkt"));
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        private void expectRejected(String aliasBlock, String expected) throws IOException {
            writeLibrary();
            write("bad.sol", """
                    include { "lib.sol" }

                    """ + aliasBlock + """

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("ok")
                    """);

            assertThat(failure("bad.sol"))
                    .contains(TypeChecker.INVALID_ALIAS)
                    .contains(expected);
        }

        @Test
        void an_unknown_target_is_rejected_at_the_target() throws IOException {
            writeLibrary();
            write("bad.sol", """
                    include { "lib.sol" }

                    alias { alpha::Missing Missing }
                    """);

            String message = failure("bad.sol");
            assertThat(message)
                    .contains(TypeChecker.INVALID_ALIAS)
                    .contains("unknown alias target 'alpha::Missing'")
                    .contains(DiagnosticReporter.displayName(directory.resolve("bad.sol")) + ":3:9");
        }

        @Test
        void a_function_target_is_rejected() throws IOException {
            expectRejected("alias { alpha::make made }", "is a function");
        }

        @Test
        void a_private_target_is_rejected() throws IOException {
            write("hidden.sol", """
                    module hidden {
                        type Secret {
                            pub func Secret() { }
                        }
                    }
                    """);
            write("bad.sol", """
                    include { "hidden.sol" }

                    alias { hidden::Secret Secret }
                    """);

            assertThat(failure("bad.sol"))
                    .contains(TypeChecker.INVALID_ALIAS)
                    .contains("is private to module 'hidden'");
        }

        @Test
        void a_duplicate_local_name_in_one_file_is_rejected() throws IOException {
            expectRejected("alias { alpha::Parser P; alpha::Ticket P }",
                    "alias 'P' is already declared in this file");
            // A failed first entry does not hide the duplicate: the local name is still recorded.
            expectRejected("alias { alpha::Missing P; alpha::Ticket P }",
                    "alias 'P' is already declared in this file");
        }

        @Test
        void a_reserved_built_in_name_is_rejected() throws IOException {
            expectRejected("alias { alpha::Parser String }", "'String' is a built-in type");
            expectRejected("alias { alpha::Parser exit }", "'exit' is a built-in function");
            expectRejected("alias { alpha::Parser solvik }", "'solvik' is the reserved");
        }

        @Test
        void a_file_level_declaration_name_is_rejected() throws IOException {
            expectRejected("type Parser { pub func Parser() { } }\nalias { alpha::Parser Parser }",
                    "'Parser' is already declared at file level");
        }

        @Test
        void a_top_level_function_name_is_rejected() throws IOException {
            expectRejected("func helper() -> Integer { return 1 }\nalias { alpha::Parser helper }",
                    "'helper' is already declared at file level");
        }

        @Test
        void a_top_level_module_name_is_rejected() throws IOException {
            expectRejected("module beta { }\nalias { alpha::Parser beta }",
                    "'beta' is already declared at file level");
        }

        @Test
        @DisplayName("an alias target is canonical: it cannot chain through another alias")
        void an_alias_of_an_alias_is_rejected() throws IOException {
            expectRejected("alias { alpha::Parser P; P Q }", "unknown alias target 'P'");
        }

        @Test
        @DisplayName("a module-local declaration wins over a file alias, and the alias may still exist")
        void a_module_local_declaration_shadows_the_alias_inside_its_module() throws Exception {
            writeLibrary();
            write("shadow.sol", """
                    include { "lib.sol" }

                    alias { alpha::Ticket Tkt }

                    module beta {
                        pub type Tkt {
                            pub func Tkt() { }

                            pub func label() -> String {
                                return "module-local"
                            }
                        }

                        pub func local() -> String {
                            return Tkt().label()
                        }
                    }

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((beta::local()) .. (" ") .. (Tkt(3)))
                    """);

            assertThat(run("shadow.sol")).isEqualTo("module-local #3\n");
        }

        @Test
        @DisplayName("a private target is still reachable by its canonical name inside its module")
        void the_strict_target_policy_does_not_narrow_existing_access() throws Exception {
            write("hidden.sol", """
                    module hidden {
                        type Secret {
                            pub value := 5

                            pub func Secret() { }
                        }

                        pub func secretValue() -> Integer {
                            return Secret().value
                        }
                    }
                    """);
            write("app.sol", """
                    include { "hidden.sol" }

                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((hidden::secretValue()) .. "")
                    """);

            assertThat(run("app.sol")).isEqualTo("5\n");
        }
    }
}
