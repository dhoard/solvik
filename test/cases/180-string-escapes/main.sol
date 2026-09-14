package escapes

struct Main {

    pub func run(args: String...): Integer {
        // Every escape form, verified against its unicode equivalent.
        System.getOut().println("\n" == "\u000A")
        System.getOut().println("\t" == "\u0009")
        System.getOut().println("\r" == "\u000D")
        System.getOut().println("\0" == "\u0000")
        System.getOut().println("\\" == "\u005c")
        System.getOut().println("\\\\" == "\u005c\u005c")
        System.getOut().println("\"" == "\u0022")
        System.getOut().println("'" == "\u0027")
        System.getOut().println("\x41" == "A")
        System.getOut().println("\u00e9" == "\u{e9}")
        System.getOut().println("\U0001F600" == "\u{1F600}")
        // Char escapes.
        System.getOut().println('\n' == '\u000A')
        return 0
    }
}
