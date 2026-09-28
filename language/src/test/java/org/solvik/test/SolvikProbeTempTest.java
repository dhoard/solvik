package org.solvik.test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
final class SolvikProbeTempTest {
    private static String run(String s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (Context c = Context.newBuilder("solvik").out(o).err(o).option("engine.WarnInterpreterOnly","false").allowAllAccess(true).build()) {
            Source src;
            try { src = Source.newBuilder("solvik", s, "t.sol").build(); }
            catch (IOException e) { throw new UncheckedIOException(e); }
            c.eval(src);
        }
        return o.toString(StandardCharsets.UTF_8);
    }
    private static void p(String l, String prog) {
        try { System.out.println("PROBE[" + l + "]: '" + run(prog).replace("\n","\\n") + "'"); }
        catch (Throwable t) { System.out.println("PROBE[" + l + "] THREW: " + t.getMessage()); }
    }
    @Test void run() {
        // widening at initializer site
        p("init-byte-f", "var x: Float = 1");
        // widening in arithmetic (Byte + Long -> Long)
        p("arith-b-l", "println(1 + 1L)");
        // widening in equality (Byte == Long)
        p("eq-b-l", "println(1 == 1L)");
        // widening in function argument
        p("arg-l-d", "func f(d: Double): Int { return 1 } println(f(1L))");
        // widening in return
        p("ret-b-f", "func f(): Float { return 1 } println(f())");
        // widening in collection element
        p("coll-f-d", "var m = Map<Double,Int>(); m[1.0] = 0");
    }
}
