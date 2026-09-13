package usesuper

struct Base {
    public static func new(): Self { return Self {} }
    public func value(self): Long { return 1 }
}

struct Sub {
    public func go(self): Long { return super.value() }
}

struct Main { public static func run(args: String...): Long { return 0 } }
