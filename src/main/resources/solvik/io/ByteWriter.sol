// solvik ByteWriter — a byte-oriented writer borrowing a writable Channel.
//
// Ordinary Solvik source: there is no native member and no adapter entry for this type.
type ByteWriter {
    mut target: Channel? = Null

    pub func ByteWriter(channel: Channel) {
        target = channel
    }

    // Writes every byte or raises; Channel.writeAll owns the partial-write protocol.
    pub func write(bytes: Byte[]) {
        current := target
        if current == Null {
            raise Exception(IoError.Closed, "the byte writer is closed")
        }
        current.writeAll(bytes)
    }

    pub func flush() {
        current := target
        if current == Null {
            raise Exception(IoError.Closed, "the byte writer is closed")
        }
        current.flush()
    }

    // Flushes and releases only the writer; the borrowed channel stays open.
    pub func close() {
        current := target
        if current == Null {
            return
        }
        current.flush()
        target = Null
    }
}
