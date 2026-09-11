package finalizerloops
class Main {

    public static run(args: String...): Long {
        for i in 0..3 {
            try {
                if (i == 0) { continue }
                break
            } finally { System.out().println(i) }
        }
        try {
            while (true) {
                try { break } catch (e) { System.out().println("stale handler") }
            }
            throw "outer"
        } catch (e) { System.out().println(e) }
        return 0
    }
}
