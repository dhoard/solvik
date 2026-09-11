package objdeleg

interface Named {
    name(): String
}

class W implements Named {
    // ERROR: `Object` does not guarantee conformance to `Named`.
    f: Object
    delegate Named to f
    public static new(): Self {
        return Self { f: null, }
    }
}

class Main {
    public static run(args: String...): Long {
        return 0
    }
}
