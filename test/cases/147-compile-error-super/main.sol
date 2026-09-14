package usesuper

struct Base {
    public func new(): Self { return Self {} }
    public func value(self): Long { return 1 }
}

struct Sub {
    public func go(self): Long { return super.value() }
}

struct Main { public func run(args: String...): Integer { return 0 } }
