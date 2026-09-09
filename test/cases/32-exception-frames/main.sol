package regression

class Main {
    pub static fail(): Void { throw "boom" }
    pub static value(flag: Bool): Int {
        try {
            if flag {
                return 1
            }
        } catch (e) {
            stdout.println("wrong")
        }
        return 2
    }
    pub static nested(): Void {
        saved: String = "callee local"
        Main::fail()
        stdout.println(saved)
    }
    pub static run(args: String...): Int {
        try {
            Main::nested()
        } catch (e) {
            stdout.println("caught " .. e)
        }
        try {
            Main::value(true)
            throw "after return"
        } catch (e) {
            stdout.println(e)
        }
        return 0
    }
}
