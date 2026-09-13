package delegdefault

interface Greeting {
    func greeting(self): String
    func farewell(self): String { return "bye " .. greeting() }
}

struct Bot implements Greeting {
    public static func new(): Self { return Self {} }
    public func greeting(self): String { return "bot" }
}

struct Employee implements Greeting {
    bot: Bot
    delegate Greeting to bot
    public static func new(): Self { return Self { bot: Bot.new(), } }
    // explicit method beats the delegated interface default
    public func farewell(self): String { return "custom farewell" }
}

struct Main {
    public static func run(args: String...): Long {
        let e: Employee = Employee.new()
        System.getOut().println(e.greeting())
        System.getOut().println(e.farewell())
        return 0
    }
}
