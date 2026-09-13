package diamonddefaults

// The most-specific default wins: B's default of m() must shadow A's,
// even though both reach the struct through a diamond.
interface A {
    func m(self): String {
        return "A"
    }
}

interface B extends A {
    func m(self): String {
        return "B"
    }
}

interface D1 extends B {
}

interface D2 extends B {
}

struct C implements D1, D2 {
    public func new(): Self {
        return Self {}
    }
}

struct Main {
    public func run(args: String...): Long {
        let c: C = C.new()
        System.getOut().println(c.m())
        let d: D1 = c
        System.getOut().println(d.m())
        return 0
    }
}
