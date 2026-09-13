package ifacedelegmismatch

interface Source<T> {
    func get(self): T
}

// RichSource only conforms to Source<String>, never Source<Long>.
interface RichSource extends Source<String> {
    func extra(self): Long
}

struct R implements RichSource {
    public func new(): Self {
        return Self {}
    }
    public func get(self): String {
        return "x"
    }
    public func extra(self): Long {
        return 1
    }
}

struct W implements Source<Long> {
    r: RichSource
    delegate Source<Long> to r
    public func new(): Self {
        return Self { r: R.new(), }
    }
}

struct Main {
    public func run(args: String...): Long {
        return 0
    }
}
