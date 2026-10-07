class mutable Animal {
    var name: String
    Animal(name: String) {
        this.name = name
    }
    method mutable speak(): String {
        return "..."
    }
    method describe(): String {
        return this.name .. " says " .. this.speak()
    }
}
class mutable Dog extends Animal {
    Dog(name: String) {
        super(name)
    }
    method override mutable speak(): String {
        return "woof"
    }
}
class Puppy extends Dog {
    Puppy(name: String) {
        super(name)
    }
    method override mutable speak(): String {
        return "yip"
    }
}
println(Animal("x").describe())
println(Dog("Rex").describe())
println(Puppy("Bit").describe())
