// Solvik composition: an interface default method served by a delegate, plus null safety.
interface Greeter {
    func greet(): String

    func greeting(): String {
        return "Hello, " + greet()
    }
}

class Named implements Greeter {
    val name: String

    init(name: String) {
        this.name = name
    }

    func greet(): String {
        return this.name
    }
}

class Service implements Greeter {
    delegate val greeter: Greeter

    init(greeter: Greeter) {
        this.greeter = greeter
    }
}

func describe(greeter: Greeter?): String {
    if (greeter == null) {
        return "nobody"
    }
    return greeter.greeting()
}

func main(): Unit {
    println(describe(Service(Named("Solvik"))))
    println(describe(null))
}
