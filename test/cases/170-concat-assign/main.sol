package concatassign

struct Acc {

    static mutable log: String = "start"

    mutable label: String

    public func new(): Self {
        return Self { label: "", }
    }

    // Instance field target: self.label ..= v
    public func extend(self, v: String): Void {
        self.label ..= v
    }

    public func tag(self): String {
        return self.label
    }

    // Static field target: Self.log ..= v
    public func note(v: String): Void {
        Self.log ..= v
    }

    public func logText(): String {
        return Self.log
    }
}

struct Main {

    public func run(args: String...): Long {
        // Local target.
        let mutable s: String = "ab"
        s ..= "cd"
        System.getOut().println(s)

        // Non-String operands are formatted like any `..` operand.
        s ..= 5
        System.getOut().println(s)

        // Instance field target.
        let a: Acc = Acc.new()
        a.extend("x")
        a.extend("y")
        System.getOut().println(a.tag())

        // Static field target.
        Acc.note("+")
        Acc.note("!")
        System.getOut().println(Acc.logText())

        return 0
    }
}
