// solvik::io::ByteReader — a byte-oriented reader borrowing a readable Channel.
//
// Ordinary Solvik source: there is no native member and no adapter entry for this type.
module solvik {
    module io {
        pub type ByteReader {
            mut source: Channel? = Null

            pub func ByteReader(channel: Channel) {
                source = channel
            }

            // At most `maxBytes`; Null only at end of input. Channel.read owns EOF and range
            // rules.
            pub func read(maxBytes: Integer) -> Byte[]? {
                current := source
                if current == Null {
                    raise Exception(IoError.Closed, "the byte reader is closed")
                }
                return current.read(maxBytes)
            }

            // Releases only the reader; the borrowed channel stays open. Repeated close is
            // harmless.
            pub func close() {
                source = Null
            }
        }
    }
}
