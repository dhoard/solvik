package org.solvik.transpiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command-line entry point for {@code solvik <input.sol> <OutputClassName>}. */
public final class SolvikTranspiler {
    private SolvikTranspiler() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: solvik <input.sol> <OutputClassName>");
            System.exit(3);
        }
        Path input = Path.of(args[0]);
        String className = args[1];
        if (!Files.isRegularFile(input) || !input.getFileName().toString().endsWith(".sol")) {
            System.err.println("error: input must be an existing regular .sol file: " + input);
            System.exit(3);
        }
        if (!isJavaIdentifier(className) || isJavaKeyword(className)) {
            System.err.println("error: output class name is not a legal simple Java class name: " + className);
            System.exit(3);
        }
        try {
            String source = Files.readString(input, StandardCharsets.UTF_8);
            String javaSource = Transpiler.toJava(input.toString(), source, className, phaseListener());
            Transpiler.writeJavaSource(Path.of("."), className, javaSource);
        } catch (CompileException e) {
            for (Diagnostic d : e.diagnostics()) System.err.println(d);
            System.exit(1);
        } catch (InternalCompilerException e) {
            System.err.println("error: internal compiler error: " + e.getMessage());
            System.exit(3);
        } catch (IOException | RuntimeException e) {
            System.err.println("error: internal: " + e.getMessage());
            System.exit(3);
        }
    }

    private static Transpiler.PhaseListener phaseListener() {
        if (System.getenv("SOLVIK_JAVA_PHASES") == null) return null;
        return (name, nanos) -> System.err.printf("phase %s: %.3f ms%n", name, nanos / 1e6);
    }

    private static boolean isJavaKeyword(String value) {
        return switch (value) { case "abstract", "class", "public", "private", "protected", "static", "final", "void", "int", "long", "short", "byte", "float", "double", "boolean", "char", "if", "else", "while", "for", "switch", "case", "default", "return", "new", "this", "null", "true", "false", "package", "import", "interface", "enum", "extends", "implements", "throws", "throw", "try", "catch", "finally", "break", "continue", "do", "instanceof", "native", "strictfp", "synchronized", "transient", "volatile", "assert", "const", "goto", "var", "yield", "record", "sealed", "permits", "non-sealed", "module", "open", "requires", "transitive", "exports", "opens", "to", "uses", "provides", "with", "_" -> true; default -> false; };
    }

    private static boolean isJavaIdentifier(String value) {
        if (value.isEmpty()) return false;
        int first = value.codePointAt(0);
        if (!Character.isJavaIdentifierStart(first)) return false;
        for (int offset = Character.charCount(first); offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isJavaIdentifierPart(codePoint)) return false;
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}
