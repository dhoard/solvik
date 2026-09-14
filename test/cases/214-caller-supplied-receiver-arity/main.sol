package callerreceiver

struct X {
    pub func new(): Self { return Self {} }
    pub func add(self, value: Long): Long { return value }
}

struct Main {
    pub func run(args: String...): Integer {
        let x: X = X.new()
        return x.add(x, 1)
    }
}
