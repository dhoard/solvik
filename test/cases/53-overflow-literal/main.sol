package badliteral
struct Main {
    public static func run(args: String...): Long {
        System.getOut().println(9223372036854775808)
        return 0
    }
}
