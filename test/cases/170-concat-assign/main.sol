package concatassign

class Acc {

    static mutable log: String = "start"

    mutable label: String

    public static new(): Self {
        return Self { label: "", }
    }

    // Instance field target: self.label ..= v
    public extend(v: String): Void {
        self.label ..= v
    }

    public tag(): String {
        return self.label
    }

    // Static field target: Acc.log ..= v
    public static note(v: String): Void {
        Acc.log ..= v
    }

    public static logText(): String {
        return Acc.log
    }
}

class Main {

    public static run(args: String...): Long {
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
