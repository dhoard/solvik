package delegdyn

interface Named { func name(self): String }

struct Person implements Named {
    nameValue: String
    public static func new(name: String): Self { return Self { nameValue: name, } }
    public func name(self): String { return self.nameValue }
}

struct Employee implements Named {
    person: Person
    delegate Named to person
    public static func new(name: String): Self { return Self { person: Person.new(name), } }
}

struct Main {
    public static func run(args: String...): Long {
        let o: Object = Employee.new("Alice")
        System.getOut().println(o.name().toString())
        return 0
    }
}
