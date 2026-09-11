package tvdeleg

interface Named {
    name(): String
}

class P implements Named {
    public static new(n: String): Self {
        return Self {}
    }
    public name(): String {
        return "p"
    }
}

// A type variable conforms to the delegated interface through its nominal
// constraint.
class W<T: Named> implements Named {
    f: T
    delegate Named to f
    public static new(f: T): Self {
        return Self { f: f, }
    }
}

class Main {
    public static run(args: String...): Long {
        let w: W<P> = W<P>.new(P.new("x"))
        stdout.println(w.name())
        return 0
    }
}
