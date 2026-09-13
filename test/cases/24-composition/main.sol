package composition

interface Identified {

    func id(self): Long
}

struct Parent implements Identified {

    idValue: Long

    public static func new(id: Long): Self {
        return Self { idValue: id, }
    }

    public func id(self): Long {
        return self.idValue
    }
}

struct Child implements Identified {

    parent: Parent

    delegate Identified to parent

    public static func make(id: Long): Self {
        return Self { parent: Parent.new(id), }
    }
}

struct Main {

    public static func run(args: String...): Long {
        let p: Parent = Parent.new(7)
        if p.id() != 7 { return 1 }
        let c: Child = Child.make(9)
        if c.id() != 9 { return 2 }
        System.getOut().println("ok")
        return 0
    }
}
