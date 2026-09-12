package variadiccall

class Main {

    public static count(values: Long...): Long {
        return values.size()
    }

    public static run(args: String...): Long {
        System.getOut().println(Main.count(1, 2, 3))
        return 0
    }
}
