module finalizerloops
class Main {

    public static run(args: String...): Long {
        for i in 0..3 {
            try {
                if (i == 0) { continue }
                break
            } finally { stdout.println(i) }
        }
        try {
            while (true) {
                try { break } catch (e) { stdout.println("stale handler") }
            }
            throw "outer"
        } catch (e) { stdout.println(e) }
        return 0
    }
}
