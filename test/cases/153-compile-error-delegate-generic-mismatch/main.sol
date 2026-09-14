package ifacedelegmismatch

trait Source<T> {
    func get(self): T
}

// RichSource only conforms to Source<String>, never Source<Long>.
trait RichSource extends Source<String> {
    func extra(self): Long
}

struct R implements RichSource {
    pub func new(): Self {
        return Self {}
    }
    pub func get(self): String {
        return "x"
    }
    pub func extra(self): Long {
        return 1
    }
}

struct W implements Source<Long> {
    r: RichSource
    delegate Source<Long> to r
    pub func new(): Self {
        return Self { r: R.new(), }
    }
}

struct Main {
    pub func run(args: String...): Integer {
        return 0
    }
}
