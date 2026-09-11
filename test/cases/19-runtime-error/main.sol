package badruntime

class Main {

    public static run(args: String...): Long {
        let x: List<Long> = [1]
        System.out().println(x.get(5))
        return 0
    }
}
