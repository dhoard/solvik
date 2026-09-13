package c

interface Named {

    func name(self): String
}

struct Person implements Named {

    public static func new(): Self {
        return Self {}
    }

    public func name(self): String {
        return "p"
    }
}

struct Box<T> {

    v: T

    public static func new(v: T): Self {
        return Self { v: v, }
    }

    public func get<U: Named>(self, x: U): U {
        return x
    }
}

struct Main {

    public static func run(args: String...): Long {
        let p: Person = Person.new()
        let b: Box<Person> = Box.new(p)
        let r: Named = b.get(42)
        System.getOut().println(r.name())
        return 0
    }
}
