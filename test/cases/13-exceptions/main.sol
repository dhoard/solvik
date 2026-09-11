package exceptions

class Main {

    public static run(args: String...): Long {
        // basic catch
        try {
            throw "boom"
        } catch (e) {
            System.out().println("caught " .. e)
        }
        // finally always runs
        try {
            System.out().println("work")
        } finally {
            System.out().println("cleaned")
        }
        // catch + finally
        try {
            throw "again"
        } catch (e) {
            System.out().println("got " .. e)
        } finally {
            System.out().println("done")
        }
        // exception propagates out of a nested try without catch
        let mutable flag: String = "unset"
        try {
            try {
                throw "deep"
            } finally {
                flag = "finally-ran"
                System.out().println(flag)
            }
        } catch (e) {
            System.out().println("outer caught " .. e)
        }
        return 0
    }
}
