// solvik File — an owning file handle exposing a byte Channel.
//
// Only the inherently platform-specific filesystem operations are native; the ownership,
// open-mode, close, and error rules are ordinary Solvik source. A File owns its channel.
type File {
    mut handle: Channel? = Null
    mut filePath := ""

    // Type-private: values come from the open factories, never from user code.
    func File(channel: Channel, path: String) {
        handle = channel
        filePath = path
    }

    // Bundled bindings: each open returns an owned single-direction channel.
    static native func openReadChannel(path: String) -> Channel
    static native func openWriteChannel(path: String) -> Channel
    static native func openAppendChannel(path: String) -> Channel

    static pub native func exists(path: String) -> Boolean
    static pub native func delete(path: String) -> Boolean

    // Creates the file when absent and truncates it when present.
    static pub func openWrite(path: String) -> File {
        return File(openWriteChannel(path), path)
    }

    // Requires an existing readable file.
    static pub func openRead(path: String) -> File {
        return File(openReadChannel(path), path)
    }

    // Creates the file when absent and appends when present.
    static pub func openAppend(path: String) -> File {
        return File(openAppendChannel(path), path)
    }

    pub func channel() -> Channel {
        current := handle
        if current == Null {
            raise Exception(IoError.Closed, "the file is closed")
        }
        return current
    }

    pub func path() -> String {
        return filePath
    }

    pub func isOpen() -> Boolean {
        return handle != Null
    }

    pub func close() {
        current := handle
        if current == Null {
            return
        }
        current.close()
        handle = Null
    }
}
