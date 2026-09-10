module baddelegate

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

    thing: Thing

    // ERROR: 'missing' is not a field of Wrapper.
    delegate Named to missing
}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
