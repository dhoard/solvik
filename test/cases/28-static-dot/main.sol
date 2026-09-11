package dotcolon

class Math2 {

    public static double(x: Long): Long {
        return x * 2
    }
}

class Main {

    public static run(args: String...): Long {
        // Dot-qualified calls select static methods on uppercase type names.
        let v: Long = Math2.double(4)
        stdout.println(v)
        return 0
    }
}
