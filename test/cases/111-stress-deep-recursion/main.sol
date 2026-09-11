package stressdeep

class Main {

    public static acc(n: Long): Long {
        if n == 0 {
            return 0
        }
        return n % 7 + Main.acc(n - 1)
    }

    public static run(args: String...): Long {
        stdout.println(Main.acc(20000))
        return 0
    }
}
