package baddelegate

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

    thing: Thing

    // ERROR: 'missing' is not a field of Wrapper.
    delegate Named to missing
}

struct Main {

    pub func run(args: String...): Integer {
        return 0
    }
}
