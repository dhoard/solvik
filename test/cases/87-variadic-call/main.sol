package variadiccall

class Main {
    pub static count(values: Int...): Int {
        return values.size()
    }

    pub static run(args: String...): Int {
        stdout.println(Main::count(1, 2, 3))
        return 0
    }
}
