package exceptions

class Main {

    public static run(args: String...): Long {
        // basic catch
        try {
            throw Exception.new("boom")
        } catch (e: Exception) {
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
            throw Exception.new("again")
        } catch (e: Exception) {
            System.out().println("got " .. e)
        } finally {
            System.out().println("done")
        }
        // exception propagates out of a nested try without catch
        let mutable flag: String = "unset"
        try {
            try {
                throw Exception.new("deep")
            } finally {
                flag = "finally-ran"
                System.out().println(flag)
            }
        } catch (e: Exception) {
            System.out().println("outer caught " .. e)
        }
        return 0
    }
}
