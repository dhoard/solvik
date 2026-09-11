package strings

class Main {

    public static run(args: String...): Long {
        let s: String = "hello"
        System.out().println(s.length())
        System.out().println("foo" .. "bar")
        System.out().println(s.substring(1, 3))
        System.out().println(s.contains("ell"))
        System.out().println(s.startsWith("he"))
        System.out().println(s.endsWith("lo"))
        let parts: List<String> = s.split("l")
        System.out().println(parts.size())
        System.out().println(s.replace("l", "L"))
        System.out().println("  pad  ".trim())
        System.out().println(s.toUpperCase())
        System.out().println(s.toLowerCase())
        System.out().println(s.indexOf("lo"))
        System.out().println(s.charAt(0))
        return 0
    }
}
