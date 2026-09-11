package badruntime

class Main {

    public static run(args: String...): Long {
        let x: List<Long> = [1]
        stdout.println(x.get(5))
        return 0
    }
}
