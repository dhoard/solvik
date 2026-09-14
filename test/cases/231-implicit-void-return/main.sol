package implicitvoid

trait Greeter {

    func greet(self)
}

struct Person implements Greeter {

    mutable count: Integer

    public func new(): Self {
        return Self { count: 0, }
    }

    public func greet(self) {
        self.count = self.count + 1
        System.getOut().println("hello " .. self.count)
    }

    public func visits(self): Integer {
        return self.count
    }

    public func announce() {
        System.getOut().println("announced")
    }
}

struct Main {

    public func run(args: String...): Integer {
        Person.announce()
        let p: Person = Person.new()
        p.greet()
        p.greet()
        let g: Greeter = p
        g.greet()
        System.getOut().println("visits=" .. p.visits())
        return 0
    }
}
