package defaultself

trait Named {
    func name(self): String
    func describe(self): String {
        return "name=" .. self.name()
    }
}

struct Bot implements Named {
    pub func new(): Self { return Self {} }
    pub func name(self): String { return "bot" }
}

struct Main {
    pub func run(args: String...): Integer {
        let n: Named = Bot.new()
        System.getOut().println(n.describe())
        return 0
    }
}
