package delegexc

trait Op { func run(self): Long }

struct Thrower implements Op {
    pub func new(): Self { return Self {} }
    pub func run(self): Long { throw Exception.new("boom") }
}

struct Wrapper implements Op {
    t: Thrower
    delegate Op to t
    pub func new(): Self { return Self { t: Thrower.new(), } }
}

struct Main {
    pub func run(args: String...): Integer {
        try {
            Wrapper.new().run()
        } catch (e: Exception) {
            System.getOut().println(e)
        }
        return 0
    }
}
