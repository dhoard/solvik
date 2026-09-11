package delegdyn

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
        let o: Object = Employee.new("Alice")
        System.out().println(o.name().toString())
        return 0
    }
}
