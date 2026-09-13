package delegexc

interface Op { func run(self): Long }

struct Thrower implements Op {
    public static func new(): Self { return Self {} }
    public func run(self): Long { throw Exception.new("boom") }
}

struct Wrapper implements Op {
    t: Thrower
    delegate Op to t
    public static func new(): Self { return Self { t: Thrower.new(), } }
}

struct Main {
    public static func run(args: String...): Long {
        try {
            Wrapper.new().run()
        } catch (e: Exception) {
            System.getOut().println(e)
        }
        return 0
    }
}
