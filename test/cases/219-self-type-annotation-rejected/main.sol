package selftypeannotation

struct A {
    // `self` carries the declaring struct's type implicitly and must not be
    // given a type annotation.
    public func f(self: Long): Long { return 0 }
}

struct Main {
    public func run(args: String...): Long { return 0 }
}
