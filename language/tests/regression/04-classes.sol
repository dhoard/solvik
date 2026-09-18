open class Animal {
    val name: String
    Animal(name: String) {
        this.name = name
    }
    open func speak(): String {
        return "..."
    }
    func describe(): String {
        return this.name .. " says " .. this.speak()
    }
}
open class Dog extends Animal {
    Dog(name: String) {
        super(name)
    }
    override open func speak(): String {
        return "woof"
    }
}
class Puppy extends Dog {
    Puppy(name: String) {
        super(name)
    }
    override open func speak(): String {
        return "yip"
    }
}
println(Animal("x").describe())
println(Dog("Rex").describe())
println(Puppy("Bit").describe())
