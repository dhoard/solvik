package selftest

class Base {
    pub static new(): Self {
        return Self {}
    }

    pub fluent(): Self {
        return self
    }
}

class Sub extends Base {
    // inherited factory creates the subclass
}

class Main {
    pub static run(args: String...): Int {
        // base factory
        a: Base = Base::new()
        // inherited factory creating subclass
        s: Sub = Sub::new()
        // fluent instance return
        f: Base = a.fluent()
        if f != a { return 1 }
        stdout.println("ok")
        return 0
    }
}
