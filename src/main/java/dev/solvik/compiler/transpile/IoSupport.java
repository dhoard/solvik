package dev.solvik.compiler.transpile;

/**
 * The embedded Java definition of the generated native-binding adapter.
 *
 * <p>Bundled `.sol` definitions declare bodyless {@code native} members; a generated program may
 * depend only on the JDK, so the implementations cannot live in a compiler-shipped runtime class.
 * The transpiler therefore emits this adapter as its own generated source file,
 * {@code solvik/Solvik$Io.java}, and only when the program reaches at least one binding. It uses
 * JDK types and the program's generated {@code $solvikException}; its name contains {@code $},
 * which no Solvik identifier can spell, so it is never part of the Solvik surface.
 *
 * <p>The adapter is deliberately self-contained: standard handles, synchronized {@code Stdio}
 * bindings, a duplex in-memory channel, strict UTF-8 text conversion, CR/LF/CRLF line recognition,
 * flush-on-write, and reader/writer close that never closes the borrowed channel. Failures throw
 * the generated {@code Exception} with the codes documented in {@code plans/SOLVIK_IO_DESIGN.md} §8.
 */
public final class IoSupport {

    /** The fixed codes carried by the generated {@code Exception} an I/O failure raises. */
    public static final int IO_ERROR = 5001;
    public static final int CLOSED_ERROR = 5002;
    public static final int UNSUPPORTED_ERROR = 5003;
    public static final int ENCODING_ERROR = 5004;
    public static final int INVALID_ARGUMENT_ERROR = 5005;

    private IoSupport() {
    }

    /** The complete generated adapter source, emitted only for a program that reaches a binding. */
    public static String adapterSource() {
        return SOURCE;
    }

