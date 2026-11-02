// solvik::io::Stdio — the three currently assigned standard channels.
//
// `Stdio` has only static operations and no constructor, so a program cannot create one.
// Setters replace a channel reference; the adapter publishes the replacement atomically.
module solvik {
    module io {
        pub type Stdio {
            static pub native func stdin() -> Channel
            static pub native func stdout() -> Channel
            static pub native func stderr() -> Channel

            // A readable, open channel is required; an invalid replacement leaves the old binding.
            static pub native func setStdin(channel: Channel)

            // A writable, open channel is required; an invalid replacement leaves the old
            // binding.
            static pub native func setStdout(channel: Channel)
            static pub native func setStderr(channel: Channel)
        }
    }
}
