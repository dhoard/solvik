package interfaces

interface Greetable {

    func greeting(self): String

    func farewell(self): String {
        return "bye from " .. greeting()
    }
}

struct Bot implements Greetable {

    public static func new(): Self {
        return Self {}
    }

    public func greeting(self): String {
        return "bot"
    }
}

struct PoliteBot implements Greetable {

    public static func new(): Self {
        return Self {}
    }

    public func greeting(self): String {
        return "polite bot"
    }
}

struct Main {

    public static func run(args: String...): Long {
        let g: Greetable = PoliteBot.new()
        System.getOut().println(g.greeting())
        System.getOut().println(g.farewell())
        let b: Bot = Bot.new()
        System.getOut().println(b.farewell())
        return 0
    }
}
