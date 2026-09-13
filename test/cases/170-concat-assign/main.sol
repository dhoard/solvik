package concatassign

struct Acc {

    static mutable log: String = "start"

    mutable label: String

    public static func new(): Self {
        return Self { label: "", }
    }

    // Instance field target: self.label ..= v
    public func extend(self, v: String): Void {
        self.label ..= v
    }

    public func tag(self): String {
        return self.label
    }

    // Static field target: Acc.log ..= v
    public static func note(v: String): Void {
        Acc.log ..= v
    }

    public static func logText(): String {
        return Acc.log
    }
}

struct Main {

    public static func run(args: String...): Long {
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
