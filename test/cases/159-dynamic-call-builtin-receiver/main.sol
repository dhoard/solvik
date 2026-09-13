package dynbuiltin

struct Main {

    public func run(args: String...): Long {
        let s: String = "hello"
        let o: Object = s
        o.length()
        return 0
    }
}
