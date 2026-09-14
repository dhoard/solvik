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

    public func new(): Self {
        return Self {}
    }
}

struct Main {

    public func run(args: String...): Long {
        return 0
    }
}
