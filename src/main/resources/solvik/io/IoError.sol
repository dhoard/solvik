// solvik::io::IoError — the codes carried by an I/O Exception.
//
// Solvik has one exception type, so the I/O categories are published as Integer codes a program
// compares against `error.getCode()`. `IoError` has no constructor, so it is only a namespace.
module solvik {
    module io {
        pub type IoError {
            static pub Io := 5001
            static pub Closed := 5002
            static pub Unsupported := 5003
            static pub Encoding := 5004
            static pub InvalidArgument := 5005
        }
    }
}
