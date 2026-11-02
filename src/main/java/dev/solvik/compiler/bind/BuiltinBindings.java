package dev.solvik.compiler.bind;

import dev.solvik.compiler.types.SolvikType;
import java.util.List;
import java.util.Map;

/**
 * The compiler-owned registry of bundled {@code native} declarations
 * ({@code plans/SOLVIK_IO_DESIGN.md} §6.4).
 *
 * <p>A native declaration has a Solvik signature and no body. This registry is the single source of
 * truth that maps each signature to the generated adapter entrypoint that implements it, plus the
 * conversion applied at that argument or result boundary. Resolution is exact: owner type, member
 * name, static/instance designation, parameter types, and return type must all match, and a missing
 * or mismatched entry is a build error rather than a reflective guess.
 *
 * <p>Keys use the written Solvik spellings a diagnostic shows ({@code Integer}, {@code Byte[]},
 * {@code Channel}, {@code String}), so a failure names the signature the author wrote.
 */
public final class BuiltinBindings {

    /** How a value crosses the Solvik/Java boundary at an adapter call. */
    public enum Conversion {
        /** The value already has the adapter's Java carrier. */
        NONE,
        /** A Solvik {@code Channel} value: the adapter receives its opaque payload. */
        PAYLOAD_ARGUMENT,
        /** A {@code Channel}-returning binding: the payload is wrapped in a {@code Channel}. */
        PAYLOAD_RESULT
    }

    /**
     * One resolved adapter entrypoint.
     *
     * @param adapter    the adapter method name inside the generated adapter class
     * @param parameters the conversion applied to each declared parameter, in order
     * @param result     the conversion applied to the adapter's return value
     * @param resultType the exact written return type, {@code Void} for a declaration without one
     */
    public record Entry(String adapter, List<Conversion> parameters, Conversion result,
                        String resultType) {

        public Entry {
            parameters = List.copyOf(parameters);
        }
    }

    /** An exact native signature key. */
    public record Key(String owner, String member, boolean isStatic, List<String> parameters) {

        public Key {
            parameters = List.copyOf(parameters);
        }
    }

    private static final Map<Key, Entry> ENTRIES = Map.ofEntries(
            entry("Channel", "bytes", true, List.of(), new Entry("memory", List.of(),
                    Conversion.PAYLOAD_RESULT, "Channel")),
            entry("Channel", "read", false, List.of("Integer"),
                    new Entry("read", List.of(Conversion.NONE), Conversion.NONE, "Byte[]?")),
            entry("Channel", "writeSome", false, List.of("Byte[]", "Integer", "Integer"),
                    new Entry("write", List.of(Conversion.NONE, Conversion.NONE, Conversion.NONE),
                            Conversion.NONE, "Integer")),
            entry("Channel", "flush", false, List.of(),
                    new Entry("flush", List.of(), Conversion.NONE, "Void")),
            entry("Channel", "close", false, List.of(),
                    new Entry("close", List.of(), Conversion.NONE, "Void")),
            entry("Channel", "isOpen", false, List.of(),
                    new Entry("isOpen", List.of(), Conversion.NONE, "Boolean")),
            entry("Channel", "canRead", false, List.of(),
                    new Entry("canRead", List.of(), Conversion.NONE, "Boolean")),
            entry("Channel", "canWrite", false, List.of(),
                    new Entry("canWrite", List.of(), Conversion.NONE, "Boolean")),
            entry("Stdio", "stdin", true, List.of(),
                    new Entry("stdin", List.of(), Conversion.PAYLOAD_RESULT, "Channel")),
            entry("Stdio", "stdout", true, List.of(),
                    new Entry("stdout", List.of(), Conversion.PAYLOAD_RESULT, "Channel")),
            entry("Stdio", "stderr", true, List.of(),
                    new Entry("stderr", List.of(), Conversion.PAYLOAD_RESULT, "Channel")),
            entry("Stdio", "setStdin", true, List.of("Channel"),
                    new Entry("setStdin", List.of(Conversion.PAYLOAD_ARGUMENT), Conversion.NONE, "Void")),
            entry("Stdio", "setStdout", true, List.of("Channel"),
                    new Entry("setStdout", List.of(Conversion.PAYLOAD_ARGUMENT), Conversion.NONE, "Void")),
            entry("Stdio", "setStderr", true, List.of("Channel"),
                    new Entry("setStderr", List.of(Conversion.PAYLOAD_ARGUMENT), Conversion.NONE, "Void")),
            entry("TextReader", "TextReader", false, List.of("Channel"),
                    new Entry("openReader", List.of(Conversion.PAYLOAD_ARGUMENT),
                            Conversion.NONE, "Void")),
            entry("TextReader", "readLine", false, List.of(),
                    new Entry("readLine", List.of(), Conversion.NONE, "String?")),
            entry("TextReader", "close", false, List.of(),
                    new Entry("closeReader", List.of(), Conversion.NONE, "Void")),
            entry("TextWriter", "TextWriter", false, List.of("Channel"),
                    new Entry("openWriter", List.of(Conversion.PAYLOAD_ARGUMENT),
                            Conversion.NONE, "Void")),
            entry("TextWriter", "write", false, List.of("String"),
                    new Entry("writeText", List.of(Conversion.NONE), Conversion.NONE, "Void")),
            entry("TextWriter", "flush", false, List.of(),
                    new Entry("flushWriter", List.of(), Conversion.NONE, "Void")),
            entry("TextWriter", "close", false, List.of(),
                    new Entry("closeWriter", List.of(), Conversion.NONE, "Void")),
            entry("TextWriter", "lineSeparator", true, List.of(),
                    new Entry("lineSeparator", List.of(), Conversion.NONE, "String")),
            entry("File", "openReadChannel", true, List.of("String"),
                    new Entry("openFileRead", List.of(Conversion.NONE),
                            Conversion.PAYLOAD_RESULT, "Channel")),
            entry("File", "openWriteChannel", true, List.of("String"),
                    new Entry("openFileWrite", List.of(Conversion.NONE),
                            Conversion.PAYLOAD_RESULT, "Channel")),
            entry("File", "openAppendChannel", true, List.of("String"),
                    new Entry("openFileAppend", List.of(Conversion.NONE),
                            Conversion.PAYLOAD_RESULT, "Channel")),
            entry("File", "exists", true, List.of("String"),
                    new Entry("fileExists", List.of(Conversion.NONE), Conversion.NONE,
                            "Boolean")),
            entry("File", "delete", true, List.of("String"),
                    new Entry("fileDelete", List.of(Conversion.NONE), Conversion.NONE,
                            "Boolean")));

    private BuiltinBindings() {
    }

    /**
     * The adapter entrypoint for an exact native signature, or {@code null} when none is registered.
     *
     * @param owner      the declaring type's simple name ({@code Channel})
     * @param member     the declared function name ({@code writeSome})
     * @param isStatic   whether the declaration belongs to the type rather than an instance
     * @param parameters each declared parameter's written Solvik type
     */
    public static Entry resolve(String owner, String member, boolean isStatic,
            List<SolvikType> parameters) {
        return ENTRIES.get(new Key(owner, member, isStatic,
                parameters.stream().map(SolvikType::toString).toList()));
    }

    private static Map.Entry<Key, Entry> entry(String owner, String member, boolean isStatic,
            List<String> parameters, Entry entry) {
        return Map.entry(new Key(owner, member, isStatic, parameters), entry);
    }
}
