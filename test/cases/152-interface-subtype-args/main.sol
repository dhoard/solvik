package ifacesubargs

trait Source<T> {
    func get(self): T
}

// FixedSource is a non-generic refinement of Source<String>; a value
// statically typed as FixedSource must be assignable to Source<String>.
trait FixedSource extends Source<String> {
    func extra(self): Long
}

struct Impl implements FixedSource {
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

struct Main {
    pub func run(args: String...): Integer {
        let f: FixedSource = Impl.new()
        let s: Source<String> = f
        System.getOut().println(s.get())
        System.getOut().println(f.extra())
        return 0
    }
}
