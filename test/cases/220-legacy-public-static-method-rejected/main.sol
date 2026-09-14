package publicstaticmethod

struct A {
    // The `static` method modifier was removed. A method's kind is inferred
    // from whether its first parameter is `self`; `pub static func` is a
    // syntax error, not a legacy declaration.
    pub static func make(): Long { return 0 }
}

struct Main {
    pub func run(args: String...): Integer { return 0 }
}
