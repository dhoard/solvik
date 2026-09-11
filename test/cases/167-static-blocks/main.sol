package staticblocks

// A class may declare at most one static block. It runs exactly once,
// after all of the class's static field initializers and before Main.run,
// in class declaration order. Inside the block, static members of the
// declaring class resolve by bare name (no class or Self qualifier);
// mutable static fields may be written.

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

    static mutable entry: Long = Counter.get()

    static {
        entry += 100
    }

    public static get(): Long {
        return Ledger.entry
    }
}

class Main {

    public static run(args: String...): Long {
        // Counter: 1 + 4 increments = 5; Ledger: 5 + 100 = 105.
        stdout.println(Counter.get())
        stdout.println(Ledger.get())
        return 0
    }
}
