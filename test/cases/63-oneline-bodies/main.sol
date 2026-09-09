module oneline

// Regression: block bodies whose first statement starts on the same line
// as the opening brace previously lost their first token (if/while/for/try
// consumed the '{' before parse_block swallowed the first body token).

class Main {

    public static run(args: String...): Long {
        // one-line if
        if (true) { stdout.println("if-one-line") }
        // one-line if/else
        if (1 > 2) { stdout.println("bad") } else { stdout.println("if-else") }
        // one-line while
        mutable i: Long = 0
        while (i < 3) { stdout.println("while-" .. i); i += 1 }
        // one-line for over a list
        for x in [10, 20] { stdout.println("for-" .. x) }
        // one-line try/catch/finally
        try { throw "boom" } catch (e) { stdout.println("catch") } finally { stdout.println("finally") }
        // one-line body starting with a declaration
        if (true) { mutable v: Long = 5; stdout.println("decl-" .. v) }
        stdout.println("done")
        return 0
    }
}
