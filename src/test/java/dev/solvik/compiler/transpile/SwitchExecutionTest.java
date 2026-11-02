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

/** End-to-end runs of {@code switch} statements: dispatch, regex matching, and no fall-through. */
@DisplayName("switch executed")
class SwitchExecutionTest {

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
    @DisplayName("exactly one arm runs: there is no fall-through")
    void exactly_one_arm_runs(@TempDir Path directory) throws Exception {
        String output = run("""
                func report(method: String) {
                    switch method {
                        case "GET" {
                            TextWriter(Stdio.stdout()).writeLine(("read") .. "")
                        }
                        case "POST" {
                            TextWriter(Stdio.stdout()).writeLine(("write") .. "")
                        }
                        case "DELETE" {
                            TextWriter(Stdio.stdout()).writeLine(("remove") .. "")
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("other") .. "")
                        }
                    }
                }

                report("GET")
                report("POST")
                report("DELETE")
                report("PATCH")
                """, directory);

        assertThat(output).isEqualTo("read\nwrite\nremove\nother\n");
    }

    @Test
    @DisplayName("parentheses around the subject parse and run the same way")
    void parentheses_around_the_subject(@TempDir Path directory) throws Exception {
        String output = run("""
                code := 2
                switch (code) {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("one") .. "")
                    }
                    case 2 {
                        TextWriter(Stdio.stdout()).writeLine(("two") .. "")
                    }
                }
                """, directory);

        assertThat(output).isEqualTo("two\n");
    }

    @Test
    @DisplayName("if, else if, and while accept optional parentheses, as switch does")
    void conditions_and_subjects_may_be_parenthesized(@TempDir Path directory) throws Exception {
        String output = run("""
                mut n := 2
                if (n > 1) {
                    TextWriter(Stdio.stdout()).writeLine(("parenthesized if") .. "")
                } else if (n > 0) {
                    TextWriter(Stdio.stdout()).writeLine(("unreachable branch") .. "")
                }
                while (n > 1) {
                    n = n - 1
                }
                switch (n) {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("parenthesized switch") .. "")
                    }
                }
                TextWriter(Stdio.stdout()).writeLine(("n: ") .. (n))
                """, directory);

        assertThat(output).isEqualTo("parenthesized if\nparenthesized switch\nn: 1\n");
    }

    @Test
    @DisplayName("a switch with neither a matching case nor a default does nothing")
    void no_match_and_no_default_is_a_no_op(@TempDir Path directory) throws Exception {
        String output = run("""
                method := "PATCH"
                switch method {
                    case "GET" {
                        TextWriter(Stdio.stdout()).writeLine(("read") .. "")
                    }
                }
                TextWriter(Stdio.stdout()).writeLine(("after") .. "")
                """, directory);

        assertThat(output).isEqualTo("after\n");
    }

    @Test
    @DisplayName("a regex case matches the whole subject, not a substring")
    void regex_matches_the_whole_subject(@TempDir Path directory) throws Exception {
        String output = run("""
                func exact(subject: String) {
                    switch subject {
                        case regex "^b$" {
                            TextWriter(Stdio.stdout()).writeLine(("exact full") .. "")
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("exact no") .. "")
                        }
                    }
                }

                func dotted(subject: String) {
                    switch subject {
                        case regex "a.c" {
                            TextWriter(Stdio.stdout()).writeLine(("dot full") .. "")
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("dot no") .. "")
                        }
                    }
                }

                exact("b")
                exact("abc")
                dotted("abc")
                dotted("abcd")
                """, directory);

        // "abc" contains "b" but is not equal to it, and "abcd" starts with "abc" but is longer.
        assertThat(output).isEqualTo("""
                exact full
                exact no
                dot full
                dot no
                """);
    }

    @Test
    @DisplayName("a literal case wins over a regex case that would also match")
    void a_literal_case_wins_over_a_regex_case(@TempDir Path directory) throws Exception {
        String output = run("""
                func route(path: String) {
                    switch path {
                        case "/health" {
                            TextWriter(Stdio.stdout()).writeLine(("literal") .. "")
                        }
                        case regex "^/.*" {
                            TextWriter(Stdio.stdout()).writeLine(("regex") .. "")
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("default") .. "")
                        }
                    }
                }

                route("/health")
                route("/users")
                route("")
                """, directory);

        assertThat(output).isEqualTo("literal\nregex\ndefault\n");
    }

    @Test
    @DisplayName("regex arms are tried in source order")
    void regex_arms_are_ordered(@TempDir Path directory) throws Exception {
        String output = run("""
                func kind(path: String) {
                    switch path {
                        case regex "^/api/users/[0-9]+$" {
                            TextWriter(Stdio.stdout()).writeLine(("user") .. "")
                        }
                        case regex "^/api/.*" {
                            TextWriter(Stdio.stdout()).writeLine(("api") .. "")
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("other") .. "")
                        }
                    }
                }

                kind("/api/users/7")
                kind("/api/users/me")
                kind("/static/app.js")
                """, directory);

        assertThat(output).isEqualTo("user\napi\nother\n");
    }

