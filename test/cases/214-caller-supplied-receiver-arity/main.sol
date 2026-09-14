package callerreceiver

struct X {
    public func new(): Self { return Self {} }
    public func add(self, value: Long): Long { return value }
}

struct Main {
    public func run(args: String...): Integer {
        let x: X = X.new()
        return x.add(x, 1)
    }
}
