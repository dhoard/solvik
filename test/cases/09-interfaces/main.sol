package interfaces

trait Greetable {

    func greeting(self): String

    func farewell(self): String {
        return "bye from " .. greeting()
    }
}

struct Bot implements Greetable {

    pub func new(): Self {
        return Self {}
    }

    pub func greeting(self): String {
        return "bot"
    }
}

struct PoliteBot implements Greetable {

    pub func new(): Self {
        return Self {}
    }

    pub func greeting(self): String {
        return "polite bot"
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let g: Greetable = PoliteBot.new()
        System.getOut().println(g.greeting())
        System.getOut().println(g.farewell())
        let b: Bot = Bot.new()
        System.getOut().println(b.farewell())
        return 0
    }
}
