package concatassign

struct Acc {

    static var log: String = "start"

    var label: String

    pub func new(): Self {
        return Self { label: "", }
    }

    // Instance field target: self.label ..= v
    pub func extend(self, v: String) {
        self.label ..= v
    }

    pub func tag(self): String {
        return self.label
    }

    // Static field target: Self.log ..= v
    pub func note(v: String) {
        Self.log ..= v
    }

    pub func logText(): String {
        return Self.log
    }
}

struct Main {

    pub func run(args: String...): Integer {
        // Local target.
        var s: String = "ab"
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
