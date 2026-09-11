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
        stdout.println(Counter.tick())
        stdout.println(Counter.tick())
        // Both instances observe the same shared slot.
        stdout.println(a.current())
        stdout.println(b.current())
        // A fresh instance still sees the persisted value.
        stdout.println(Counter.new().current())
        stdout.println(a.name())
        return 0
    }
}
