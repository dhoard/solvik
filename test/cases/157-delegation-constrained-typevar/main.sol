package tvdeleg

trait Named {
    func name(self): String
}

struct P implements Named {
    pub func new(n: String): Self {
        return Self {}
    }
    pub func name(self): String {
        return "p"
    }
}

// A type variable conforms to the delegated trait through its nominal
// constraint.
struct W<T: Named> implements Named {
    f: T
    delegate Named to f
    pub func new(f: T): Self {
        return Self { f: f, }
    }
}

struct Main {
    pub func run(args: String...): Integer {
        let w: W<P> = W<P>.new(P.new("x"))
        System.getOut().println(w.name())
        return 0
    }
}
