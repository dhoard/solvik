package gcloop

struct Main {

    pub func run(args: String...): Integer {
        // Allocates well past the GC threshold inside the loop; the list
        // and its elements must survive every collection.
        let l: List<String> = []
        var i: Long = 0
        while i < 6000 {
            l.add("s" .. i)
            i += 1
        }
        System.getOut().println(l.size())
        System.getOut().println(l.get(5999))
        return 0
    }
}
