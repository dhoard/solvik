package variadicspread

struct Main {

    public static func count(values: Long...): Long {
        return values.size()
    }

    public static func run(args: String...): Long {
        let values: List<Long> = [1, 2]
        let n: Long = Main.count(...values)
        System.getOut().println(values.size())
        System.getOut().println(n)
        return 0
    }
}
