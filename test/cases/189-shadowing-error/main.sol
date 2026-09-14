package shadowerr

struct Main {
    public func run(args: String...): Integer {
        let x: Long = 1
        { let x: Long = 2 }
        return x
    }
}
