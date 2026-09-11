package oneline

// Regression: block bodies whose first statement starts on the same line
// as the opening brace previously lost their first token (if/while/for/try
// consumed the '{' before parse_block swallowed the first body token).

class Main {

    public static run(args: String...): Long {
        // one-line if
        if (true) { System.out().println("if-one-line") }
        // one-line if/else
        if (1 > 2) { System.out().println("bad") } else { System.out().println("if-else") }
        // one-line while
        let mutable i: Long = 0
        while (i < 3) { System.out().println("while-" .. i); i += 1 }
        // one-line for over a list
        for x in [10, 20] { System.out().println("for-" .. x) }
        // one-line try/catch/finally
        try { throw "boom" } catch (e) { System.out().println("catch") } finally { System.out().println("finally") }
        // one-line body starting with a declaration
        if (true) { let mutable v: Long = 5; System.out().println("decl-" .. v) }
        System.out().println("done")
        return 0
    }
}
