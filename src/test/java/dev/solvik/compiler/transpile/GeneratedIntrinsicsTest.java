package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.solvik.compiler.jir.JDecl;
import dev.solvik.compiler.jir.JExpr;
import dev.solvik.compiler.jir.JStmt;
import dev.solvik.compiler.jir.JType;
import dev.solvik.compiler.jir.JTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Structural support-member selection: reachable groups and their transitive dependencies. */
@DisplayName("generated support selection")
class GeneratedIntrinsicsTest {

    private static Map<String, List<JDecl.Member>> registry() {
        return GeneratedIntrinsics.registryFor(Map.of());
    }

    @Test
    @DisplayName("a conversion pulls in the failure, rendering, and error declarations it calls")
    void dependencies_are_discovered_from_the_declaration_ir() {
        Set<String> reachable = GeneratedIntrinsics.reachable(Set.of("conversion"), registry());

        assertThat(reachable).contains("conversion", "fail", "render", GeneratedNames.ERROR_CLASS);
        assertThat(reachable).doesNotContain("exit", "divide", "array", "type");
    }

    @Test
    @DisplayName("exit reaches exactly its transitive support closure")
    void exit_reaches_its_transitive_closure() {
        Set<String> reachable = GeneratedIntrinsics.reachable(Set.of("exit"), registry());

        assertThat(reachable).contains("exit", GeneratedNames.EXIT_CLASS, "fail", GeneratedNames.ERROR_CLASS,
                "toBigInteger",
                "toBigDecimal", "required", "conversion", "render", "isIntegerText",
                "isDecimalText");
        assertThat(reachable).doesNotContain("divide", "remainder", "array", "type", "hash");
    }

    @Test
    @DisplayName("an unused support group is never emitted")
    void unused_groups_are_not_reachable() {
        Set<String> reachable = GeneratedIntrinsics.reachable(Set.of("array"), registry());

        assertThat(reachable).containsExactly("array");
    }

    @Test
    @DisplayName("an unknown support name fails instead of emitting Java that cannot compile")
    void an_unknown_support_name_fails() {
        assertThatThrownBy(() -> GeneratedIntrinsics.reachable(Set.of("noSuchHelper"), registry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown support member noSuchHelper");
    }

    @Test
    @DisplayName("every support member name is reserved, so a user function cannot capture it")
    void support_member_names_are_reserved() {
        Set<String> methods = new java.util.TreeSet<>(registry().keySet());
        methods.removeAll(Set.of(GeneratedNames.ERROR_CLASS, GeneratedNames.EXIT_CLASS,
                GeneratedNames.EXCEPTION_CLASS));

        assertThat(GeneratedNames.supportMemberNames())
                .describedAs("a new support member needs an entry in GeneratedNames")
                .containsExactlyInAnyOrderElementsOf(methods);
    }

    @Test
    @DisplayName("a user function with a support member's name keeps a distinct Java name")
    void a_user_function_with_a_support_name_is_renamed() {
        assertThat(GeneratedNames.functionName("render")).isEqualTo("render$function");
        assertThat(GeneratedNames.functionName("toLong")).isEqualTo("toLong$function");
        assertThat(GeneratedNames.functionName("toString")).isEqualTo("toString$function");
        assertThat(GeneratedNames.functionName("area")).isEqualTo("area");
    }

    @Test
    @DisplayName("the type() dispatch is generated from the declared user types")
    void type_dispatch_uses_the_declared_types() {
        Map<String, Integer> declared = new java.util.LinkedHashMap<>();
        declared.put("Box", 1);
        declared.put("Pair", 2);
        Map<String, List<JDecl.Member>> registry = GeneratedIntrinsics.registryFor(declared);
        Set<String> reachable = GeneratedIntrinsics.reachable(Set.of("type"), registry);

        assertThat(reachable).containsExactly("type");
        assertThat(registry.get("type")).hasSize(1);
        JDecl.Method method = (JDecl.Method) registry.get("type").getFirst();
        List<JExpr.InstanceOf> tests = new ArrayList<>();
        for (JStmt statement : method.body()) {
            if (statement instanceof JStmt.If branch
                    && branch.condition() instanceof JExpr.InstanceOf test) {
                tests.add(test);
            }
        }
        assertThat(tests).extracting(JExpr.InstanceOf::tested).containsExactly(
                JTypes.listOfAny(),
                JTypes.generated("Box").withArguments(List.of(JType.Wildcard.any())),
                JTypes.generated("Pair").withArguments(List.of(
                        JType.Wildcard.any(), JType.Wildcard.any())));
    }
}
