module variadicspread

class Main {

    public static count(values: Long...): Long {
        return values.size()
    }

    public static run(args: String...): Long {
        values: List<Long> = [1, 2]
        n: Long = Main.count(...values)
        stdout.println(values.size())
        stdout.println(n)
        return 0
    }
}
