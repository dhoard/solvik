package ifaceinhdeleg

trait Named { func name(self): String }
trait DisplayNamed extends Named { func displayName(self): String }

struct Person implements DisplayNamed {
    nameValue: String
    public func new(name: String): Self { return Self { nameValue: name, } }
    public func name(self): String { return self.nameValue }
    public func displayName(self): String { return "Person:" .. self.nameValue }
}

struct Employee implements DisplayNamed {
    person: Person
    delegate DisplayNamed to person
    public func new(name: String): Self { return Self { person: Person.new(name), } }
}

struct Main {
    public func run(args: String...): Integer {
        let e: Employee = Employee.new("Alice")
        System.getOut().println(e.name())
        System.getOut().println(e.displayName())
        let n: Named = e
        System.getOut().println(n.name())
        return 0
    }
}
