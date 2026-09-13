package callerreceiver

struct X {
    public static func new(): Self { return Self {} }
    public func add(self, value: Long): Long { return value }
}

struct Main {
    public static func run(args: String...): Long {
        let x: X = X.new()
        return x.add(x, 1)
    }
}
