package staticselfuse

struct X {
    static count: Long = 0
    static func bad(): Long {
        return self.count
    }
}

struct Main {
    public static func run(args: String...): Long {
        return 0
    }
}
