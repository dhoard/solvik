package mainexitcode

// Main.run returns the process exit status. Java's System.exit takes an int,
// so the entry point returns Integer (the 32-bit Solvik type) and the
// generated wrapper forwards it unchanged.

struct Main {

    public func run(args: String...): Integer {
        System.getOut().println("exiting with 7")
        return 7
    }
}
