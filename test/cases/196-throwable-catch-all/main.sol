package catchall

struct Cancelled {
    pub func new(): Self {
        return Self {}
    }
}

struct Main {
    pub func run(args: String...): Integer {
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

trait Task {
    func go(self)
}

struct Doer implements Task {
    pub func new(): Self {
        return Self {}
    }
    pub func go(self) {}
}
