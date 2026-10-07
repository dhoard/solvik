// Solvik composition: an interface default method served by a delegate, plus null safety.
interface Greeter {
    method greet(): String

    method greeting(): String {
        return "Hello, " .. greet()
    }
}

class Named implements Greeter {
    var name: String

    Named(name: String) {
        this.name = name
    }

    method greet(): String {
        return this.name
    }
}

class Service implements Greeter {
    delegate greeter: Greeter

    Service(greeter: Greeter) {
        this.greeter = greeter
    }
}

func describe(greeter: Greeter?): String {
    if (greeter == null) {
        return "nobody"
    }
    return greeter.greeting()
}

println(describe(Service(Named("Solvik"))))
println(describe(null))
