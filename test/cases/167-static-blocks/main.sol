package staticblocks

// A struct may declare at most one static block. A struct's static field
// initializers and its block form one unit that runs exactly once, lazily,
// immediately before the struct's first active use (static field access,
// static method call, or object construction). Inside the block, static
// members of the declaring struct resolve by bare name (no struct or Self
// qualifier); mutable static fields may be written.

struct Counter {

    static mutable total: Long = 1
    static limit: Long = 4

    static {
        // Field initializers have already run: total == 1, limit == 4.
        let mutable i: Long = 0
        while i < limit {
            total += 1
            i += 1
        }
    }

    public static func get(): Long {
        return Counter.total
    }
}

struct Ledger {

    // Actively uses Counter, so Counter initializes before Ledger's own
    // initialization continues.
    static mutable entry: Long = Counter.get()

    static {
        entry += 100
    }

    public static func get(): Long {
        return Ledger.entry
    }
}

struct NeverUsed {

    static {
        // Never actively used: this block must not run.
        System.getOut().println("never used")
    }
}

struct Main {

    public static func run(args: String...): Long {
        // Printed before any Ticker-style struct initializes: no static
        // block output may appear above this line.
        System.getOut().println("entering Main")
        // First active use of Ledger initializes Ledger, which actively
        // uses Counter and therefore initializes Counter first.
        System.getOut().println(Ledger.get())
        // Later accesses observe the already-initialized state; both
        // blocks ran exactly once.
        System.getOut().println(Counter.get())
        return 0
    }
}
