// solvik::io::TextReader — a stateful strict UTF-8 line reader over a readable Channel.
//
// The reader borrows its channel: `close()` releases only the reader's decoder and buffers.
module solvik {
    module io {
        pub type TextReader {
            pub native func TextReader(channel: Channel)

            // The next line without its terminator (LF, CRLF, or CR), "" for an empty line, the
            // final unterminated line at end of input, and Null only when no further characters
            // exist.
            pub native func readLine() -> String?

            pub native func close()
        }
    }
}
