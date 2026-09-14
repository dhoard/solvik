package duplicatedelegate

trait Named {

    func name(self): String
}

struct Thing implements Named {

    public func new(): Self {
        return Self {}
    }

    public func name(self): String {
        return "x"
    }
}

struct Wrapper implements Named {

    a: Thing
    b: Thing

    delegate Named to a
    // ERROR: trait 'Named' is already delegated.
    delegate Named to b

    public func new(): Self {
        return Self { a: Thing.new(), b: Thing.new(), }
    }
}

struct Main {

    public func run(args: String...): Long {
        return 0
    }
}
