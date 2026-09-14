package duplicatedelegate

trait Named {

    func name(self): String
}

struct Thing implements Named {

    pub func new(): Self {
        return Self {}
    }

    pub func name(self): String {
        return "x"
    }
}

struct Wrapper implements Named {

    a: Thing
    b: Thing

    delegate Named to a
    // ERROR: trait 'Named' is already delegated.
    delegate Named to b

    pub func new(): Self {
        return Self { a: Thing.new(), b: Thing.new(), }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        return 0
    }
}
