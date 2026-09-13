package gcloop

struct Main {

    public func run(args: String...): Long {
        // Allocates well past the GC threshold inside the loop; the list
        // and its elements must survive every collection.
        let l: List<String> = []
        let mutable i: Long = 0
        while i < 6000 {
            l.add("s" .. i)
            i += 1
        }
        System.getOut().println(l.size())
        System.getOut().println(l.get(5999))
        return 0
    }
}
