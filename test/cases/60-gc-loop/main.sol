package gcloop

class Main {

    public static run(args: String...): Long {
        // Allocates well past the GC threshold inside the loop; the list
        // and its elements must survive every collection.
        let l: List<String> = []
        let mutable i: Long = 0
        while i < 6000 {
            l.add("s" .. i)
            i += 1
        }
        System.out().println(l.size())
        System.out().println(l.get(5999))
        return 0
    }
}
