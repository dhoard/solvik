package ifaceinhdeleg

interface Named { name(): String }
interface DisplayNamed extends Named { displayName(): String }

class Person implements DisplayNamed {
    nameValue: String
    public static new(name: String): Self { return Self { nameValue: name, } }
    public name(): String { return self.nameValue }
    public displayName(): String { return "Person:" .. self.nameValue }
}

class Employee implements DisplayNamed {
    person: Person
    delegate DisplayNamed to person
    public static new(name: String): Self { return Self { person: Person.new(name), } }
}

class Main {
    public static run(args: String...): Long {
        let e: Employee = Employee.new("Alice")
        System.out().println(e.name())
        System.out().println(e.displayName())
        let n: Named = e
        System.out().println(n.name())
        return 0
    }
}
