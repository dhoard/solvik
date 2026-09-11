package variadicspread

class Main {

    public static count(values: Long...): Long {
        return values.size()
    }

    public static run(args: String...): Long {
        let values: List<Long> = [1, 2]
        let n: Long = Main.count(...values)
        System.out().println(values.size())
        System.out().println(n)
        return 0
    }
}
