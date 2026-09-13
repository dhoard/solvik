package catchall

struct Cancelled {
    public static func new(): Self {
        return Self {}
    }
}

struct Main {
    public static func run(args: String...): Long {
        // Throwable is the catch-all: every thrown value conforms to it.
        try {
            throw Exception.new("boom")
        } catch (t: Throwable) {
            System.getOut().println("any")
        }
        // Custom struct values are throwable too.
        try {
            throw Cancelled.new()
        } catch (t: Throwable) {
            System.getOut().println("custom")
        }
        // Interface-typed values are throwable as well.
        try {
            let i: Task = Doer.new()
            throw i
        } catch (t: Throwable) {
            System.getOut().println("iface")
        }
        return 0
    }
}

interface Task {
    func go(self): Void
}

struct Doer implements Task {
    public static func new(): Self {
        return Self {}
    }
    public func go(self): Void {}
}
