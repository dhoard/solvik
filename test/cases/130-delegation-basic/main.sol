package delegbasic

trait Named { func name(self): String }

struct Person implements Named {
    nameValue: String
    public func new(name: String): Self { return Self { nameValue: name, } }
    public func name(self): String { return self.nameValue }
}

struct Employee implements Named {
    person: Person
    delegate Named to person
    public func new(name: String): Self { return Self { person: Person.new(name), } }
}

struct Main {
    public func run(args: String...): Integer {
        let e: Employee = Employee.new("Alice")
        System.getOut().println(e.name())
        let n: Named = e
        System.getOut().println(n.name())
        return 0
    }
}
