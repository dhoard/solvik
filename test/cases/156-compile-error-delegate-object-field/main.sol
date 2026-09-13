package objdeleg

interface Named {
    func name(self): String
}

struct W implements Named {
    // ERROR: `Object` does not guarantee conformance to `Named`.
    f: Object
    delegate Named to f
    public static func new(): Self {
        return Self { f: null, }
    }
}

struct Main {
    public static func run(args: String...): Long {
        return 0
    }
}
