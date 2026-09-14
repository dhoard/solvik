package stringcodepointoffset

struct Main {

    pub func run(args: String...): Integer {
        // A supplementary code point (🌍) precedes the requested indices; the
        // code-point index unit must be translated to a UTF-16 offset.
        let s: String = "🌍abc"
        System.getOut().println(s.length())
        System.getOut().println(s.charAt(0))
        System.getOut().println(s.charAt(1))
        System.getOut().println(s.charAt(3))
        return 0
    }
}