    private static final String SOURCE = """
            package solvik;

            import java.io.FileInputStream;
            import java.io.FileOutputStream;
            import java.io.Flushable;
            import java.io.IOException;
            import java.io.InputStream;
            import java.io.InputStreamReader;
            import java.io.OutputStream;
            import java.io.Reader;
            import java.nio.ByteBuffer;
            import java.nio.CharBuffer;
            import java.nio.charset.CharacterCodingException;
            import java.nio.charset.CharsetDecoder;
            import java.nio.charset.CharsetEncoder;
            import java.nio.charset.CodingErrorAction;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.InvalidPathException;
            import java.nio.file.Path;
            import java.util.ArrayList;
            import java.util.List;

            /**
             * Generated adapter for the bundled native bindings; not a Solvik-visible type.
             *
             * <p>This file is emitted only when a program reaches an I/O binding. It depends on the
             * JDK and on the program's own generated {@code $solvikException} type, never on a
             * Solvik runtime library.
             */
            public final class Solvik$Io {

                private static final int IO_ERROR = 5001;
                private static final int CLOSED_ERROR = 5002;
                private static final int UNSUPPORTED_ERROR = 5003;
                private static final int ENCODING_ERROR = 5004;
                private static final int INVALID_ARGUMENT_ERROR = 5005;
                private static final char CARRIAGE_RETURN = (char) 13;
                private static final char LINE_FEED = (char) 10;
                private static final int NO_PENDING_CHARACTER = -2;
                private static final int MAXIMUM_CHUNK = 1 << 20;

                private static Handle standardInput = new Handle(System.in, null, null, true);
                private static Handle standardOutput =
                        new Handle(null, System.out, System.out, true);
                private static Handle standardError =
                        new Handle(null, System.err, System.err, true);

                private Solvik$Io() {
                }

                /* ------------------------------------------------------------ handles */

                private static final class Handle {
                    private final InputStream in;
                    private final OutputStream out;
                    private final Flushable flushable;
                    private final boolean standard;
                    private boolean closed;

                    Handle(InputStream in, OutputStream out, Flushable flushable, boolean standard) {
                        this.in = in;
                        this.out = out;
                        this.flushable = flushable;
                        this.standard = standard;
                    }
                }

                private static final class MemoryBuffer {
                    private byte[] data = new byte[64];
                    private int size;
                    private int position;

                    void append(int value) {
                        if (size == data.length) {
                            byte[] larger = new byte[data.length * 2];
                            System.arraycopy(data, 0, larger, 0, size);
                            data = larger;
                        }
                        data[size] = (byte) value;
                        size++;
                    }

                    void append(byte[] source, int offset, int length) {
                        for (int index = 0; index < length; index++) {
                            append(source[offset + index]);
                        }
                    }
                }

                private static final class MemoryInput extends InputStream {
                    private final MemoryBuffer buffer;

                    MemoryInput(MemoryBuffer buffer) {
                        this.buffer = buffer;
                    }

                    @Override
                    public int read() {
                        if (buffer.position >= buffer.size) {
                            return -1;
                        }
                        int value = buffer.data[buffer.position] & 0xFF;
                        buffer.position++;
                        return value;
                    }

                    @Override
                    public int read(byte[] target, int offset, int length) {
                        if (length == 0) {
                            return 0;
                        }
                        if (buffer.position >= buffer.size) {
                            return -1;
                        }
                        int available = Math.min(length, buffer.size - buffer.position);
                        System.arraycopy(buffer.data, buffer.position, target, offset, available);
                        buffer.position += available;
                        return available;
                    }
                }

                private static final class MemoryOutput extends OutputStream {
                    private final MemoryBuffer buffer;

                    MemoryOutput(MemoryBuffer buffer) {
                        this.buffer = buffer;
                    }

                    @Override
                    public void write(int value) {
                        buffer.append(value);
                    }

                    @Override
                    public void write(byte[] source, int offset, int length) {
                        buffer.append(source, offset, length);
                    }
                }

                /** A new duplex in-memory channel: writes append, reads consume from the front. */
                public static Object memory() {
                    MemoryBuffer buffer = new MemoryBuffer();
                    return new Handle(new MemoryInput(buffer), new MemoryOutput(buffer), null,
                            false);
                }

                /* ------------------------------------------------------- standard I/O */

                public static synchronized Object stdin() {
                    return standardInput;
                }

                public static synchronized Object stdout() {
                    return standardOutput;
                }

                public static synchronized Object stderr() {
                    return standardError;
                }

                public static synchronized void setStdin(Object payload) {
                    Handle candidate = handle(payload);
                    requireOpen(candidate);
                    if (candidate.in == null) {
                        throw unsupported("standard input requires a readable channel");
                    }
                    standardInput = candidate;
                }

                public static synchronized void setStdout(Object payload) {
                    Handle candidate = handle(payload);
                    requireOpen(candidate);
                    if (candidate.out == null) {
                        throw unsupported("standard output requires a writable channel");
                    }
                    standardOutput = candidate;
                }

                public static synchronized void setStderr(Object payload) {
                    Handle candidate = handle(payload);
                    requireOpen(candidate);
                    if (candidate.out == null) {
                        throw unsupported("standard error requires a writable channel");
                    }
                    standardError = candidate;
                }

                /* ----------------------------------------------------------- channels */

                public static List<Byte> read(Object payload, Integer maxBytes) {
                    Handle channel = handle(payload);
                    requireOpen(channel);
                    if (channel.in == null) {
                        throw unsupported("the channel is not readable");
                    }
                    if (maxBytes == null || maxBytes <= 0) {
                        throw invalid("a read length must be at least 1");
                    }
                    // A huge request would allocate before the endpoint can answer; a read may
                    // return fewer bytes, so a bounded chunk keeps the allocation safe.
                    byte[] chunk = new byte[Math.min(maxBytes, MAXIMUM_CHUNK)];
                    try {
                        int count = channel.in.read(chunk);
                        if (count < 0) {
                            return null;
                        }
                        if (count == 0) {
                            // A stream may report no progress; Solvik has no zero-length array,
                            // so read one byte rather than exposing an empty value.
                            int single = channel.in.read();
                            if (single < 0) {
                                return null;
                            }
                            List<Byte> one = new ArrayList<>(1);
                            one.add((byte) single);
                            return one;
                        }
                        List<Byte> bytes = new ArrayList<>(count);
                        for (int index = 0; index < count; index++) {
                            bytes.add(chunk[index]);
                        }
                        return bytes;
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                public static Integer write(Object payload, List<Byte> bytes, Integer offset,
                        Integer length) {
                    Handle channel = handle(payload);
                    requireOpen(channel);
                    if (channel.out == null) {
                        throw unsupported("the channel is not writable");
                    }
                    if (bytes == null || offset == null || length == null) {
                        throw invalid("a write requires bytes, an offset, and a length");
                    }
                    int start = offset;
                    int count = length;
                    if (start < 0 || count < 0 || start > bytes.size() - count) {
                        throw invalid("the write range is outside the byte array");
                    }
                    if (count == 0) {
                        return 0;
                    }
                    byte[] chunk = new byte[count];
                    for (int index = 0; index < count; index++) {
                        chunk[index] = bytes.get(start + index);
                    }
                    try {
                        channel.out.write(chunk);
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                    flushChannel(channel);
                    return count;
                }

                public static void flush(Object payload) {
                    Handle channel = handle(payload);
                    requireOpen(channel);
                    flushChannel(channel);
                }

                public static void close(Object payload) {
                    Handle channel = handle(payload);
                    if (channel.standard) {
                        throw unsupported("a standard channel is protected and cannot be closed");
                    }
                    if (channel.closed) {
                        return;
                    }
                    channel.closed = true;
                    try {
                        if (channel.in != null) {
                            channel.in.close();
                        }
                        if (channel.out != null) {
                            channel.out.close();
                        }
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                public static Boolean isOpen(Object payload) {
                    return !handle(payload).closed;
                }

                public static Boolean canRead(Object payload) {
                    return handle(payload).in != null;
                }

                public static Boolean canWrite(Object payload) {
                    return handle(payload).out != null;
                }

                /* ------------------------------------------------------------ files */

                public static Object openFileRead(String path) {
                    Path target = filePath(path);
                    try {
                        return new Handle(new FileInputStream(target.toFile()), null, null, false);
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                public static Object openFileWrite(String path) {
                    Path target = filePath(path);
                    try {
                        return new Handle(null, new FileOutputStream(target.toFile()), null,
                                false);
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                public static Object openFileAppend(String path) {
                    Path target = filePath(path);
                    try {
                        return new Handle(null, new FileOutputStream(target.toFile(), true), null,
                                false);
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                public static Boolean fileExists(String path) {
                    return Files.exists(filePath(path));
                }

                public static Boolean fileDelete(String path) {
                    Path target = filePath(path);
                    try {
                        if (Files.isDirectory(target)) {
                            throw io(new IOException("a directory is not a file: " + path));
                        }
                        return Files.deleteIfExists(target);
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                private static Path filePath(String value) {
                    if (value == null || value.isEmpty()) {
                        throw invalid("a file path is required");
                    }
                    try {
                        return Path.of(value);
                    } catch (InvalidPathException failure) {
                        throw invalid("invalid file path: " + failure.getMessage());
                    }
                }

                /* ------------------------------------------------------------- text */

                private static final class ReaderState {
                    private final Reader reader;
                    private boolean closed;
                    private int pending = NO_PENDING_CHARACTER;

                    ReaderState(Reader reader) {
                        this.reader = reader;
                    }
                }

                private static final class WriterState {
                    private final Handle channel;
                    private final CharsetEncoder encoder;
                    private boolean closed;

                    WriterState(Handle channel) {
                        this.channel = channel;
                        this.encoder = StandardCharsets.UTF_8.newEncoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT);
                    }
                }

                public static Object openReader(Object payload) {
                    Handle channel = handle(payload);
                    requireOpen(channel);
                    if (channel.in == null) {
                        throw unsupported("a text reader requires a readable channel");
                    }
                    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);
                    return new ReaderState(new InputStreamReader(channel.in, decoder));
                }

                public static String readLine(Object state) {
                    ReaderState reader = reader(state);
                    if (reader.closed) {
                        throw closed("the text reader is closed");
                    }
                    StringBuilder line = new StringBuilder();
                    try {
                        while (true) {
                            int next;
                            if (reader.pending != NO_PENDING_CHARACTER) {
                                next = reader.pending;
                                reader.pending = NO_PENDING_CHARACTER;
                            } else {
                                next = reader.reader.read();
                            }
                            if (next < 0) {
                                return line.length() == 0 ? null : line.toString();
                            }
                            if (next == LINE_FEED) {
                                return line.toString();
                            }
                            if (next == CARRIAGE_RETURN) {
                                int following = reader.reader.read();
                                if (following >= 0 && following != LINE_FEED) {
                                    reader.pending = following;
                                }
                                return line.toString();
                            }
                            line.append((char) next);
                        }
                    } catch (CharacterCodingException failure) {
                        throw encoding("the input is not valid UTF-8");
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                public static void closeReader(Object state) {
                    ReaderState reader = reader(state);
                    reader.closed = true;
                }

                public static Object openWriter(Object payload) {
                    Handle channel = handle(payload);
                    requireOpen(channel);
                    if (channel.out == null) {
                        throw unsupported("a text writer requires a writable channel");
                    }
                    return new WriterState(channel);
                }

                public static void writeText(Object state, String text) {
                    WriterState writer = writer(state);
                    if (writer.closed) {
                        throw closed("the text writer is closed");
                    }
                    if (text == null) {
                        throw invalid("a text write requires a String");
                    }
                    try {
                        ByteBuffer encoded = writer.encoder.encode(CharBuffer.wrap(text));
                        byte[] bytes = new byte[encoded.remaining()];
                        encoded.get(bytes);
                        writer.channel.out.write(bytes);
                    } catch (CharacterCodingException failure) {
                        throw encoding("the text cannot be encoded as UTF-8");
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                    flushChannel(writer.channel);
                }

                public static void flushWriter(Object state) {
                    WriterState writer = writer(state);
                    if (writer.closed) {
                        throw closed("the text writer is closed");
                    }
                    flushChannel(writer.channel);
                }

                public static void closeWriter(Object state) {
                    WriterState writer = writer(state);
                    if (writer.closed) {
                        return;
                    }
                    flushChannel(writer.channel);
                    writer.closed = true;
                }

                /** The platform line terminator, read once by the generated type's static field. */
                public static String lineSeparator() {
                    return System.lineSeparator();
                }

                /* ------------------------------------------------------------ support */

                private static Handle handle(Object payload) {
                    if (payload instanceof Handle handle) {
                        return handle;
                    }
                    throw new IllegalStateException("not a Solvik I/O handle");
                }

                private static ReaderState reader(Object state) {
                    if (state instanceof ReaderState reader) {
                        return reader;
                    }
                    throw new IllegalStateException("not a Solvik text reader");
                }

                private static WriterState writer(Object state) {
                    if (state instanceof WriterState writer) {
                        return writer;
                    }
                    throw new IllegalStateException("not a Solvik text writer");
                }

                private static void requireOpen(Handle channel) {
                    if (channel.closed) {
                        throw closed("the channel is closed");
                    }
                }

                private static void flushChannel(Handle channel) {
                    if (channel.flushable == null) {
                        return;
                    }
                    try {
                        channel.flushable.flush();
                    } catch (IOException failure) {
                        throw io(failure);
                    }
                }

                private static RuntimeException io(IOException failure) {
                    return new Solvik$Program.$solvikException(IO_ERROR,
                            "I/O operation failed: " + failure.getMessage());
                }

                private static RuntimeException closed(String message) {
                    return new Solvik$Program.$solvikException(CLOSED_ERROR, message);
                }

                private static RuntimeException unsupported(String message) {
                    return new Solvik$Program.$solvikException(UNSUPPORTED_ERROR, message);
                }

                private static RuntimeException encoding(String message) {
                    return new Solvik$Program.$solvikException(ENCODING_ERROR, message);
                }

                private static RuntimeException invalid(String message) {
                    return new Solvik$Program.$solvikException(INVALID_ARGUMENT_ERROR, message);
                }
            }
            """;
}
