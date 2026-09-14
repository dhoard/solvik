package objdeleg

trait Named {
    func name(self): String
}

struct W implements Named {
    // ERROR: `Object` does not guarantee conformance to `Named`.
    f: Object
    delegate Named to f
    pub func new(): Self {
        return Self { f: null, }
    }
}

struct Main {
    pub func run(args: String...): Integer {
        return 0
    }
}
