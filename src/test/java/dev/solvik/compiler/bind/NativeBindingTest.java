package dev.solvik.compiler.bind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.check.TypeChecker;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.load.ProgramLoader;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.transpile.GeneratedNames;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("native bindings, classpath, packages, and mutability")
class NativeBindingTest {

    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        TypeChecker.check(program, diagnostics);
        return diagnostics;
    }

    private static void accepted(String source) {
        Diagnostics diagnostics = check(source);
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("<t>")).isEmpty();
    }

    private static void rejected(String source, String code) {
        assertThat(check(source).all()).extracting(d -> d.code()).contains(code);
    }

    private static final String GOOD = """
            module bound {
                pub type Box<T> native "java.util.ArrayList" {
                    pub func Box()
                    pub func add(item: T)
                    pub func size() -> Integer
                }
            }
            """;

    @Test
    void a_declared_binding_resolves_and_emits_a_nominal_adapter() {
        String java = new SolvikCompiler("t.sol").transpile(GOOD + """
                box := bound::Box<String>()
                box.add("x")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((box.size()) .. "")
                """).get("bound/Box.java");

        assertThat(java).contains("package bound;", "public final class Box<T>",
                "private final ArrayList<T> $delegate;", "public Integer size()",
                "solvikTypeName()");
    }

    @Test
    @DisplayName("standard-library types are loaded from the compiler's own resources")
    void the_embedded_bindings_are_importable() {
        Map<String, String> files = new SolvikCompiler("t.sol").transpile("""
                map := solvik::collection::Map<String, Integer>()
                map.put("a", 1)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((map.size()) .. "")
                stack := solvik::collection::Stack<Integer>()
                stack.push(2)
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((stack.pop()) .. "")
                list := solvik::collection::List<String>()
                list.add("x")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((list.get(0)) .. "")
                """);

        assertThat(files).containsKeys("solvik/collection/Map.java",
                "solvik/collection/Stack.java", "solvik/collection/List.java", "Solvik$Program.java");
        assertThat(files.get("solvik/collection/Map.java"))
                .contains("package solvik.collection;")
                .contains("HashMap<K, V>");
        assertThat(files.get("solvik/collection/Stack.java")).contains("java.util.Stack<T>");
        assertThat(files.get("solvik/collection/List.java")).contains("ArrayList<T>");
    }

    @Test
    void the_embedded_map_has_no_hashmap_compatibility_alias() {
        assertThatThrownBy(() -> new SolvikCompiler("t.sol").transpile("""
                map := solvik::collection::HashMap()
                """))
                .isInstanceOf(SolvikCompiler.CompilationFailure.class)
                .hasMessageContaining("HashMap");
    }

    @Test
    void an_unknown_or_unsupported_native_target_is_reported() {
        rejected("""
                type Broken native "no.such.Class" {
                    pub func Broken()
                }
                """, JavaBinding.INVALID_BINDING);
        rejected("""
                type Iface native "java.util.List" {
                    pub func Iface()
                }
                """, JavaBinding.INVALID_BINDING);
        rejected("""
                trait NotAnInterface native "java.lang.String" {
                    pub func size() -> Integer
                }
                """, JavaBinding.INVALID_BINDING);
        rejected("""
                type Bad<T, U> native "java.util.ArrayList" {
                    pub func Bad()
                }
                """, JavaBinding.INVALID_BINDING);
        rejected("""
                type WithField native "java.util.ArrayList" {
                    x := 1
                    pub func WithField()
                }
                """, JavaBinding.INVALID_BINDING);
        rejected("""
                trait BadDefault native "java.lang.Runnable" {
                    pub func run() -> String { return "not void" }
                }
                """, JavaBinding.INVALID_BINDING);
    }

    /** A native trait over the JDK iterator, declared by the program so no packaged type is needed. */
    private static final String SEQUENCE = """
            module bound {
                pub trait Sequence<T> native "java.util.Iterator" {
                    pub func hasNext() -> Boolean
                    pub func next() -> T
                }

                pub type Box<T> native "java.util.ArrayList" {
                    pub func Box()
                    pub func add(item: T)
                    pub func iterator() -> Sequence<T>
                }
            }
            """;

    @Test
    @DisplayName("a native trait takes type parameters and a member may return one with arguments")
    void a_parameterized_native_return_is_bound() {
        // The packaged library also names java.util.Iterator, so this self-contained binding is
        // checked on its own: a second declaration of the same Java interface is ambiguous, and the
        // packaged one wins for a program that uses it.
        accepted(SEQUENCE);
    }

    @Test
    @DisplayName("a generic native trait is a Solvik interface with a generated $of adapter")
    void a_generic_native_trait_emits_the_adapter_and_converts_its_results() {
        Map<String, String> files = new SolvikCompiler("t.sol").transpile("""
                box := solvik::collection::List<Integer>()
                box.add(1)
                sequence := box.iterator()
                """);

        // A Solvik Boolean cannot override Java's primitive hasNext(), so the trait interface is
        // Solvik-owned and $of is the one Java-to-Solvik conversion.
        assertThat(files.get("solvik/collection/Iterator.java")).contains(
                "public interface Iterator<T>",
                "Boolean hasNext();",
                "static <T> Iterator<T> $of(java.util.Iterator<T> delegate)",
                "final class $Adapter<T> implements Iterator<T>");
        assertThat(files.get("solvik/collection/List.java")).contains(
                "public Iterator<T> iterator()",
                "Iterator.$of(this.$delegate.iterator())");
    }

    @Test
    @DisplayName("a parameterized return with no declared Solvik type stays unmatched")
    void an_unmappable_parameterized_return_is_rejected() {
        rejected("""
                module bound {
                    pub type Box<T> native "java.util.ArrayList" {
                        pub func Box()
                        pub func raw() -> Any native "iterator"
                    }
                }
                """, JavaBinding.INVALID_BINDING);
    }

    @Test
    @DisplayName("a signature with no unique Java overload is rejected, and renaming selects one")
    void overload_selection_is_exact() {
        rejected("""
                type L native "java.util.ArrayList" {
                    pub func L()
                    pub func mystery() -> Integer
                }
                """, JavaBinding.INVALID_BINDING);
        accepted("""
                type L<T> native "java.util.ArrayList" {
                    pub func L()
                    pub func drop(index: Integer) -> T native "remove"
                    pub func dropValue(item: Any) -> Boolean native "remove"
                }
                """);
        rejected("""
                type L<T> native "java.util.ArrayList" {
                    pub func L()
                    pub func add(item: T, extra: T)
                }
                """, JavaBinding.INVALID_BINDING);
    }

    @Test
    void native_types_are_nominal_and_raw_arguments_default_to_any() {
        Map<String, String> files = new SolvikCompiler("t.sol").transpile(GOOD + """
                raw := bound::Box()
                typed := bound::Box<Integer>()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((raw.type()) .. (" ") .. (typed.type()))
                """);
        assertThat(files.get("Solvik$Program.java"))
                .contains("Box<Object>", "Box<Any>", "Box<Integer>",
                        "value instanceof Box<?> userType");
    }

    @Test
    void modules_are_java_packages_and_functions_are_reachable_across_them() {
        Map<String, String> files = new SolvikCompiler("t.sol").transpile("""
                module geometry {
                    pub func width() -> Integer {
                        return 2
                    }
                    pub type Point {
                        pub func Point() { }
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((geometry::width()) .. "")
                p := geometry::Point()
                """);

        assertThat(files).containsKeys("geometry/Solvik$Functions.java", "geometry/Point.java");
        assertThat(files.get("geometry/Point.java")).contains("package geometry;");
        assertThat(files.get("Solvik$Program.java"))
                .contains("package solvik;")
                .contains("Solvik$Functions.width()");
    }

    @Test
    void declarations_are_immutable_unless_marked_mut() {
        rejected("a := 1\na = 2", TypeChecker.IMMUTABLE_MEMBER);
        rejected("mut b := 1\nb ..= \"x\"", TypeChecker.TYPE_ERROR);
        rejected("s := \"t\"\ns ..= \"x\"", TypeChecker.IMMUTABLE_MEMBER);
        accepted("mut a := 1\na = 2");
        accepted("mut slot: Integer? = Null\nslot = 1");
        rejected("type T {\n    x := 1\n    pub func T() { }\n    pub func f() {\n        x = 2\n    }\n}",
                TypeChecker.IMMUTABLE_MEMBER);
        rejected("type T {\n    pub x := 1\n    pub func T() { }\n}\nt := T()\nt.x = 2",
                TypeChecker.IMMUTABLE_MEMBER);
        accepted("type T {\n    pub mut x := 1\n    pub func T() { }\n}\nt := T()\nt.x = 2");
    }

    @Test
    void java_numeric_types_and_ranges_are_checked() {
        accepted("b := Byte(127)\ns := Short(32767)\ni := Integer(42)\nl := Long(\"9000000000\")");
        accepted("f := Float(1.5)\nd := Double(\"Infinity\")\nbi := BigInteger(\"123\")\nbd := BigDecimal(\"1.5\")");
        rejected("b := Byte(128)", TypeChecker.STATIC_FAILURE);
        rejected("s := Short(40000)", TypeChecker.STATIC_FAILURE);
        rejected("i := Integer(\"2147483648\")", TypeChecker.STATIC_FAILURE);
        rejected("l := Long(1.5)", TypeChecker.STATIC_FAILURE);
    }

    @Test
    void classpath_entries_are_validated_and_fingerprinted(@TempDir Path directory) throws Exception {
        Path jar = directory.resolve("dep.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("dep/Marker.txt"));
            out.write("marker".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        JavaClasspath classpath = JavaClasspath.parse(List.of(jar.toString()));
        assertThat(classpath.entries()).containsExactly(jar.toRealPath());
        String first = classpath.fingerprint();
        assertThat(JavaClasspath.parse(List.of(jar.toString())).fingerprint()).isEqualTo(first);

        assertThatThrownBy(() -> JavaClasspath.parse(List.of(directory.resolve("absent.jar").toString())))
                .isInstanceOf(IOException.class).hasMessageContaining("not a readable JAR");
        assertThatThrownBy(() -> JavaClasspath.parse(List.of("")))
                .isInstanceOf(IOException.class).hasMessageContaining("non-empty");
        assertThatThrownBy(() -> JavaClasspath.parse(List.of(directory + "/*")))
                .isInstanceOf(IOException.class);
        assertThat(JavaClasspath.empty().entries()).isEmpty();
    }

    @Test
    void an_unknown_embedded_module_is_reported() {
        Diagnostics diagnostics = new Diagnostics();
        ProgramLoader.Loaded loaded = new ProgramLoader(diagnostics)
                .loadSource("include { \"java:nope\" }\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1) .. \"\")", "t.sol");
        assertThat(diagnostics.all()).extracting(d -> d.code()).contains(ProgramLoader.MISSING_FILE);
        assertThat(loaded.programFileNames()).contains("t.sol");
    }

    @Test
    @DisplayName("built-in big numbers expose their Java methods without an adapter")
    void big_number_java_methods_are_reachable() {
        Map<String, String> files = new SolvikCompiler("t.sol").transpile("""
                a := BigInteger("12")
                b := BigInteger("18")
                shift := 2
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.gcd(b)) .. (" ") .. (a.pow(2)) .. (" ") .. (a.signum()) .. (" ") .. (a.bitLength()))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.max(b)) .. (" ") .. (a.shiftLeft(shift)) .. (" ") .. (a.toString(2)))
                d := BigDecimal("1234.5000")
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((d.scale()) .. (" ") .. (d.stripTrailingZeros()) .. (" ") .. (d.unscaledValue()))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((d.movePointLeft(2)) .. (" ") .. (d.add(BigDecimal("0.5"))))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((d.toPlainString()) .. (" ") .. (d.toString()))
                """);
        String java = files.get("Solvik$Program.java");

        assertThat(java).contains(".gcd(", ".pow(", ".bitLength()", ".shiftLeft(",
                ".toString(", ".scale()", ".stripTrailingZeros()", ".unscaledValue()",
                ".movePointLeft(", ".add(");
        // A value argument is unboxed for a Java primitive parameter; a literal already is one.
        assertThat(java).as("arguments are unboxed for Java primitives")
                .contains("a.shiftLeft(Objects.requireNonNull(shift).intValue())")
                .doesNotContain("a.pow(Objects.requireNonNull(2).intValue())");
        assertThat(java).as("contract rendering is preserved for BigDecimal")
                .contains("toPlainString()");
    }

    @Test
    void unknown_or_unsupported_big_number_methods_are_reported() {
        rejected("a := BigInteger(\"1\")\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.nope()) .. \"\")", TypeChecker.TYPE_ERROR);
        rejected("a := BigInteger(\"1\")\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.gcd(1)) .. \"\")", TypeChecker.TYPE_ERROR);
        rejected("a := BigInteger(\"1\")\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.toByteArray()) .. \"\")", TypeChecker.TYPE_ERROR);
        rejected("a := BigInteger(\"1\")\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.pow(\"2\")) .. \"\")", TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("nested modules form a package path, and the reserved java root is prefixed")
    void nested_modules_map_to_nested_packages() {
        Map<String, String> files = new SolvikCompiler("t.sol").transpile("""
                module library {
                    module inner {
                        pub func twice(n: Integer) -> Integer {
                            return n * 2
                        }
                        pub type Point {
                            pub func Point() { }
                        }
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((library::inner::twice(3)) .. "")
                p := library::inner::Point()
                """);

        assertThat(files).containsKeys("library/inner/Solvik$Functions.java",
                "library/inner/Point.java");
        assertThat(files.get("library/inner/Point.java")).contains("package library.inner;");
        assertThat(files.get("Solvik$Program.java"))
                .contains("Solvik$Functions.twice(3)");
        assertThat(GeneratedNames.javaPackage("java::collection"))
                .isEqualTo("solvik.java.collection");
    }

    @Test
    void generated_names_map_modules_to_packages() {
        assertThat(GeneratedNames.javaClassName("Point")).isEqualTo("solvik.Point");
        assertThat(GeneratedNames.javaClassName("geometry::Point")).isEqualTo("geometry.Point");
        assertThat(GeneratedNames.sourcePath("geometry::Point")).isEqualTo("geometry/Point.java");
        assertThat(GeneratedNames.moduleFunctions("geometry")).isEqualTo("geometry.Solvik$Functions");
    }
}
