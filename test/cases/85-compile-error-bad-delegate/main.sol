package baddelegate

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

    thing: Thing

    // ERROR: 'missing' is not a field of Wrapper.
    delegate Named to missing
}

struct Main {

    public func run(args: String...): Integer {
        return 0
    }
}
