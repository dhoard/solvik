module regression

class Main {

    public static run(args: String...): Long {
        x: List<Long> = [7]
        stdout.println(x.get(-1))
        return 0
    }
}
