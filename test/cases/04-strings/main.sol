module strings

class Main {

    public static run(args: String...): Long {
        s: String = "hello"
        stdout.println(s.length())
        stdout.println("foo" .. "bar")
        stdout.println(s.substring(1, 3))
        stdout.println(s.contains("ell"))
        stdout.println(s.startsWith("he"))
        stdout.println(s.endsWith("lo"))
        parts: List<String> = s.split("l")
        stdout.println(parts.size())
        stdout.println(s.replace("l", "L"))
        stdout.println("  pad  ".trim())
        stdout.println(s.toUpperCase())
        stdout.println(s.toLowerCase())
        stdout.println(s.indexOf("lo"))
        stdout.println(s.charAt(0))
        return 0
    }
}
