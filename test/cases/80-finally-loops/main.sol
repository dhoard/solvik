package finalizerloops
class Main {

    public static run(args: String...): Long {
        for i in 0..3 {
            try {
                if (i == 0) { continue }
                break
            } finally { System.getOut().println(i) }
        }
        try {
            while (true) {
                try { break } catch (e: Exception) { System.getOut().println("stale handler") }
            }
            throw Exception.new("outer")
        } catch (e: Exception) { System.getOut().println(e) }
        return 0
    }
}
