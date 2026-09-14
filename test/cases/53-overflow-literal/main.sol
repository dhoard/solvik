package badliteral
struct Main {
    public func run(args: String...): Integer {
        System.getOut().println(9223372036854775808)
        return 0
    }
}
