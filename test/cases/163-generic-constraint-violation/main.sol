package c

trait Named {

    func name(self): String
}

struct Person implements Named {

    pub func new(): Self {
        return Self {}
    }

    pub func name(self): String {
        return "p"
    }
}

struct Box<T> {

    v: T

    pub func new(v: T): Self {
        return Self { v: v, }
    }

    pub func get<U: Named>(self, x: U): U {
        return x
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let p: Person = Person.new()
        let b: Box<Person> = Box.new(p)
        let r: Named = b.get(42)
        System.getOut().println(r.name())
        return 0
    }
}
