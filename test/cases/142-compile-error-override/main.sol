package useoverride

trait Named { func name(self): String }

struct Person implements Named {
    override public name(): String { return "x" }
}

struct Main { public func run(args: String...): Integer { return 0 } }
