package regression

class Main {

    public static fail(): Void { throw "boom" }
    public static value(flag: Bool): Long {
        try {
            if flag {
                return 1
            }
        } catch (e) {
            System.out().println("wrong")
        }
        return 2
    }
    public static nested(): Void {
        let saved: String = "callee local"
        Main.fail()
        System.out().println(saved)
    }
    public static run(args: String...): Long {
        try {
            Main.nested()
        } catch (e) {
            System.out().println("caught " .. e)
        }
        try {
            Main.value(true)
            throw "after return"
        } catch (e) {
            System.out().println(e)
        }
        return 0
    }
}
