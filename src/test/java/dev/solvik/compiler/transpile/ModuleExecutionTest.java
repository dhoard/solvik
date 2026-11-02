package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end runs of modules (LANGUAGE.md §2, "Modules").
 *
 * <p>A qualified type is emitted as its own class named {@code module$Type}, so two types of the
 * same name coexist on the JVM without either being renamed in the source.
 */
@DisplayName("modules executed")
class ModuleExecutionTest {

    private String run(String source, Path directory) throws Exception {
        Path sources = directory.resolve("src");
        Path classes = directory.resolve("classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);

        Map<String, String> files = new SolvikCompiler("test.sol").transpile(source);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path $path = sources.resolve(file.getKey());
            Files.createDirectories($path.getParent());
            Files.writeString($path, file.getValue());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = Stream.concat(
                Stream.of("-d", classes.toString()),
                files.keySet().stream().map(name -> sources.resolve(name).toString())
        ).toList();
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).isZero();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            loader.loadClass(GeneratedNames.PROGRAM_BINARY_NAME)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("two modules may each declare Point")
    void two_modules_may_each_declare_point(@TempDir Path directory) throws Exception {
        String output = run("""
                module geometry {
                    pub type Point {
                        pub mut x := 0
                        pub mut y := 0

                        pub func Point(x: Integer, y: Integer) {
                            self.x = x
                            self.y = y
                        }

                        pub func shifted(dx: Integer) -> Point {
                            return Point(x + dx, y)
                        }

                        pub func toString() -> String {
                            return "geo(" .. x .. ", " .. y .. ")"
                        }
                    }
                }

                module billing {
                    pub type Point {
                        pub note := "invoice"

                        pub func Point() { }

                        pub func toString() -> String {
                            return "bill:" .. note
                        }
                    }
                }

                start := geometry::Point(1, 2)
                bill := billing::Point()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((start.shifted(3)) .. (" ") .. (bill))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((start.type()) .. (" ") .. (bill.type()))
                """, directory);

        assertThat(output).isEqualTo("geo(4, 2) bill:invoice\nPoint Point\n");
    }

    @Test
    void blocks_with_the_same_name_merge_into_one_module(@TempDir Path directory)
            throws Exception {
        String output = run("""
                module geometry {
                    pub type Point {
                        pub x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }
                    }
                }

                module geometry {
                    pub type Circle {
                        pub radius := 1

                        pub func Circle(radius: Integer) {
                            self.radius = radius
                        }

                        pub func center() -> Point {
                            return Point(radius)
                        }
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((geometry::Circle(7).center().x) .. "")
                """, directory);

        assertThat(output).isEqualTo("7\n");
    }

    @Test
    void a_qualified_type_works_in_arrays_generics_and_loops(@TempDir Path directory)
            throws Exception {
        String output = run("""
                module geometry {
                    pub type Point {
                        pub mut x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }
                    }

                    pub type Box<T> {
                        pub mut value: T? = Null

                        pub func Box(value: T) {
                            self.value = value
                        }
                    }
                }

                func total(points: geometry::Point[]) -> Integer {
                    mut sum := 0
                    for point in points {
                        sum = sum + point.x
                    }
                    return sum
                }

                grid := geometry::Point[3] { geometry::Point(1), geometry::Point(2), geometry::Point(3) }
                box := geometry::Box(geometry::Point(10))
                mut boxed: geometry::Point? = box.value
                if boxed != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((total(grid)) .. (" ") .. (boxed.x) .. (" ") .. (box.type()))
                }
                """, directory);

        assertThat(output).isEqualTo("6 10 Box<Point>\n");
    }

    @Test
    @DisplayName("module functions merge, shadow globals, and call each other")
    void module_functions_run(@TempDir Path directory) throws Exception {
        String output = run("""
                func describe() -> String {
                    return "global"
                }

                module geometry {
                    pub func describe() -> String {
                        return "scoped"
                    }

                    pub func area(width: Integer, height: Integer) -> Integer {
                        return width * height
                    }

                    pub func squared(side: Integer) -> Integer {
                        return area(side, side)
                    }
                }

                module geometry {
                    pub func report(side: Integer) -> String {
                        return describe() .. ":" .. squared(side)
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((describe()) .. (" ") .. (geometry::describe()) .. (" ") .. (geometry::report(4)))
                """, directory);

        assertThat(output).isEqualTo("global scoped scoped:16\n");
    }

    @Test
    void a_type_body_calls_module_and_global_functions(@TempDir Path directory)
            throws Exception {
        String output = run("""
                func twice(n: Integer) -> Integer {
                    return n * 2
                }

                module geometry {
                    pub type Point {
                        pub x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }

                        pub func scaled(factor: Integer) -> Integer {
                            return twice(scale(x, factor))
                        }
                    }

                    pub func scale(value: Integer, factor: Integer) -> Integer {
                        return value * factor
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((geometry::Point(3).scaled(4)) .. "")
                """, directory);

        assertThat(output).isEqualTo("24\n");
    }

    @Test
    @DisplayName("a pub type uses a private type inside its module")
    void a_pub_type_uses_a_private_type_inside_its_module(@TempDir Path directory)
            throws Exception {
        String output = run("""
                module shapes {
                    type Ledger {
                        pub mut total := 0

                        pub func Ledger(total: Integer) {
                            self.total = total
                        }

                        pub func doubled() -> Integer {
                            return total * 2
                        }
                    }

                    pub type Report {
                        pub func Report() { }

                        pub func summary() -> String {
                            ledger := Ledger(21)
                            return "total=" .. ledger.total .. " doubled=" .. ledger.doubled()
                        }
                    }

                    pub func helper() -> Integer {
                        return Ledger(4).doubled()
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((shapes::Report().summary()) .. (" ") .. (shapes::helper()))
                """, directory);

        assertThat(output).isEqualTo("total=21 doubled=42 8\n");
    }

    @Test
    void a_name_declared_at_the_top_level_stays_global(@TempDir Path directory) throws Exception {
        String output = run("""
                type Point {
                    pub x := 0

                    pub func Point(x: Integer) {
                        self.x = x
                    }

                    pub func toString() -> String {
                        return "global(" .. x .. ")"
                    }
                }

                module geometry {
                    pub type Point {
                        pub x := 0

                        pub func Point(x: Integer) {
                            self.x = x
                        }

                        pub func toString() -> String {
                            return "scoped(" .. x .. ")"
                        }

                        pub func fresh() -> Point {
                            return Point(2)
                        }
                    }
                }

                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Point(1)) .. (" ") .. (geometry::Point(9)) .. (" ") .. (geometry::Point(0).fresh()))
                """, directory);

        assertThat(output).isEqualTo("global(1) scoped(9) scoped(2)\n");
    }
}
