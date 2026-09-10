module exceptions

class Main {

    public static run(args: String...): Long {
        // basic catch
        try {
            throw "boom"
        } catch (e) {
            stdout.println("caught " .. e)
        }
        // finally always runs
        try {
            stdout.println("work")
        } finally {
            stdout.println("cleaned")
        }
        // catch + finally
        try {
            throw "again"
        } catch (e) {
            stdout.println("got " .. e)
        } finally {
            stdout.println("done")
        }
        // exception propagates out of a nested try without catch
        let mutable flag: String = "unset"
        try {
            try {
                throw "deep"
            } finally {
                flag = "finally-ran"
                stdout.println(flag)
            }
        } catch (e) {
            stdout.println("outer caught " .. e)
        }
        return 0
    }
}
