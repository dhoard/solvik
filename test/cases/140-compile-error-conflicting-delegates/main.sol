package delegconflict

interface A { func value(self): String }
interface B { func value(self): String }

struct AImpl implements A { public func value(self): String { return "a" } public static func new(): Self { return Self {} } }
struct BImpl implements B { public func value(self): String { return "b" } public static func new(): Self { return Self {} } }

struct X implements A, B {
    a: AImpl
    b: BImpl
    delegate A to a
    delegate B to b
    public static func new(): Self { return Self { a: AImpl.new(), b: BImpl.new(), } }
}

struct Main { public static func run(args: String...): Long { return 0 } }
