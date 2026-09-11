package nummixed

// Regression: comparing an Long with a Double picked the comparison opcode
// from the left operand alone, so `1 == 1.5` crashed at runtime instead of
// promoting to Double semantics.

class Main {

    public static run(args: String...): Long {
        if 1 == 1.5 { System.out().println("eq") } else { System.out().println("ne") }
        if 2 == 2.0 { System.out().println("eq2") } else { System.out().println("ne2") }
        if 1 < 1.5 { System.out().println("lt") } else { System.out().println("ge") }
        if 1.5 > 1 { System.out().println("gt") } else { System.out().println("le") }
        if 3 != 3.0 { System.out().println("ne3") } else { System.out().println("eq3") }
        if 2.5 <= 2 { System.out().println("le2") } else { System.out().println("gt2") }
        // mixed types through variables and match patterns
        let a: Long = 7
        let b: Double = 7.0
        if a == b { System.out().println("var-eq") } else { System.out().println("var-ne") }
        let f: Double = 2.5
        match f {
            1.0 => System.out().println("one")
            2.5 => System.out().println("two")
            _ => System.out().println("other")
        }
        return 0
    }
}
