package baddelegate

interface Named {

    func name(self): String
}

struct Thing implements Named {

    public static func new(): Self {
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

    public static func run(args: String...): Long {
        return 0
    }
}
