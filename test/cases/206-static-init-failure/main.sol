package staticinitfailure

// A failing static initializer is no longer a startup error: the struct
// initializes lazily at its first active use, and that use fails. The
// program terminates with a runtime error naming the failure.

struct A {

    static {
        throw Exception.new("init boom")
    }

    pub func get(): Integer {
        return 0
    }
}

struct Main {

    pub func run(args: String...): Integer {
        // First active use of A: initialization fails here.
        return A.get()
    }
}
