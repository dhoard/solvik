package diamonddefaults

// The most-specific default wins: B's default of m() must shadow A's,
// even though both reach the class through a diamond.
interface A {
    m(): String {
        return "A"
    }
}

interface B extends A {
    m(): String {
        return "B"
    }
}

interface D1 extends B {
}

interface D2 extends B {
}

class C implements D1, D2 {
    public static new(): Self {
        return Self {}
    }
}

class Main {
    public static run(args: String...): Long {
        let c: C = C.new()
        stdout.println(c.m())
        let d: D1 = c
        stdout.println(d.m())
        return 0
    }
}
