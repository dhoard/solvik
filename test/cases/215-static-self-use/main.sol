package staticselfuse

struct X {
    static count: Long = 0
    func bad(): Long {
        return self.count
    }
}

struct Main {
    pub func run(args: String...): Integer {
        return 0
    }
}
