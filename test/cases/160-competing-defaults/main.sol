package competingdefaults

interface A {

    func f(self): Long {
        return 1
    }
}

interface B extends A {

    func f(self): Long {
        return 2
    }
}

interface C extends A {

    func f(self): Long {
        return 3
    }
}

struct X implements B, C {

    public static func new(): Self {
        return Self {}
    }
}

struct Main {

    public static func run(args: String...): Long {
        return 0
    }
}
