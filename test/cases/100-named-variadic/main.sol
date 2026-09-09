package namedvariadic

class Main {
    static count(first: Int, rest: Int...): Int {
        return first
    }

    pub static run(args: String...): Int {
        stdout.println(Main::count(first: 7))
        return 0
    }
}
