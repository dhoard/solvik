package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/** {@code switch} / {@code case} / {@code case regex} / {@code default} rules (§3, "switch"). */
@DisplayName("Type checker / switch")
class SwitchTest {

    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void assertAccepted(String source) {
        Diagnostics result = check(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>")).isEmpty();
    }

    private static void assertRejected(String source) {
        assertThat(check(source).all()).isNotEmpty();
    }

    // ------------------------------------------------------------------ accepted

    @Test
    void a_string_switch_with_literal_cases_and_a_default() {
        assertAccepted("""
                method := "GET"
                switch method {
                    case "GET" {
                        TextWriter(Stdio.stdout()).writeLine(("read") .. "")
                    }
                    case "POST" {
                        TextWriter(Stdio.stdout()).writeLine(("write") .. "")
                    }
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("other") .. "")
                    }
                }
                """);
    }

    @Test
    @DisplayName("parentheses around the subject are optional")
    void parentheses_around_the_subject_are_optional() {
        assertAccepted("""
                method := "GET"
                switch (method) {
                    case "GET" {
                        TextWriter(Stdio.stdout()).writeLine(("read") .. "")
                    }
                }
                """);
    }

    @Test
    void regex_cases_come_after_literal_cases() {
        assertAccepted("""
                path := "/api/users/42"
                switch path {
                    case "root" {
                        TextWriter(Stdio.stdout()).writeLine(("root") .. "")
                    }
                    case regex "^/api/users/[0-9]+$" {
                        TextWriter(Stdio.stdout()).writeLine(("user") .. "")
                    }
                    case regex "^/api/.*" {
                        TextWriter(Stdio.stdout()).writeLine(("api") .. "")
                    }
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("static") .. "")
                    }
                }
                """);
    }

    @Test
    void integer_byte_and_boolean_subjects_are_switchable() {
        assertAccepted("""
                code := 2
                switch code {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("one") .. "")
                    }
                    case -1 {
                        TextWriter(Stdio.stdout()).writeLine(("minus one") .. "")
                    }
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("other") .. "")
                    }
                }
                """);
        // A literal is Integer; a Byte value comes from a typed array or a Byte parameter.
        assertAccepted("""
                bytes := Byte[2] {0, 127}
                for small in bytes {
                    switch small {
                        case 0 {
                            TextWriter(Stdio.stdout()).writeLine(("zero") .. "")
                        }
                        case 127 {
                            TextWriter(Stdio.stdout()).writeLine(("max") .. "")
                        }
                    }
                }
                """);
        assertAccepted("""
                flag := true
                switch flag {
                    case true {
                        TextWriter(Stdio.stdout()).writeLine(("yes") .. "")
                    }
                    case false {
                        TextWriter(Stdio.stdout()).writeLine(("no") .. "")
                    }
                }
                """);
    }

    @Test
    void a_switch_may_be_nested_and_live_in_a_loop_or_a_method() {
        assertAccepted("""
                words := String[2] {"a", "b"}
                for word in words {
                    switch word {
                        case "a" {
                            switch word {
                                case "a" {
                                    TextWriter(Stdio.stdout()).writeLine(("inner") .. "")
                                }
                            }
                        }
                        default {
                            TextWriter(Stdio.stdout()).writeLine(("outer default") .. "")
                        }
                    }
                }
                """);
        assertAccepted("""
                type Router {
                    route := "/"

                    pub func Router(route: String) {
                        self.route = route
                    }

                    pub func kind() -> String {
                        switch self.route {
                            case regex "^/api/.*" {
                                return "api"
                            }
                            default {
                                return "page"
                            }
                        }
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((Router("/api/x").kind()) .. "")
                """);
    }

    @Test
    void a_switch_without_a_default_is_accepted() {
        assertAccepted("""
                method := "GET"
                switch method {
                    case "GET" {
                        TextWriter(Stdio.stdout()).writeLine(("read") .. "")
                    }
                }
                """);
    }

    @Test
    void a_switch_branch_may_return() {
        assertAccepted("""
                func classify(code: Integer) -> String {
                    switch code {
                        case 200 {
                            return "ok"
                        }
                        default {
                            return "other"
                        }
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((classify(200)) .. "")
                """);
    }

    // ------------------------------------------------------------------ rejected

    @Test
    void only_string_integer_byte_and_boolean_subjects_are_switchable() {
        assertRejected("""
                price := 1.5
                switch price {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("one") .. "")
                    }
                }
                """);
        assertRejected("""
                type Point {
                    x := 0
                }
                point := Point()
                switch point {
                    case point {
                        TextWriter(Stdio.stdout()).writeLine(("point") .. "")
                    }
                }
                """);
        assertRejected("""
                var empty
                switch empty {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("one") .. "")
                    }
                }
                """);
    }

    @Test
    void a_case_label_must_be_a_literal() {
        assertRejected("""
                limit := 2
                code := 2
                switch code {
                    case limit {
                        TextWriter(Stdio.stdout()).writeLine(("limit") .. "")
                    }
                }
                """);
        assertRejected("""
                method := "GET"
                switch method {
                    case method {
                        TextWriter(Stdio.stdout()).writeLine(("same") .. "")
                    }
                }
                """);
    }

    @Test
    void a_case_label_must_match_the_subject_type() {
        assertRejected("""
                method := "GET"
                switch method {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("number") .. "")
                    }
                }
                """);
        assertRejected("""
                code := 1
                switch code {
                    case "one" {
                        TextWriter(Stdio.stdout()).writeLine(("text") .. "")
                    }
                }
                """);
        assertRejected("""
                flag := true
                switch flag {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("number") .. "")
                    }
                }
                """);
    }

    @Test
    void duplicate_case_labels_are_rejected() {
        String duplicateString = """
                method := "GET"
                switch method {
                    case "GET" {
                        TextWriter(Stdio.stdout()).writeLine(("first") .. "")
                    }
                    case "GET" {
                        TextWriter(Stdio.stdout()).writeLine(("second") .. "")
                    }
                }
                """;
        String duplicateNumber = """
                code := 1
                switch code {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("first") .. "")
                    }
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("second") .. "")
                    }
                }
                """;
        String duplicateWrittenDifferently = """
                code := 1
                switch code {
                    case 1 {
                        TextWriter(Stdio.stdout()).writeLine(("first") .. "")
                    }
                    case 01 {
                        TextWriter(Stdio.stdout()).writeLine(("second") .. "")
                    }
                }
                """;
        assertRejected(duplicateString);
        assertRejected(duplicateNumber);
        assertRejected(duplicateWrittenDifferently);
    }

    @Test
    @DisplayName("a Byte case outside the signed range can never match")
    void a_byte_case_outside_the_byte_range_is_rejected() {
        assertRejected("""
                bytes := Byte[1] {0}
                for small in bytes {
                    switch small {
                        case 128 {
                            TextWriter(Stdio.stdout()).writeLine(("impossible") .. "")
                        }
                    }
                }
                """);
        assertRejected("""
                bytes := Byte[1] {0}
                for small in bytes {
                    switch small {
                        case -129 {
                            TextWriter(Stdio.stdout()).writeLine(("impossible") .. "")
                        }
                    }
                }
                """);
    }

    @Test
    void a_regex_case_needs_a_string_subject_and_a_valid_pattern() {
        assertRejected("""
                code := 1
                switch code {
                    case regex "[0-9]" {
                        TextWriter(Stdio.stdout()).writeLine(("digit") .. "")
                    }
                }
                """);
        assertRejected("""
                path := "/api"
                switch path {
                    case regex "^/api(" {
                        TextWriter(Stdio.stdout()).writeLine(("broken") .. "")
                    }
                }
                """);
    }

    @Test
    @DisplayName("a literal case after a regex case is rejected, so source order is the whole rule")
    void a_literal_case_may_not_follow_a_regex_case() {
        assertRejected("""
                path := "/api"
                switch path {
                    case regex "^/api.*" {
                        TextWriter(Stdio.stdout()).writeLine(("api") .. "")
                    }
                    case "/api" {
                        TextWriter(Stdio.stdout()).writeLine(("exact") .. "")
                    }
                }
                """);
    }

    @Test
    void a_regex_label_must_be_a_string_literal() {
        Diagnostics diagnostics = check("""
                path := "/api"
                switch path {
                    case regex 5 {
                        TextWriter(Stdio.stdout()).writeLine(("number") .. "")
                    }
                }
                """);
        assertThat(diagnostics.all()).isNotEmpty();
    }

    @Test
    void an_invalid_pattern_reports_its_own_diagnostic_code() {
        Diagnostics diagnostics = check("""
                path := "/api"
                switch path {
                    case regex "[a-" {
                        TextWriter(Stdio.stdout()).writeLine(("broken") .. "")
                    }
                }
                """);
        assertThat(diagnostics.all())
                .extracting(dev.solvik.compiler.diagnostic.Diagnostic::code)
                .containsExactly(TypeChecker.INVALID_REGEX);
    }

    @Test
    void a_switch_may_declare_default_only_once_and_last() {
        assertRejected("""
                method := "GET"
                switch method {
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("first") .. "")
                    }
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("second") .. "")
                    }
                }
                """);
        assertRejected("""
                method := "GET"
                switch method {
                    default {
                        TextWriter(Stdio.stdout()).writeLine(("first") .. "")
                    }
                    case "GET" {
                        TextWriter(Stdio.stdout()).writeLine(("late") .. "")
                    }
                }
                """);
    }
}
