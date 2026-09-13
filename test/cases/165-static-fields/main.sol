package staticfields

// Static fields are struct-level state: declared once, initialized before
// Main.run in declaration order, shared by every instance and thread, and
// reachable only through type-qualified access from the declaring struct.

struct Counter {

    static count: Long = 0
    static mutable total: Long = 0
    static mutable cache: Map<String, Long> = {}
    static label: String = "counter"
    static limit: Long = 10

    public static func new(): Self {
        return Self {}
    }

    public static func tick(): Long {
        Self.total += 1
        if Self.total > Counter.limit {
            Counter.total = Counter.limit
        }
        return Self.total
    }

    public func current(self): Long {
        return Counter.total
    }

    public func name(self): String {
        return Self.label
    }
}

struct Main {

    public static func run(args: String...): Long {
        let a: Counter = Counter.new()
        let b: Counter = Counter.new()
        System.getOut().println(Counter.tick())
        System.getOut().println(Counter.tick())
        // Both instances observe the same shared slot.
        System.getOut().println(a.current())
        System.getOut().println(b.current())
        // A fresh instance still sees the persisted value.
        System.getOut().println(Counter.new().current())
        System.getOut().println(a.name())
        return 0
    }
}
