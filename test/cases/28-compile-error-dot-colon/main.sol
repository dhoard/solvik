package dotcolon

class Math2 {
    pub static double(x: Int): Int {
        return x * 2
    }
}

class Main {
    pub static run(args: String...): Int {
        // ERROR: static methods use '::', not '.' member syntax
        v: Int = Math2.double(4)
        stdout.println(v)
        return 0
    }
}
