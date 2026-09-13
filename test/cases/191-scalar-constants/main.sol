package scalconst

struct Main {
    public static func run(args: String...): Long {
        System.getOut().println(Byte.MAX_VALUE)
        System.getOut().println(Integer.MIN_VALUE)
        System.getOut().println(Long.MAX_VALUE)
        let n: Double = Double.NaN
        System.getOut().println(n == n)
        return 0
    }
}
