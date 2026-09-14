package exceptions

struct Main {

    public func run(args: String...): Integer {
        // basic catch
        try {
            throw Exception.new("boom")
        } catch (e: Exception) {
            System.getOut().println("caught " .. e)
        }
        // finally always runs
        try {
            System.getOut().println("work")
        } finally {
            System.getOut().println("cleaned")
        }
        // catch + finally
        try {
            throw Exception.new("again")
        } catch (e: Exception) {
            System.getOut().println("got " .. e)
        } finally {
            System.getOut().println("done")
        }
        // exception propagates out of a nested try without catch
        let mutable flag: String = "unset"
        try {
            try {
                throw Exception.new("deep")
            } finally {
                flag = "finally-ran"
                System.getOut().println(flag)
            }
        } catch (e: Exception) {
            System.getOut().println("outer caught " .. e)
        }
        return 0
    }
}
