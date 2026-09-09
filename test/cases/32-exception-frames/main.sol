module regression

class Main {

    public static fail(): Void { throw "boom" }
    public static value(flag: Bool): Long {
        try {
            if flag {
                return 1
            }
        } catch (e) {
            stdout.println("wrong")
        }
        return 2
    }
    public static nested(): Void {
        saved: String = "callee local"
        Main.fail()
        stdout.println(saved)
    }
    public static run(args: String...): Long {
        try {
            Main.nested()
        } catch (e) {
            stdout.println("caught " .. e)
        }
        try {
            Main.value(true)
            throw "after return"
        } catch (e) {
            stdout.println(e)
        }
        return 0
    }
}