    @Test
    @DisplayName("the subject expression is evaluated exactly once")
    void the_subject_is_evaluated_once(@TempDir Path directory) throws Exception {
        String output = run("""
                type Counter {
                    mut calls := 0

                    pub func Counter() {
                        self.calls = 0
                    }

                    pub func next() -> String {
                        self.calls = self.calls + 1
                        return "value"
                    }

                    pub func count() -> Integer {
                        return self.calls
                    }
                }

                counter := Counter()
                switch counter.next() {
                    case regex "^no$" {
                        TextWriter(Stdio.stdout()).writeLine(("first") .. "")
                    }
                    case regex "^value$" {
                        TextWriter(Stdio.stdout()).writeLine(("second") .. "")
                    }
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("default") .. "")
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((counter.count()) .. "")
                """, directory);

        assertThat(output).isEqualTo("second\n1\n");
    }

    @Test
    @DisplayName("Integer, Byte, and Boolean subjects dispatch correctly")
    void numeric_and_boolean_subjects(@TempDir Path directory) throws Exception {
        String output = run("""
                func name(code: Integer) -> String {
                    switch code {
                        case -1 {
                            return "minus one"
                        }
                        case 0 {
                            return "zero"
                        }
                        case 200 {
                            return "two hundred"
                        }
                        default {
                            return "other"
                        }
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((name(-1)) .. (" ") .. (name(0)) .. (" ") .. (name(200)) .. (" ") .. (name(7)))

                bytes := Byte[3] {0, 127, 5}
                for small in bytes {
                    switch small {
                        case 0 {
                            TextWriter(Stdio.stdout()).writeLine(("byte zero") .. "")
                        }
                        case 127 {
                            TextWriter(Stdio.stdout()).writeLine(("byte max") .. "")
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("byte other") .. "")
                        }
                    }
                }

                flags := Boolean[2] {true, false}
                for flag in flags {
                    switch flag {
                        case true {
                            TextWriter(Stdio.stdout()).writeLine(("true") .. "")
                        }
                        case false {
                            TextWriter(Stdio.stdout()).writeLine(("false") .. "")
                        }
                    }
                }
                """, directory);

        assertThat(output).isEqualTo("""
                minus one zero two hundred other
                byte zero
                byte max
                byte other
                true
                false
                """);
    }

    @Test
    @DisplayName("a regex switch inside a loop reuses its compiled pattern and stays correct")
    void a_regex_switch_in_a_loop(@TempDir Path directory) throws Exception {
        String output = run("""
                paths := String[4] {"/api/users/1", "/api/other", "/index.html", "/api/users/2"}
                mut users := 0
                mut api := 0
                mut pages := 0
                for path in paths {
                    switch path {
                        case regex "^/api/users/[0-9]+$" {
                            users = users + 1
                        }
                        case regex "^/api/.*" {
                            api = api + 1
                        }
                        default {
                            pages = pages + 1
                        }
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((users) .. (" ") .. (api) .. (" ") .. (pages))
                """, directory);

        assertThat(output).isEqualTo("2 1 1\n");
    }

    @Test
    @DisplayName("a switch inside a type method compiles its patterns into that type's class")
    void a_switch_inside_a_type_method(@TempDir Path directory) throws Exception {
        String output = run("""
                type Router {
                    route := "/"

                    pub func Router(route: String) {
                        self.route = route
                    }

                    pub func kind() -> String {
                        switch self.route {
                            case regex "^/api/users/[0-9]+$" {
                                return "user"
                            }
                            case regex "^/api/.*" {
                                return "api"
                            }
                            default {
                                return "page"
                            }
                        }
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((Router("/api/users/9").kind()) .. "")
                TextWriter(Stdio.stdout()).writeLine((Router("/api/status").kind()) .. "")
                TextWriter(Stdio.stdout()).writeLine((Router("/about").kind()) .. "")
                """, directory);

        assertThat(output).isEqualTo("user\napi\npage\n");
    }

    @Test
    @DisplayName("a generic type may hold a regex switch")
    void a_regex_switch_inside_a_generic_type(@TempDir Path directory) throws Exception {
        String output = run("""
                type Box<T> {
                    tag := ""
                    mut value: T? = Null

                    pub func Box(tag: String, value: T) {
                        self.tag = tag
                        self.value = value
                    }

                    pub func classify() -> String {
                        switch self.tag {
                            case regex "^num.*" {
                                return "number-ish"
                            }
                            default {
                                return "other"
                            }
                        }
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((Box("numeric", 1).classify()) .. "")
                TextWriter(Stdio.stdout()).writeLine((Box("word", "text").classify()) .. "")
                """, directory);

        assertThat(output).isEqualTo("number-ish\nother\n");
    }

    @Test
    @DisplayName("a nested switch dispatches independently")
    void a_nested_switch(@TempDir Path directory) throws Exception {
        String output = run("""
                func describe(kind: String, code: Integer) -> String {
                    switch kind {
                        case "http" {
                            switch code {
                                case 200 {
                                    return "ok"
                                }
                                case 404 {
                                    return "missing"
                                }
                                default {
                                    return "http " .. code
                                }
                            }
                        }
                        default {
                            return "plain"
                        }
                    }
                }

                TextWriter(Stdio.stdout()).writeLine((describe("http", 200)) .. "")
                TextWriter(Stdio.stdout()).writeLine((describe("http", 500)) .. "")
                TextWriter(Stdio.stdout()).writeLine((describe("other", 200)) .. "")
                """, directory);

        assertThat(output).isEqualTo("ok\nhttp 500\nplain\n");
    }
}
