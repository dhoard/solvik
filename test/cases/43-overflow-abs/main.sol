module regression

class Main {

    public static run(args: String...): Long {
        stdout.println(Math.abs(-9223372036854775807 - 1))
        return 0
    }
}
