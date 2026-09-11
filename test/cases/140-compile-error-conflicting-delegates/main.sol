package delegconflict

interface A { value(): String }
interface B { value(): String }

class AImpl implements A { public value(): String { return "a" } public static new(): Self { return Self {} } }
class BImpl implements B { public value(): String { return "b" } public static new(): Self { return Self {} } }

class X implements A, B {
    a: AImpl
    b: BImpl
    delegate A to a
    delegate B to b
    public static new(): Self { return Self { a: AImpl.new(), b: BImpl.new(), } }
}

class Main { public static run(args: String...): Long { return 0 } }
