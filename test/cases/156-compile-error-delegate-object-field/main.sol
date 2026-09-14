package objdeleg

trait Named {
    func name(self): String
}

struct W implements Named {
    // ERROR: `Object` does not guarantee conformance to `Named`.
    f: Object
    delegate Named to f
    public func new(): Self {
        return Self { f: null, }
    }
}

struct Main {
    public func run(args: String...): Integer {
        return 0
    }
}
