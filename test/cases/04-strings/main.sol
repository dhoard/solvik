package strings

struct Main {

    public func run(args: String...): Integer {
        let s: String = "hello"
        System.getOut().println(s.length())
        System.getOut().println("foo" .. "bar")
        System.getOut().println(s.substring(1, 3))
        System.getOut().println(s.contains("ell"))
        System.getOut().println(s.startsWith("he"))
        System.getOut().println(s.endsWith("lo"))
        let parts: List<String> = s.split("l")
        System.getOut().println(parts.size())
        System.getOut().println(s.replace("l", "L"))
        System.getOut().println("  pad  ".trim())
        System.getOut().println(s.toUpperCase())
        System.getOut().println(s.toLowerCase())
        System.getOut().println(s.indexOf("lo"))
        System.getOut().println(s.charAt(0))
        return 0
    }
}
