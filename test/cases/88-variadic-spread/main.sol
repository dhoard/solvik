package variadicspread

class Main {
    pub static count(values: Int...): Int {
        return values.size()
    }

    pub static run(args: String...): Int {
        values: List<Int> = [1, 2]
        n: Int = Main::count(...values)
        stdout.println(values.size())
        stdout.println(n)
        return 0
    }
}
