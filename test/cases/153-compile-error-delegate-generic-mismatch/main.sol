package ifacedelegmismatch

interface Source<T> {
    get(): T
}

// RichSource only conforms to Source<String>, never Source<Long>.
interface RichSource extends Source<String> {
    extra(): Long
}

class R implements RichSource {
    public static new(): Self {
        return Self {}
    }
    public get(): String {
        return "x"
    }
    public extra(): Long {
        return 1
    }
}

class W implements Source<Long> {
    r: RichSource
    delegate Source<Long> to r
    public static new(): Self {
        return Self { r: R.new(), }
    }
}

class Main {
    public static run(args: String...): Long {
        return 0
    }
}
