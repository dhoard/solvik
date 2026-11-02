package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/**
 * Member visibility and mutability (§2, "Visibility and mutability").
 *
 * <p>A member is private and immutable unless declared otherwise: {@code pub} makes it readable
 * from outside, {@code mut} makes it writable after construction, and {@code pub mut} allows both
 * from outside.
 */
@DisplayName("Type checker / member visibility and mutability")
class MemberAccessTest {

    private static final String ACCOUNT = """
            type Account {
                pub owner := "ana"
                pub mut balance := 0
                mut attempts := 0
                mut frozen := false

                pub func Account(owner: String) {
                    self.owner = owner
                    self.balance = 0
                    self.attempts = 0
                    self.frozen = false
                }

                pub func deposit(amount: Integer) {
                    self.balance = self.balance + amount
                    self.attempts = self.attempts + 1
                }

                pub func attempts() -> Integer {
                    return self.attempts
                }

                pub func freeze() {
                    self.frozen = true
                }
            }
            """;

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

    private static void assertRejected(String source, String code) {
        assertThat(check(source).all())
                .describedAs("expected %s for: %s", code, source)
                .extracting(Diagnostic::code)
                .contains(code);
    }

    @Test
    void a_public_member_is_readable_from_outside() {
        assertAccepted(ACCOUNT + "account := Account(\"bo\")\nTextWriter(Stdio.stdout()).writeLine((account.owner) .. \"\")\n");
    }

    @Test
    void a_pub_mut_member_is_writable_from_outside() {
        assertAccepted(ACCOUNT + """
                account := Account("bo")
                account.balance = 10
                account.balance = account.balance + 1
                TextWriter(Stdio.stdout()).writeLine((account.balance) .. "")
                """);
    }

