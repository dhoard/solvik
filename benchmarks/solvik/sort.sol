package bench

struct Main {

    public func run(args: String...): Integer {
        let values: List<Long> = List<Long>.new()
        let mutable i: Long = 0
        while i < 200000 {
            values.add((i * 2654435761) % 4294967296)
            i += 1
        }
        values.sort()
        System.getOut().println(values.get(0))
        System.getOut().println(values.get(199999))
        return 0
    }
}
