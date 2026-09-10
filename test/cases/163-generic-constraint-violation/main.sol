module c

interface Named {

    name(): String
}

class Person implements Named {

    public static new(): Self {
        return Self {}
    }

    public name(): String {
        return "p"
    }
}

class Box<T> {

    v: T

    public static new(v: T): Self {
        return Self { v: v, }
    }

    public get<U: Named>(x: U): U {
        return x
    }
}

class Main {

    public static run(args: String...): Long {
        let p: Person = Person.new()
        let b: Box<Person> = Box.new(p)
        let r: Named = b.get(42)
        stdout.println(r.name())
        return 0
    }
}
