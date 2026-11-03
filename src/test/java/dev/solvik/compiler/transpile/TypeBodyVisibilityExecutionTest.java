package dev.solvik.compiler.transpile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.SolvikCompiler;
import dev.solvik.compiler.testsupport.GeneratedProgram;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A root type's field and signature may reach the packaged library and types declared later, and
 * the generated Java runs them ({@code LANGUAGE.md} §8.1).
 */
@DisplayName("type-body name visibility executes")
class TypeBodyVisibilityExecutionTest {

    @TempDir
    Path directory;

    private String run(String source) throws Exception {
        return GeneratedProgram.run(new SolvikCompiler("Input.sol").transpile(source),
                directory.resolve("out"));
    }

    @Test
    @DisplayName("a root type holds a packaged list and constructs a later type and a module type")
    void a_root_type_body_uses_later_declarations() throws Exception {
        String output = run("""
                type Basket {
                    pub items := solvik::collection::List<Integer>()
                    pub origin := geo::Point(2, 3)
                    pub seal := Seal("root")

                    pub func Basket() { }

                    pub func absorb(source: solvik::collection::List<Integer>) -> Integer {
                        self.items.add(source.size())
                        return self.items.size() + source.size()
                    }
                }

                type Seal {
                    pub label := ""

                    pub func Seal(label: String) {
                        self.label = label
                    }
                }

                module geo {
                    pub type Point {
                        pub mut x := 0

                        pub mut y := 0

                        pub func Point(x: Integer, y: Integer) {
                            self.x = x
                            self.y = y
                        }
                    }
                }

                source := solvik::collection::List<Integer>()
                source.add(7)
                source.add(8)
                basket := Basket()
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("absorbed: ") .. (basket.absorb(source)))
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("origin: ") .. (basket.origin.x) .. (" ") .. (basket.origin.y) .. (" ") .. (basket.seal.label))
                """);
        assertThat(output).isEqualTo("absorbed: 3\norigin: 2 3 root\n");
    }
}
