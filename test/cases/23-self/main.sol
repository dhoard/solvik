package selftest

class Base {

    public static new(): Self {
        return Self {}
    }

    public fluent(): Self {
        return self
    }
}

class Main {

    public static run(args: String...): Long {
        // base factory
        let a: Base = Base.new()
        // fluent instance return
        let f: Base = a.fluent()
        if f != a { return 1 }
        System.out().println("ok")
        return 0
    }
}
