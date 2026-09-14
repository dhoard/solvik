import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.solvik.transpiler.Ast;
import org.solvik.transpiler.IrOptimizer;
import org.solvik.transpiler.Lexer;
import org.solvik.transpiler.Parser;
import org.solvik.transpiler.SemanticAnalyzer;
import org.solvik.transpiler.SolvikLowerer;
import org.solvik.transpiler.SolvikProgram;
import org.solvik.transpiler.Token;
import org.solvik.transpiler.backend.JavaEmitter;
import org.solvik.transpiler.backend.JavaProgram;

/**
 * Warmed per-phase compiler timing for the Solvik-to-Java transpiler.
 *
 * <p>Run it with the transpiler classes on the class path:</p>
 *
 * <pre>
 * javac -cp target/classes -d /tmp benchmarks/PhaseBench.java
 * java -cp /tmp:target/classes PhaseBench source1.sol source2.sol
 * </pre>
 *
 * <p>All measurements happen inside a single JVM after warm-up, so they exclude
 * process start-up and JIT warm-up costs. Wall-clock process timings from
 * {@code benchmarks/run.sh} remain the authoritative end-to-end numbers.</p>
 */
public final class PhaseBench {
    private static final int WARMUP = 12;
    private static final int ITERATIONS = 31;

    private PhaseBench() {}

    public static void main(String[] args) throws Exception {
        System.out.printf("%-24s %9s %9s %10s %9s %8s %10s%n",
                "source", "lexer", "parser", "semantic", "emit", "total", "bytes");
        for (String arg : args) {
            Path path = Path.of(arg);
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (int i = 0; i < WARMUP; i++) {
                pipeline(path.toString(), source);
            }
            long[] lexer = new long[ITERATIONS];
            long[] parser = new long[ITERATIONS];
            long[] semantic = new long[ITERATIONS];
            long[] emit = new long[ITERATIONS];
            int bytes = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                long start = System.nanoTime();
                Lexer lexerInstance = new Lexer(path.toString(), source);
                List<Token> tokens = lexerInstance.tokenize();
                long afterLexer = System.nanoTime();
                Parser parserInstance = new Parser(tokens);
                Ast.CompilationUnit unit = parserInstance.parse(path.toString());
                long afterParser = System.nanoTime();
                SemanticAnalyzer.Model model = new SemanticAnalyzer().analyze(unit);
                long afterSemantic = System.nanoTime();
                String java = compile(model, path.toString());
                long afterEmit = System.nanoTime();
                lexer[i] = afterLexer - start;
                parser[i] = afterParser - afterLexer;
                semantic[i] = afterSemantic - afterParser;
                emit[i] = afterEmit - afterSemantic;
                bytes = java.length();
            }
            System.out.printf("%-24s %7.2fms %7.2fms %8.2fms %7.2fms %6.2fms %10d%n",
                    path.getFileName(), ms(lexer), ms(parser), ms(semantic), ms(emit),
                    ms(lexer) + ms(parser) + ms(semantic) + ms(emit), bytes);
        }
    }

    private static void pipeline(String file, String source) throws Exception {
        Lexer lexer = new Lexer(file, source);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Ast.CompilationUnit unit = parser.parse(file);
        SemanticAnalyzer.Model model = new SemanticAnalyzer().analyze(unit);
        compile(model, file);
    }

    private static String compile(SemanticAnalyzer.Model model, String file) {
        SolvikProgram program = new IrOptimizer().optimize(new SolvikLowerer(model).lower());
        return new JavaEmitter(new JavaProgram(program, "Bench", file)).emit();
    }

    private static double ms(long[] samples) {
        long[] copy = samples.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2] / 1e6;
    }
}
