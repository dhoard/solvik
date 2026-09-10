module duplicatedelegate

interface Named {

    name(): String
}

class Thing implements Named {

    public static new(): Self {
        return Self {}
    }

    public name(): String {
        return "x"
    }
}

class Wrapper implements Named {

    a: Thing
    b: Thing

    delegate Named to a
    // ERROR: interface 'Named' is already delegated.
    delegate Named to b

    public static new(): Self {
        return Self { a: Thing.new(), b: Thing.new(), }
    }
}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
