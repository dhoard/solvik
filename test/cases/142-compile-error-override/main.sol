package useoverride

interface Named { func name(self): String }

struct Person implements Named {
    override public name(): String { return "x" }
}

struct Main { public func run(args: String...): Long { return 0 } }
