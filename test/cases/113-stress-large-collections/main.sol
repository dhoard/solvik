package stresscoll

class Main {

    public static run(args: String...): Long {
        let xs: List<Long> = []
        let mutable i: Long = 0
        while i < 100000 {
            xs.add(i)
            i += 1
        }
        let mutable t: Long = 0
        let mutable j: Long = 0
        while j < xs.size() {
            t += xs.get(j)
            j += 1
        }
        System.out().println(xs.size())
        System.out().println(t)
        return 0
    }
}
