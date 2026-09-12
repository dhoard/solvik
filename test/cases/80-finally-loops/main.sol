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
                try { break } catch (e: Exception) { System.out().println("stale handler") }
            }
            throw Exception.new("outer")
        } catch (e: Exception) { System.out().println(e) }
        return 0
    }
}
