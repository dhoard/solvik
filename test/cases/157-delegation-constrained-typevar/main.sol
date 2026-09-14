package tvdeleg

trait Named {
    func name(self): String
}

struct P implements Named {
    public func new(n: String): Self {
        return Self {}
    }
    public func name(self): String {
        return "p"
    }
}

// A type variable conforms to the delegated trait through its nominal
// constraint.
struct W<T: Named> implements Named {
    f: T
    delegate Named to f
    public func new(f: T): Self {
        return Self { f: f, }
    }
}

struct Main {
    public func run(args: String...): Long {
        let w: W<P> = W<P>.new(P.new("x"))
        System.getOut().println(w.name())
        return 0
    }
}
