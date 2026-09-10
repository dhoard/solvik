module delegdefault

interface Greeting {
    greeting(): String
    farewell(): String { return "bye " .. greeting() }
}

class Bot implements Greeting {
    public static new(): Self { return Self {} }
    public greeting(): String { return "bot" }
}

class Employee implements Greeting {
    bot: Bot
    delegate Greeting to bot
    public static new(): Self { return Self { bot: Bot.new(), } }
    // explicit method beats the delegated interface default
    public farewell(): String { return "custom farewell" }
}

class Main {
    public static run(args: String...): Long {
        let e: Employee = Employee.new()
        stdout.println(e.greeting())
        stdout.println(e.farewell())
        return 0
    }
}
