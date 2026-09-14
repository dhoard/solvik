package stringcodepoints

struct Main {

    public func run(args: String...): Integer {
        let s: String = "héllo🌍"
        System.getOut().println(s.length())
        System.getOut().println(s.charAt(0))
        System.getOut().println(s.charAt(1))
        System.getOut().println(s.charAt(4))
        System.getOut().println(s.substring(0, 3))
        System.getOut().println(s.substring(4, 6))
        let mutable count: Long = 0
        for c in s {
            count = count + 1
        }
        System.getOut().println(count)
        return 0
    }
}
