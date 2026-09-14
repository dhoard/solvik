package entryreturnlong

// The entry point is the process exit status, which Java types as int.
// Returning Long is therefore rejected with C131.

struct Main {

    public func run(args: String...): Long {
        return 0
    }
}
