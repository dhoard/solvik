package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.testsupport.PackagedLibrary;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Generated source must compile against the JDK alone, not a Solvik runtime JAR. */
class StandaloneJavaBackendTest {

    @Test
    void generates_a_jdk_only_program_without_a_runtime_or_type_interface() {
        Map<String, String> files = new SolvikCompiler("program.sol").transpile("""
                type Item {
                    pub name := "item"
                    pub func Item() { }
                }
                items := Integer[2] {1, 2}
                TextWriter(Stdio.stdout()).writeLine((Item().name) .. (" ") .. (items.size))
                """);

        assertThat(PackagedLibrary.programFiles(files)).containsExactly("Item.java", "Solvik$Program.java");
        assertThat(files.get("Solvik$Program.java"))
                .contains("Arrays.<Integer>asList(1, 2)")
                .doesNotContain("Solvik$Runtime", "Solvik$Type", "com.helger");
        assertThat(files.get("Item.java"))
                .contains("final class Item")
                .doesNotContain("implements Solvik$Type", "com.helger");
    }

    @Test
    void translates_number_and_nested_array_to_jdk_reference_types() {
        Map<String, String> files = new SolvikCompiler("types.sol").transpile("""
                func sum(values: Number[]) -> Number {
                    return values[0]
                }
                items := Number[2] {Byte(3), 4.5}
                TextWriter(Stdio.stdout()).writeLine((items) .. "")
                """);
        assertThat(files.get("Solvik$Program.java"))
                .contains("List<Number>")
                .contains("Arrays.<Number>asList(");
    }
}
