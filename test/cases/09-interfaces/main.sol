package interfaces

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

    public greeting(): String {
        return "bot"
    }
}

class PoliteBot implements Greetable {

    public static new(): Self {
        return Self {}
    }

    public greeting(): String {
        return "polite bot"
    }
}

class Main {

    public static run(args: String...): Long {
        let g: Greetable = PoliteBot.new()
        System.out().println(g.greeting())
        System.out().println(g.farewell())
        let b: Bot = Bot.new()
        System.out().println(b.farewell())
        return 0
    }
}
