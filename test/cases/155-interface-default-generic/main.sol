package ifacedefgen

interface Boxed<T> {
    value(): T
    dup(): T {
        return value()
    }
}

class L implements Boxed<Long> {
    public static new(): Self {
        return Self {}
    }
    public value(): Long {
        return 7
    }
}

class Main {
    public static run(args: String...): Long {
        // A generic interface default must type-check against its declared
        // signature and instantiate for the receiver.
        let b: L = L.new()
        let x: Long = b.dup() + 1
        System.out().println(x)
        let i: Boxed<Long> = b
        let y: Long = i.dup() + 1
        System.out().println(y)
        return 0
    }
}
