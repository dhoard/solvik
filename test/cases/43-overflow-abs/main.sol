package regression

struct Main {

    public static func run(args: String...): Long {
        System.getOut().println(Math.abs(-9223372036854775807 - 1))
        return 0
    }
}
