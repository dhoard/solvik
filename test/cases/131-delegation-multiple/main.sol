package delegmulti

interface Named { func name(self): String }
interface Identified { func id(self): Long }

struct Person implements Named {
    nameValue: String
    public static func new(name: String): Self { return Self { nameValue: name, } }
    public func name(self): String { return self.nameValue }
}

struct Identity implements Identified {
    idValue: Long
    public static func new(id: Long): Self { return Self { idValue: id, } }
    public func id(self): Long { return self.idValue }
}

struct Employee implements Named, Identified {
    person: Person
    identity: Identity
    delegate Named to person
    delegate Identified to identity
    public static func new(name: String, id: Long): Self {
        return Self { person: Person.new(name), identity: Identity.new(id), }
    }
}

struct Main {
    public static func run(args: String...): Long {
        let e: Employee = Employee.new("Alice", 1001)
        System.getOut().println(e.name())
        System.getOut().println(e.id())
        let n: Named = e
        let i: Identified = e
        System.getOut().println(n.name())
        System.getOut().println(i.id())
        return 0
    }
}
