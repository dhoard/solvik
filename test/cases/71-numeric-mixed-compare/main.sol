module nummixed

// Regression: comparing an Long with a Double picked the comparison opcode
// from the left operand alone, so `1 == 1.5` crashed at runtime instead of
// promoting to Double semantics.

class Main {

    public static run(args: String...): Long {
        if 1 == 1.5 { stdout.println("eq") } else { stdout.println("ne") }
        if 2 == 2.0 { stdout.println("eq2") } else { stdout.println("ne2") }
        if 1 < 1.5 { stdout.println("lt") } else { stdout.println("ge") }
        if 1.5 > 1 { stdout.println("gt") } else { stdout.println("le") }
        if 3 != 3.0 { stdout.println("ne3") } else { stdout.println("eq3") }
        if 2.5 <= 2 { stdout.println("le2") } else { stdout.println("gt2") }
        // mixed types through variables and match patterns
        let a: Long = 7
        let b: Double = 7.0
        if a == b { stdout.println("var-eq") } else { stdout.println("var-ne") }
        let f: Double = 2.5
        match f {
            1.0 => stdout.println("one")
            2.5 => stdout.println("two")
            _ => stdout.println("other")
        }
        return 0
    }
}
