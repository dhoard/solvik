package defaultself

trait Named {
    func name(self): String
    func describe(self): String {
        return "name=" .. self.name()
    }
}

struct Bot implements Named {
    public func new(): Self { return Self {} }
    public func name(self): String { return "bot" }
}

struct Main {
    public func run(args: String...): Long {
        let n: Named = Bot.new()
        System.getOut().println(n.describe())
        return 0
    }
}
