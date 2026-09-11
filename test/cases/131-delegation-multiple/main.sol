package delegmulti

interface Named { name(): String }
interface Identified { id(): Long }

class Person implements Named {
    nameValue: String
    public static new(name: String): Self { return Self { nameValue: name, } }
    public name(): String { return self.nameValue }
}

class Identity implements Identified {
    idValue: Long
    public static new(id: Long): Self { return Self { idValue: id, } }
    public id(): Long { return self.idValue }
}

class Employee implements Named, Identified {
    person: Person
    identity: Identity
    delegate Named to person
    delegate Identified to identity
    public static new(name: String, id: Long): Self {
        return Self { person: Person.new(name), identity: Identity.new(id), }
    }
}

class Main {
    public static run(args: String...): Long {
        let e: Employee = Employee.new("Alice", 1001)
        System.out().println(e.name())
        System.out().println(e.id())
        let n: Named = e
        let i: Identified = e
        System.out().println(n.name())
        System.out().println(i.id())
        return 0
    }
}
