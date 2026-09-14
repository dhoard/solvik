package delegdyn

trait Named { func name(self): String }

struct Person implements Named {
    nameValue: String
    pub func new(name: String): Self { return Self { nameValue: name, } }
    pub func name(self): String { return self.nameValue }
}

struct Employee implements Named {
    person: Person
    delegate Named to person
    pub func new(name: String): Self { return Self { person: Person.new(name), } }
}

struct Main {
    pub func run(args: String...): Integer {
        let o: Object = Employee.new("Alice")
        System.getOut().println(o.name().toString())
        return 0
    }
}
