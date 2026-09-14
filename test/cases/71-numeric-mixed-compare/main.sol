package nummixed

// Regression: comparing an Long with a Double picked the comparison opcode
// from the left operand alone, so `1 == 1.5` crashed at runtime instead of
// promoting to Double semantics.

struct Main {

    public func run(args: String...): Integer {
        if 1 == 1.5 { System.getOut().println("eq") } else { System.getOut().println("ne") }
        if 2 == 2.0 { System.getOut().println("eq2") } else { System.getOut().println("ne2") }
        if 1 < 1.5 { System.getOut().println("lt") } else { System.getOut().println("ge") }
        if 1.5 > 1 { System.getOut().println("gt") } else { System.getOut().println("le") }
        if 3 != 3.0 { System.getOut().println("ne3") } else { System.getOut().println("eq3") }
        if 2.5 <= 2 { System.getOut().println("le2") } else { System.getOut().println("gt2") }
        // mixed types through variables and match patterns
        let a: Long = 7
        let b: Double = 7.0
        if a == b { System.getOut().println("var-eq") } else { System.getOut().println("var-ne") }
        let f: Double = 2.5
        match f {
            1.0 => System.getOut().println("one")
            2.5 => System.getOut().println("two")
            _ => System.getOut().println("other")
        }
        return 0
    }
}
