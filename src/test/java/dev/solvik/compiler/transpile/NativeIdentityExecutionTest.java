package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.testsupport.PackagedLibrary;
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
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code Any} contract executes on values of a native binding (§8).
 *
 * <p>{@code type()}, {@code toString()}, {@code equals()}, and {@code hashCode()} belong to every
 * value and reach the shared support members, so a binding gets them whether or not the Java class
 * has methods of those names. A binding that <em>declares</em> one of {@code toString},
 * {@code equals}, or {@code hashCode} selects its Java method instead; {@code type()} is final and
 * can never be declared. A declared {@code equals} is the contract bridge, so it compares the Java
 * objects: another value of the same binding crosses as its delegate. Both declaration backends must
 * agree.
 */
@DisplayName("Native binding identity executed")
class NativeIdentityExecutionTest {

    private static Map<String, String> transpile(String source) {
            return new SolvikCompiler("native.sol").transpile(source);
    }

    private static String output(String source, Path directory) throws Exception {
        Map<String, String> files = transpile(source);
        Path sources = Files.createDirectories(directory.resolve("src"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path emptyClasspath = Files.createDirectories(directory.resolve("empty-classpath"));
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-classpath",
                emptyClasspath.toString(), "-d", classes.toString()));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            arguments.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
                PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void a_packaged_binding_reports_the_name_it_was_constructed_with(@TempDir Path directory) throws Exception {
        String output = output("""
                scores := solvik::collections::Map<String, Integer>()
                scores.put("ann", 10)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((scores.type()) .. (" ") .. (scores.size()) .. (" ") .. (scores.get("ann")))

                plain := solvik::collections::Stack()
                plain.push(3)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((plain.type()) .. (" ") .. (plain.pop()))
                """, directory);

        assertThat(output).isEqualTo("Map<String, Integer> 1 10\nStack<Any> 3\n");
    }

    @Test
    void a_value_of_a_native_binding_answers_the_any_contract(@TempDir Path directory) throws Exception {
        String output = output("""
                module nat {
                    pub type Die native "java.util.Random" {
                        pub func Die()
                        pub func roll(sides: Integer) -> Integer native "nextInt"
                    }
                }

                die := nat::Die()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((die.roll(1)) .. (" ") .. (die.type()))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((die.toString().startsWith("java.util.Random")) .. (" ") .. (die.equals(die)) .. (" ") .. (die.hashCode() == die.hashCode()))
                """, directory);

        assertThat(output).isEqualTo("0 Die\ntrue true true\n");
    }

    @Test
    void a_declared_contract_member_selects_its_java_method(@TempDir Path directory) throws Exception {
        String output = output("""
                module nat {
                    pub type Bag native "java.util.ArrayList" {
                        pub func Bag()
                        pub func add(item: Any)
                        pub func hashCode() -> Integer native "size"
                    }

                    pub type Lines native "java.util.Scanner" {
                        pub func Lines(source: String)
                        pub func toString() -> String native "nextLine"
                    }
                }

                bag := nat::Bag()
                bag.add(7)
                bag.add(8)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((bag.type()) .. (" ") .. (bag.hashCode()) .. (" ") .. (bag.toString().startsWith("[")))

                lines := nat::Lines("ff\\nrest")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((lines.type()) .. (" ") .. (lines.toString()))
                """, directory);

        // hashCode() is the declared binding (ArrayList.size()) rather than an identity hash,
        // toString() is Scanner.nextLine(), and an undeclared toString() still renders the delegate.
        assertThat(output).isEqualTo("Bag 2 true\nLines ff\n");
    }

    @Test
    void a_null_binding_value_keeps_the_contract_answers(@TempDir Path directory) throws Exception {
        String output = output("""
                module nat {
                    pub type Die native "java.util.Random" {
                        pub func Die()
                    }
                }

                mut die: nat::Die? = nat::Die()
                die = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((die) .. (" ") .. (die == Null) .. (" ") .. (Null == Null) .. (" ") .. (String(die)))
                """, directory);

        assertThat(output).isEqualTo("null true true null\n");
    }

    @Test
    void a_declared_equals_compares_the_wrapped_java_objects(@TempDir Path directory) throws Exception {
        String output = output("""
                module nat {
                    pub type Bag native "java.util.ArrayList" {
                        pub func Bag()
                        pub func add(item: Any)
                        pub func equals(other: Any?) -> Boolean
                    }
                }

                left := nat::Bag()
                right := nat::Bag()
                other := nat::Bag()
                left.add(1)
                right.add(1)
                other.add(2)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((left.equals(right)) .. (" ") .. (left.equals(other)) .. (" ") .. (left.equals(left)) .. (" ") .. (left.equals("x")))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((left == right) .. (" ") .. (left == left))
                """, directory);

        // The bridge hands the Java member the delegate of another value of the same binding, so
        // ArrayList.equals(Object) compares contents. Any other argument is passed as it is, and
        // operator identity still compares references: the bridge does not reach `==` (§4).
        assertThat(output).isEqualTo("true false true false\nfalse true\n");
    }

    @Test
    void a_bound_value_with_equals_and_hash_works_as_a_collection_key(@TempDir Path directory) throws Exception {
        String output = output("""
                module nat {
                    pub type Amount native "java.math.BigInteger" {
                        pub func Amount(text: String)
                        pub func equals(other: Any?) -> Boolean
                        pub func hashCode() -> Integer
                    }
                }

                owed := nat::Amount("1000")
                paid := nat::Amount("1000")
                other := nat::Amount("2000")
                ledger := solvik::collections::Map<nat::Amount, Integer>()
                ledger.put(owed, 7)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((owed.equals(paid)) .. (" ") .. (owed.hashCode() == paid.hashCode()))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((ledger.get(paid)) .. (" ") .. (ledger.get(other)) .. (" ") .. (ledger.size()))
                """, directory);

        assertThat(output).isEqualTo("true true\n7 null 1\n");
    }

    @Test
    void contract_calls_on_a_native_value_reach_the_shared_support_members() {
        Map<String, String> files = transpile("""
                module nat {
                    pub type Die native "java.util.Random" {
                        pub func Die()
                    }
                }

                die := nat::Die()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((die.type()) .. (" ") .. (die.hashCode()) .. (" ") .. (die.equals(die)))
                """);

        String program = PackagedLibrary.programClass(files);
        assertThat(program).contains("Solvik$Program.type(die)", "Solvik$Program.hash(die)",
                "Solvik$Program.equal(die, die)");
        assertThat(program).doesNotContain("die.type()");
    }
}
