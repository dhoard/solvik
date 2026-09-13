package selftest

struct Base {

    public static func new(): Self {
        return Self {}
    }

    public func fluent(self): Self {
        return self
    }
}

struct Main {

    public static func run(args: String...): Long {
        // base factory
        let a: Base = Base.new()
        // fluent instance return
        let f: Base = a.fluent()
        if f != a { return 1 }
        System.getOut().println("ok")
        return 0
    }
}
