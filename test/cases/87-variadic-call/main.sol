package variadiccall

struct Main {

    public static func count(values: Long...): Long {
        return values.size()
    }

    public static func run(args: String...): Long {
        System.getOut().println(Main.count(1, 2, 3))
        return 0
    }
}
