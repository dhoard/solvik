package mutablekey

struct Main {

    public func run(args: String...): Integer {
        let k: List<Long> = [1]
        let m: Map<List<Long>, Long> = Map.new()
        m.put(k, 1)
        return 0
    }
}
