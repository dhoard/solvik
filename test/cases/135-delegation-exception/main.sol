package delegexc

interface Op { run(): Long }

class Thrower implements Op {
    public static new(): Self { return Self {} }
    public run(): Long { throw "boom" }
}

class Wrapper implements Op {
    t: Thrower
    delegate Op to t
    public static new(): Self { return Self { t: Thrower.new(), } }
}

class Main {
    public static run(args: String...): Long {
        try {
            Wrapper.new().run()
        } catch (e) {
            System.out().println(e)
        }
        return 0
    }
}
