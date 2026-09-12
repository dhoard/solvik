package staticblocks

// A class may declare at most one static block. A class's static field
// initializers and its block form one unit that runs exactly once, lazily,
// immediately before the class's first active use (static field access,
// static method call, or object construction). Inside the block, static
// members of the declaring class resolve by bare name (no class or Self
// qualifier); mutable static fields may be written.

class Counter {

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

    public static get(): Long {
        return Counter.total
    }
}

class Ledger {

    // Actively uses Counter, so Counter initializes before Ledger's own
    // initialization continues.
    static mutable entry: Long = Counter.get()

    static {
        entry += 100
    }

    public static get(): Long {
        return Ledger.entry
    }
}

class NeverUsed {

    static {
        // Never actively used: this block must not run.
        System.out().println("never used")
    }
}

class Main {

    public static run(args: String...): Long {
        // Printed before any Ticker-style class initializes: no static
        // block output may appear above this line.
        System.out().println("entering Main")
        // First active use of Ledger initializes Ledger, which actively
        // uses Counter and therefore initializes Counter first.
        System.out().println(Ledger.get())
        // Later accesses observe the already-initialized state; both
        // blocks ran exactly once.
        System.out().println(Counter.get())
        return 0
    }
}
