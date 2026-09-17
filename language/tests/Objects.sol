// Solvik composition: an interface default method served by a delegate, plus null safety.
interface Greeter {
    fun greet(): String

    fun greeting(): String {
        return "Hello, " + greet()
    }
}

class Named implements Greeter {
    val name: String

    init(name: String) {
        this.name = name
    }

    fun greet(): String {
        return this.name
    }
}

class Service implements Greeter {
    delegate val greeter: Greeter

    init(greeter: Greeter) {
        this.greeter = greeter
    }
}

fun describe(greeter: Greeter?): String {
    if (greeter == null) {
        return "nobody"
    }
    return greeter.greeting()
}

fun main(): Unit {
    println(describe(Service(Named("Solvik"))))
    println(describe(null))
}
