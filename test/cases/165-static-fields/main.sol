package staticfields

// Static fields are class-level state: declared once, initialized before
// Main.run in declaration order, shared by every instance and thread, and
// reachable only through type-qualified access from the declaring class.

class Counter {

    static count: Long = 0
    static mutable total: Long = 0
    static mutable cache: Map<String, Long> = {}
    static label: String = "counter"
    static limit: Long = 10

    public static new(): Self {
        return Self {}
    }

    public static tick(): Long {
        Self.total += 1
        if Self.total > Counter.limit {
            Counter.total = Counter.limit
        }
        return Self.total
    }

    public current(): Long {
        return Counter.total
    }

    public name(): String {
        return Self.label
    }
}

class Main {

    public static run(args: String...): Long {
        let a: Counter = Counter.new()
        let b: Counter = Counter.new()
        System.out().println(Counter.tick())
        System.out().println(Counter.tick())
        // Both instances observe the same shared slot.
        System.out().println(a.current())
        System.out().println(b.current())
        // A fresh instance still sees the persisted value.
        System.out().println(Counter.new().current())
        System.out().println(a.name())
        return 0
    }
}
