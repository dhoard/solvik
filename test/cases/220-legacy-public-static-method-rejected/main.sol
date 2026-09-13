package publicstaticmethod

struct A {
    // The `static` method modifier was removed. A method's kind is inferred
    // from whether its first parameter is `self`; `public static func` is a
    // syntax error, not a legacy declaration.
    public static func make(): Long { return 0 }
}

struct Main {
    public func run(args: String...): Long { return 0 }
}
