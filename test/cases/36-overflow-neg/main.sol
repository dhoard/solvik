package regression

struct Main {

    public func run(args: String...): Long {
        System.getOut().println(-(-9223372036854775807 - 1))
        return 0
    }
}
