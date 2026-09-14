package ifaceinhdeleg

trait Named { func name(self): String }
trait DisplayNamed extends Named { func displayName(self): String }

struct Person implements DisplayNamed {
    nameValue: String
    pub func new(name: String): Self { return Self { nameValue: name, } }
    pub func name(self): String { return self.nameValue }
    pub func displayName(self): String { return "Person:" .. self.nameValue }
}

struct Employee implements DisplayNamed {
    person: Person
    delegate DisplayNamed to person
    pub func new(name: String): Self { return Self { person: Person.new(name), } }
}

struct Main {
    pub func run(args: String...): Integer {
        let e: Employee = Employee.new("Alice")
        System.getOut().println(e.name())
        System.getOut().println(e.displayName())
        let n: Named = e
        System.getOut().println(n.name())
        return 0
    }
}
