package delegconflict

trait A { func value(self): String }
trait B { func value(self): String }

struct AImpl implements A { public func value(self): String { return "a" } public func new(): Self { return Self {} } }
struct BImpl implements B { public func value(self): String { return "b" } public func new(): Self { return Self {} } }

struct X implements A, B {
    a: AImpl
    b: BImpl
    delegate A to a
    delegate B to b
    public func new(): Self { return Self { a: AImpl.new(), b: BImpl.new(), } }
}

struct Main { public func run(args: String...): Integer { return 0 } }
