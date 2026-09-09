module gcloop

class Main {

    public static run(args: String...): Long {
        // Allocates well past the GC threshold inside the loop; the list
        // and its elements must survive every collection.
        l: List<String> = []
        mutable i: Long = 0
        while i < 6000 {
            l.add("s" .. i)
            i += 1
        }
        stdout.println(l.size())
        stdout.println(l.get(5999))
        return 0
    }
}
