interface Greeter {
    func greet(): String
    func greeting(): String {
        return "Hello, " .. greet()
    }
}
class Named implements Greeter {
    val name: String
    Named(name: String) {
        this.name = name
    }
    func greet(): String {
        return this.name
    }
}
class Service implements Greeter {
    delegate val greeter: Greeter
    Service(greeter: Greeter) {
        this.greeter = greeter
    }
}
val g: Greeter = Named("Doug")
println(g.greeting())
println(Service(Named("Solvik")).greeting())
