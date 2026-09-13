package staticselfuse

struct X {
    static count: Long = 0
    func bad(): Long {
        return self.count
    }
}

struct Main {
    public func run(args: String...): Long {
        return 0
    }
}
