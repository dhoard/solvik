package staticblocks

// A struct may declare at most one static block. A struct's static field
// initializers and its block form one unit that runs exactly once, lazily,
// immediately before the struct's first active use (static field access,
// static method call, or object construction). Inside the block, static
// members of the declaring struct resolve by bare name (no struct or Self
// qualifier); `var` static fields may be written.

struct Counter {

    static var total: Long = 1
    static limit: Long = 4

    static {
        // Field initializers have already run: total == 1, limit == 4.
        var i: Long = 0
        while i < limit {
            total += 1
            i += 1
        }
    }

    pub func get(): Long {
        return Self.total
    }
}

struct Ledger {

    // Actively uses Counter, so Counter initializes before Ledger's own
    // initialization continues.
    static var entry: Long = Counter.get()

    static {
        entry += 100
    }

    pub func get(): Long {
        return Self.entry
    }
}

struct NeverUsed {

    static {
        // Never actively used: this block must not run.
        System.getOut().println("never used")
    }
}

struct Main {

    pub func run(args: String...): Integer {
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
