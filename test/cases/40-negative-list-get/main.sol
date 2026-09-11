package regression

class Main {

    public static run(args: String...): Long {
        let x: List<Long> = [7]
        stdout.println(x.get(-1))
        return 0
    }
}
