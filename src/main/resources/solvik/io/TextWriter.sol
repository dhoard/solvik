// solvik::io::TextWriter — a strict UTF-8 writer over a writable Channel.
//
// `writeLine` is ordinary Solvik: it appends the published platform terminator. `write`
// translates nothing and adds no terminator, so a terminator inside the caller's text stays
// exactly as written.
module solvik {
    module io {
        pub type TextWriter {
            // Binding surface, type-private: the platform's text line terminator.
            static native func lineSeparator() -> String

            // Read once at type initialization: LF on POSIX systems, CRLF on Windows. Immutable,
            // so it is read as TextWriter.EOL, never through an instance.
            static pub EOL := lineSeparator()

            pub native func TextWriter(channel: Channel)

            // Encodes and writes all bytes or raises; appends no terminator and normalizes no line
            // separator.
            pub native func write(text: String)

            pub func writeLine(text: String) {
                write(text .. EOL)
            }

            pub native func flush()
            pub native func close()
        }
    }
}
