package diamonddefaults

// The most-specific default wins: B's default of m() must shadow A's,
// even though both reach the struct through a diamond.
trait A {
    func m(self): String {
        return "A"
    }
}

trait B extends A {
    func m(self): String {
        return "B"
    }
}

trait D1 extends B {
}

trait D2 extends B {
}

struct C implements D1, D2 {
    public func new(): Self {
        return Self {}
    }
}

struct Main {
    public func run(args: String...): Integer {
        let c: C = C.new()
        System.getOut().println(c.m())
        let d: D1 = c
        System.getOut().println(d.m())
        return 0
    }
}
