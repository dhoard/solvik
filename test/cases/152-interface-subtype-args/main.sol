package ifacesubargs

interface Source<T> {
    get(): T
}

// FixedSource is a non-generic refinement of Source<String>; a value
// statically typed as FixedSource must be assignable to Source<String>.
interface FixedSource extends Source<String> {
    extra(): Long
}

class Impl implements FixedSource {
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

class Main {
    public static run(args: String...): Long {
        let f: FixedSource = Impl.new()
        let s: Source<String> = f
        stdout.println(s.get())
        stdout.println(f.extra())
        return 0
    }
}
