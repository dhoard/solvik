module delegnotimpl

interface Named { name(): String }

class Wrapper implements Named {
    count: Long
    delegate Named to count
}

class Main { public static run(args: String...): Long { return 0 } }
