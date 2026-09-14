package oneline

// Regression: block bodies whose first statement starts on the same line
// as the opening brace previously lost their first token (if/while/for/try
// consumed the '{' before parse_block swallowed the first body token).

struct Main {

    pub func run(args: String...): Integer {
        // one-line if
        if (true) { System.getOut().println("if-one-line") }
        // one-line if/else
        if (1 > 2) { System.getOut().println("bad") } else { System.getOut().println("if-else") }
        // one-line while
        var i: Long = 0
        while (i < 3) { System.getOut().println("while-" .. i); i += 1 }
        // one-line for over a list
        for x in [10, 20] { System.getOut().println("for-" .. x) }
        // one-line try/catch/finally
        try { throw Exception.new("boom") } catch (e: Exception) { System.getOut().println("catch") } finally { System.getOut().println("finally") }
        // one-line body starting with a declaration
        if (true) { var v: Long = 5; System.getOut().println("decl-" .. v) }
        System.getOut().println("done")
        return 0
    }
}
