package ifacesubargs

interface Source<T> {
    func get(self): T
}

// FixedSource is a non-generic refinement of Source<String>; a value
// statically typed as FixedSource must be assignable to Source<String>.
interface FixedSource extends Source<String> {
    func extra(self): Long
}

struct Impl implements FixedSource {
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

struct Main {
    public func run(args: String...): Long {
        let f: FixedSource = Impl.new()
        let s: Source<String> = f
        System.getOut().println(s.get())
        System.getOut().println(f.extra())
        return 0
    }
}
