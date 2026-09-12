package scalconst

class Main {
    public static run(args: String...): Long {
        System.out().println(Byte.MAX_VALUE)
        System.out().println(Integer.MIN_VALUE)
        System.out().println(Long.MAX_VALUE)
        let n: Double = Double.NaN
        System.out().println(n == n)
        return 0
    }
}
