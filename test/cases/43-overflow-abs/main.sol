package regression

class Main {

    public static run(args: String...): Long {
        System.getOut().println(Math.abs(-9223372036854775807 - 1))
        return 0
    }
}
