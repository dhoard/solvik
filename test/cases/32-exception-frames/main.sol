package regression

struct Main {

    pub func fail() { throw Exception.new("boom") }
    pub func value(flag: Boolean): Long {
        try {
            if flag {
                return 1
            }
        } catch (e: Exception) {
            System.getOut().println("wrong")
        }
        return 2
    }
    pub func nested() {
        let saved: String = "callee local"
        Main.fail()
        System.getOut().println(saved)
    }
    pub func run(args: String...): Integer {
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
