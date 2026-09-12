package namedvariadic

class Main {

    static count(first: Long, rest: Long...): Long {
        return first
    }

    public static run(args: String...): Long {
        System.getOut().println(Main.count(first: 7))
        return 0
    }
}
