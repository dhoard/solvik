package escapes

class Main {

    public static run(args: String...): Long {
        // Every escape form, verified against its unicode equivalent.
        System.out().println("\n" == "\u000A")
        System.out().println("\t" == "\u0009")
        System.out().println("\r" == "\u000D")
        System.out().println("\0" == "\u0000")
        System.out().println("\\" == "\u005c")
        System.out().println("\\\\" == "\u005c\u005c")
        System.out().println("\"" == "\u0022")
        System.out().println("'" == "\u0027")
        System.out().println("\x41" == "A")
        System.out().println("\u00e9" == "\u{e9}")
        System.out().println("\U0001F600" == "\u{1F600}")
        // Char escapes.
        System.out().println('\n' == '\u000A')
        return 0
    }
}
