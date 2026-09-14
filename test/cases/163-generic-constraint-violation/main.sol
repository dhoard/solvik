package c

trait Named {

    func name(self): String
}

struct Person implements Named {

    public func new(): Self {
        return Self {}
    }

    public func name(self): String {
        return "p"
    }
}

struct Box<T> {

    v: T

    public func new(v: T): Self {
        return Self { v: v, }
    }

    public func get<U: Named>(self, x: U): U {
        return x
    }
}

struct Main {

    public func run(args: String...): Integer {
        let p: Person = Person.new()
        let b: Box<Person> = Box.new(p)
        let r: Named = b.get(42)
        System.getOut().println(r.name())
        return 0
    }
}
