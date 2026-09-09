package gcloop

class Main {
    pub static run(args: String...): Int {
        // Allocates well past the GC threshold inside the loop; the list
        // and its elements must survive every collection.
        l: List<String> = []
        mut i: Int = 0
        while i < 6000 {
            l.add("s" .. i)
            i += 1
        }
        stdout.println(l.size())
        stdout.println(l.get(5999))
        return 0
    }
}
