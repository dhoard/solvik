package monitorreentrant

// Trait default bodies and generated delegation forwarding methods both run
// against a concrete struct receiver, so they must hold that receiver's
// monitor. The default below calls the struct's own name() method reentrantly.

trait Greeter {

    func name(self): String

    func greet(self): String {
        return "hi " .. self.name()
    }
}

struct Person implements Greeter {

    n: String

    pub func new(n: String): Self {
        return Self { n: n, }
    }

    pub func name(self): String {
        return self.n
    }
}

struct Wrapper implements Greeter {

    inner: Person

    delegate Greeter to inner

    pub func new(name: String): Self {
        return Self { inner: Person.new(name), }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let throughTrait: Greeter = Person.new("ada")
        System.getOut().println(throughTrait.greet())
        let delegated: Greeter = Wrapper.new("grace")
        System.getOut().println(delegated.greet())
        let direct: Person = Person.new("bob")
        System.getOut().println(direct.greet())
        return 0
    }
}
