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
var g: Greeter = Named("Doug")
println(g.greeting())
println(Service(Named("Solvik")).greeting())
