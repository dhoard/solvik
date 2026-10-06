mutable class Animal {
    var name: String
    Animal(name: String) {
        this.name = name
    }
    mutable func speak(): String {
        return "..."
    }
    func describe(): String {
        return this.name .. " says " .. this.speak()
    }
}
mutable class Dog extends Animal {
    Dog(name: String) {
        super(name)
    }
    override mutable func speak(): String {
        return "woof"
    }
}
class Puppy extends Dog {
    Puppy(name: String) {
        super(name)
    }
    override mutable func speak(): String {
        return "yip"
    }
}
println(Animal("x").describe())
println(Dog("Rex").describe())
println(Puppy("Bit").describe())
