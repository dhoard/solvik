package selftest

struct Base {

    pub func new(): Self {
        return Self {}
    }

    pub func fluent(self): Self {
        return self
    }
}

struct Main {

    pub func run(args: String...): Integer {
        // base factory
        let a: Base = Base.new()
        // fluent instance return
        let f: Base = a.fluent()
        if f != a { return 1 }
        System.getOut().println("ok")
        return 0
    }
}
