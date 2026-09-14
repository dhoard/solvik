package useoverride

trait Named { func name(self): String }

struct Person implements Named {
    override pub name(): String { return "x" }
}

struct Main { pub func run(args: String...): Integer { return 0 } }
