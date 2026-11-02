// solvik::io::Channel — an opaque, byte-oriented I/O endpoint.
//
// The public surface is ordinary Solvik: `write` and `writeAll` are real Solvik bodies.
// The bodyless `native` members are compiler bindings implemented by the generated
// adapter; only a packaged resource may declare one (plans/SOLVIK_IO_DESIGN.md §6).
module solvik {
    module io {
        pub type Channel {
            // A new duplex in-memory channel: writes append, reads consume from the front. It gives
            // tests and the language corpus a deterministic endpoint without a filesystem.
            static pub native func bytes() -> Channel

            // At most `maxBytes` bytes; Null only at end of input.
            pub native func read(maxBytes: Integer) -> Byte[]?

            // The binding surface for writing a range of an array without copying it at the
            // boundary.
            native func writeSome(bytes: Byte[], offset: Integer, length: Integer) -> Integer

            // Reports the bytes accepted, which may be fewer than requested.
            pub func write(bytes: Byte[]) -> Integer {
                return writeSome(bytes, 0, bytes.size)
            }

            // Continues until every byte is accepted, and fails rather than spinning on zero
            // progress.
            pub func writeAll(bytes: Byte[]) {
                mut written := 0
                while written < bytes.size {
                    sent := writeSome(bytes, written, bytes.size - written)
                    if sent <= 0 {
                        raise Exception(IoError.Io, "write made no progress")
                    }
                    written = written + sent
                }
            }

            pub native func flush()
            pub native func close()
            pub native func isOpen() -> Boolean
            pub native func canRead() -> Boolean
            pub native func canWrite() -> Boolean
        }
    }
}
