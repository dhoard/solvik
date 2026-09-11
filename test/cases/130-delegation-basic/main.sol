package delegbasic

interface Named { name(): String }

class Person implements Named {
    nameValue: String
    public static new(name: String): Self { return Self { nameValue: name, } }
    public name(): String { return self.nameValue }
}

class Employee implements Named {
    person: Person
    delegate Named to person
    public static new(name: String): Self { return Self { person: Person.new(name), } }
}

class Main {
    public static run(args: String...): Long {
        let e: Employee = Employee.new("Alice")
        System.out().println(e.name())
        let n: Named = e
        System.out().println(n.name())
        return 0
    }
}
