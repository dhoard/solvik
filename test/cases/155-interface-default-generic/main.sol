package ifacedefgen

trait Boxed<T> {
    func value(self): T
    func dup(self): T {
        return value()
    }
}

struct L implements Boxed<Long> {
    public func new(): Self {
        return Self {}
    }
    public func value(self): Long {
        return 7
    }
}

struct Main {
    public func run(args: String...): Integer {
        // A generic trait default must type-check against its declared
        // signature and instantiate for the receiver.
        let b: L = L.new()
        let x: Long = b.dup() + 1
        System.getOut().println(x)
        let i: Boxed<Long> = b
        let y: Long = i.dup() + 1
        System.getOut().println(y)
        return 0
    }
}