    @Test
    void a_private_member_is_not_readable_or_writable_from_outside() {
        assertRejected(ACCOUNT + "account := Account(\"bo\")\nTextWriter(Stdio.stdout()).writeLine((account.frozen) .. \"\")\n",
                TypeChecker.TYPE_ERROR);
        assertRejected(ACCOUNT + """
                account := Account("bo")
                account.frozen = true
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("pub without mut is readable but immutable from outside")
    void a_public_immutable_member_rejects_outside_writes() {
        assertAccepted(ACCOUNT + "account := Account(\"bo\")\nTextWriter(Stdio.stdout()).writeLine((account.owner) .. \"\")\n");
        assertRejected(ACCOUNT + """
                account := Account("bo")
                account.owner = "cy"
                """, TypeChecker.IMMUTABLE_MEMBER);
    }

    @Test
    @DisplayName("an immutable member may still be set while the constructor runs")
    void construction_may_set_any_member() {
        assertAccepted(ACCOUNT + "TextWriter(Stdio.stdout()).writeLine((Account(\"bo\").owner) .. \"\")\n");
    }

    @Test
    void a_method_may_only_change_mutable_members() {
        assertAccepted(ACCOUNT + "TextWriter(Stdio.stdout()).writeLine((Account(\"bo\").attempts()) .. \"\")\n");
        assertRejected("""
                type Sealed {
                    state := 0

                    pub func Sealed() {
                        self.state = 0
                    }

                    pub func change() {
                        self.state = 1
                    }
                }
                """, TypeChecker.IMMUTABLE_MEMBER);
    }

    @Test
    void append_to_a_member_follows_the_same_rules() {
        String tag = """
                type Tag {
                    pub mut label := "a"
                    fixed := "b"

                    pub func Tag() {
                        self.label = "a"
                        self.fixed = "b"
                    }

                    pub func rename() {
                        self.label ..= "!"
                    }
                }
                """;
        assertAccepted(tag + """
                value := Tag()
                value.label ..= "?"   // public and mutable, so it may be appended from outside
                TextWriter(Stdio.stdout()).writeLine((value.label) .. "")
                """);
        assertRejected(tag + """
                value := Tag()
                value.fixed ..= "?"
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                type SealedTag {
                    fixed := "b"

                    pub func SealedTag() {
                        self.fixed = "b"
                    }

                    pub func break_it() {
                        self.fixed ..= "!"
                    }
                }
                """, TypeChecker.IMMUTABLE_MEMBER);
    }

    @Test
    void a_public_mutable_member_of_any_type_keeps_its_type() {
        assertRejected(ACCOUNT + """
                account := Account("bo")
                account.balance = "many"
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a method is private unless declared 'pub func'")
    void a_method_is_private_unless_published() {
        String type = """
                type Service {
                    mut calls := 0

                    pub func Service() {
                        self.calls = 0
                    }

                    pub func run() {
                        self.step()
                    }

                    func step() {
                        self.calls = self.calls + 1
                    }
                }
                """;
        assertAccepted(type + """
                service := Service()
                service.run()
                """);
        assertRejected(type + """
                service := Service()
                service.step()
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a constructor is private unless declared 'pub func', and there is no implicit one")
    void a_constructor_is_private_unless_published() {
        assertRejected("""
                type Hidden {
                    func Hidden() { }
                }
                hidden := Hidden()
                """, TypeChecker.TYPE_ERROR);
        assertAccepted("""
                type Shown {
                    pub func Shown() { }
                }
                shown := Shown()
                """);
        // A type may construct itself privately from its own body.
        assertAccepted("""
                type Factory {
                    pub func Factory() { }

                    pub func make() -> Factory {
                        return Factory()
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((Factory().make().type()) .. "")
                """);
        // No constructor at all still means the type cannot be built.
        assertRejected("""
                type Empty {
                    pub value := 1
                }
                empty := Empty()
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a contract override must be public, and type() is final")
    void contract_overrides_are_checked() {
        assertRejected("""
                type Widget {
                    pub func Widget() { }

                    func toString() -> String {
                        return "widget"
                    }
                }
                """, TypeChecker.TYPE_ERROR);
        assertAccepted("""
                type Widget {
                    pub func Widget() { }

                    pub func toString() -> String {
                        return "widget"
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((Widget()) .. "")
                """);
        assertRejected("""
                type Widget {
                    pub func Widget() { }

                    pub func type() -> String {
                        return "Widget"
                    }
                }
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a member is named plainly inside the body, and self. is needed only on conflict")
    void member_names_are_plain_inside_the_body() {
        String counter = """
                type Counter {
                    mut total := 0
                    label := "counter"

                    pub func Counter(label: String) {
                        self.label = label
                        total = 0
                    }

                    pub func add(amount: Integer) {
                        total = total + amount
                        bump()
                    }

                    func bump() {
                        total = total + 1
                    }

                    pub func doubled() -> Integer {
                        mut total := 100
                        return total + self.total
                    }

                    pub func report() -> String {
                        return label .. "=" .. total
                    }
                }
                """;
        assertAccepted(counter + """
                mut counter := Counter("hits")
                counter.add(2)
                TextWriter(Stdio.stdout()).writeLine((counter.report()) .. (" ") .. (counter.doubled()))
                """);
    }

    @Test
    @DisplayName("a local or parameter wins over a member of the same name")
    void a_local_wins_over_a_member() {
        assertRejected("""
                type Shadowed {
                    mut total := 0

                    pub func Shadowed() {
                        total = 0
                    }

                    pub func wrong() -> String {
                        total := "text"
                        return total + 1
                    }
                }
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_bare_member_write_follows_the_member_rules() {
        assertRejected("""
                type Frozen {
                    total := 0

                    pub func Frozen() {
                        total = 0
                    }

                    pub func change() {
                        total = 1
                    }
                }
                """, TypeChecker.IMMUTABLE_MEMBER);
    }

    @Test
    void a_bare_name_outside_a_type_body_is_still_unknown() {
        assertRejected("""
                type Holder {
                    pub item := 1

                    pub func Holder() { }
                }
                TextWriter(Stdio.stdout()).writeLine((item) .. "")
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    void a_bare_call_reaches_a_private_method_inside_the_body() {
        assertAccepted("""
                type Quiet {
                    pub func Quiet() { }

                    pub func open() -> Integer {
                        return hidden()
                    }

                    func hidden() -> Integer {
                        return 1
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((Quiet().open()) .. "")
                """);
    }

    @Test
    @DisplayName("pub and mut are only valid on type members")
    void member_keywords_are_rejected_outside_a_type_body() {
        assertThat(check("pub x := 1").all()).isNotEmpty();
        assertThat(check("func f(mut n: Integer) { }").all()).isNotEmpty();
        assertThat(check("""
                type T {
                    x := 1
                    pub func T() { }
                    pub func f() {
                        y := 2
                        TextWriter(Stdio.stdout()).writeLine((y) .. "")
                    }
                }
                """).all()).isEmpty();
    }

    @Test
    void a_member_keyword_may_not_repeat() {
        assertThat(check("""
                type T {
                    pub pub x := 1
                    pub func T() { }
                }
                """).all()).isNotEmpty();
        assertThat(check("""
                type T {
                    mut mut x := 1
                    pub func T() { }
                }
                """).all()).isNotEmpty();
    }

    @Test
    void a_member_access_on_a_non_type_reports_a_type_error() {
        assertRejected("a := 1\nTextWriter(Stdio.stdout()).writeLine((a.field) .. \"\")\n", TypeChecker.TYPE_ERROR);
        assertRejected("items := Integer[1] {1}\nTextWriter(Stdio.stdout()).writeLine((items.field) .. \"\")\n",
                TypeChecker.TYPE_ERROR);
    }

    @Test
    void an_unknown_member_names_both_the_type_and_the_field() {
        Diagnostics diagnostics = check(ACCOUNT + "account := Account(\"bo\")\n"
                + "TextWriter(Stdio.stdout()).writeLine((account.nope) .. \"\")\n");
        assertThat(diagnostics.format("<t>"))
                .contains("Account")
                .contains("nope");
    }
}
