package delegdefault

trait Greeting {
    func greeting(self): String
    func farewell(self): String { return "bye " .. greeting() }
}

struct Bot implements Greeting {
    pub func new(): Self { return Self {} }
    pub func greeting(self): String { return "bot" }
}

struct Employee implements Greeting {
    bot: Bot
    delegate Greeting to bot
    pub func new(): Self { return Self { bot: Bot.new(), } }
    // explicit method beats the delegated trait default
    pub func farewell(self): String { return "custom farewell" }
}

struct Main {
    pub func run(args: String...): Integer {
        let e: Employee = Employee.new()
        System.getOut().println(e.greeting())
        System.getOut().println(e.farewell())
        return 0
    }
}
