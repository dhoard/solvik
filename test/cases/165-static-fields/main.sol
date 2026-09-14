package staticfields

// Static fields are struct-level state: declared once, initialized before
// Main.run in declaration order, shared by every instance and thread, and
// reachable only through Self-qualified access from the declaring struct.

struct Counter {

    static count: Long = 0
    static var total: Long = 0
    static var cache: Map<String, Long> = {}
    static label: String = "counter"
    static limit: Long = 10

    pub func new(): Self {
        return Self {}
    }

    pub func tick(): Long {
        Self.total += 1
        if Self.total > Self.limit {
            Self.total = Self.limit
        }
        return Self.total
    }

    pub func current(self): Long {
        return Self.total
    }

    // Chained member access on a reference-typed static goes through the
    // Self.-qualified read.
    pub func remember(key: String, value: Long): Long {
        Self.cache.put(key, value)
        return Self.cache.get(key) ?? -1
    }

    pub func name(self): String {
        return Self.label
    }
}

struct Main {

    pub func run(args: String...): Integer {
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
        System.getOut().println(Counter.remember("a", 42))
        return 0
    }
}
