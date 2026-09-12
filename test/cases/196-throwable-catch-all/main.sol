package catchall

class Cancelled {
    public static new(): Self {
        return Self {}
    }
}

class Main {
    public static run(args: String...): Long {
        // Throwable is the catch-all: every thrown value conforms to it.
        try {
            throw Exception.new("boom")
        } catch (t: Throwable) {
            System.out().println("any")
        }
        // Custom class values are throwable too.
        try {
            throw Cancelled.new()
        } catch (t: Throwable) {
            System.out().println("custom")
        }
        // Interface-typed values are throwable as well.
        try {
            let i: Task = Doer.new()
            throw i
        } catch (t: Throwable) {
            System.out().println("iface")
        }
        return 0
    }
}

interface Task {
    go(): Void
}

class Doer implements Task {
    public static new(): Self {
        return Self {}
    }
    public go(): Void {}
}
