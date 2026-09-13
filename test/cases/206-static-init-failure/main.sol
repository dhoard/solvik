package staticinitfailure

// A failing static initializer is no longer a startup error: the struct
// initializes lazily at its first active use, and that use fails. The
// program terminates with a runtime error naming the failure.

struct A {

    static {
        throw Exception.new("init boom")
    }

    public func get(): Long {
        return 0
    }
}

struct Main {

    public func run(args: String...): Long {
        // First active use of A: initialization fails here.
        return A.get()
    }
}
