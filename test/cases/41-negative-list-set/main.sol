package regression

class Main {

    public static run(args: String...): Long {
        let x: List<Long> = [7]
        x.set(-1, 9)
        System.out().println(x.get(0))
        return 0
    }
}
