package regression

struct Main {

    public static func fail(): Void { throw Exception.new("boom") }
    public static func value(flag: Boolean): Long {
        try {
            if flag {
                return 1
            }
        } catch (e: Exception) {
            System.getOut().println("wrong")
        }
        return 2
    }
    public static func nested(): Void {
        let saved: String = "callee local"
        Main.fail()
        System.getOut().println(saved)
    }
    public static func run(args: String...): Long {
        try {
            Main.nested()
        } catch (e: Exception) {
            System.getOut().println("caught " .. e)
        }
        try {
            Main.value(true)
            throw Exception.new("after return")
        } catch (e: Exception) {
            System.getOut().println(e)
        }
        return 0
    }
}
