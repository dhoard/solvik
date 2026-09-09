package interfaces

interface Greetable {
    greeting(): String

    farewell(): String {
        return "bye from " .. greeting()
    }
}

class Bot implements Greetable {
    pub static new(): Self {
        return Self {}
    }

    override pub greeting(): String {
        return "bot"
    }
}

class PoliteBot extends Bot {
    override pub greeting(): String {
        return "polite bot"
    }
}

class Main {
    pub static run(args: String...): Int {
        g: Greetable = PoliteBot::new()
        stdout.println(g.greeting())
        stdout.println(g.farewell())
        b: Bot = Bot::new()
        stdout.println(b.farewell())
        return 0
    }
}
