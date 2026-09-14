package delegconflict

trait A { func value(self): String }
trait B { func value(self): String }

struct AImpl implements A { pub func value(self): String { return "a" } pub func new(): Self { return Self {} } }
struct BImpl implements B { pub func value(self): String { return "b" } pub func new(): Self { return Self {} } }

struct X implements A, B {
    a: AImpl
    b: BImpl
    delegate A to a
    delegate B to b
    pub func new(): Self { return Self { a: AImpl.new(), b: BImpl.new(), } }
}

struct Main { pub func run(args: String...): Integer { return 0 } }
