module stresscoll

class Main {

    public static run(args: String...): Long {
        xs: List<Long> = []
        mutable i: Long = 0
        while i < 100000 {
            xs.add(i)
            i += 1
        }
        mutable t: Long = 0
        mutable j: Long = 0
        while j < xs.size() {
            t += xs.get(j)
            j += 1
        }
        stdout.println(xs.size())
        stdout.println(t)
        return 0
    }
}
