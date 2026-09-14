package competingdefaults

trait A {

    func f(self): Long {
        return 1
    }
}

trait B extends A {

    func f(self): Long {
        return 2
    }
}

trait C extends A {

    func f(self): Long {
        return 3
    }
}

struct X implements B, C {

    pub func new(): Self {
        return Self {}
    }
}

struct Main {

    pub func run(args: String...): Integer {
        return 0
    }
}
