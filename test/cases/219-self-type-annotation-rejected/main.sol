package selftypeannotation

struct A {
    // `self` carries the declaring struct's type implicitly and must not be
    // given a type annotation.
    pub func f(self: Long): Long { return 0 }
}

struct Main {
    pub func run(args: String...): Integer { return 0 }
}
