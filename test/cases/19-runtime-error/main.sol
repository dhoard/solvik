module badruntime

class Main {

    public static run(args: String...): Long {
        x: List<Long> = [1]
        stdout.println(x.get(5))
        return 0
    }
}
