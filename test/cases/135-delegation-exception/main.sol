package delegexc

trait Op { func run(self): Long }

struct Thrower implements Op {
    public func new(): Self { return Self {} }
    public func run(self): Long { throw Exception.new("boom") }
}

struct Wrapper implements Op {
    t: Thrower
    delegate Op to t
    public func new(): Self { return Self { t: Thrower.new(), } }
}

struct Main {
    public func run(args: String...): Integer {
        try {
            Wrapper.new().run()
        } catch (e: Exception) {
            System.getOut().println(e)
        }
        return 0
    }
}
