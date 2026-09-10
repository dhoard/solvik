module interfaces

interface Greetable {

    greeting(): String

    farewell(): String {
        return "bye from " .. greeting()
    }
}

class Bot implements Greetable {

    public static new(): Self {
        return Self {}
    }

    override public greeting(): String {
        return "bot"
    }
}

class PoliteBot extends Bot {

    override public greeting(): String {
        return "polite bot"
    }
}

class Main {

    public static run(args: String...): Long {
        let g: Greetable = PoliteBot.new()
        stdout.println(g.greeting())
        stdout.println(g.farewell())
        let b: Bot = Bot.new()
        stdout.println(b.farewell())
        return 0
    }
}
