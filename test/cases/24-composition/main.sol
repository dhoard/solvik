package composition

trait Identified {

    func id(self): Long
}

struct Parent implements Identified {

    idValue: Long

    pub func new(id: Long): Self {
        return Self { idValue: id, }
    }

    pub func id(self): Long {
        return self.idValue
    }
}

struct Child implements Identified {

    parent: Parent

    delegate Identified to parent

    pub func make(id: Long): Self {
        return Self { parent: Parent.new(id), }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let p: Parent = Parent.new(7)
        if p.id() != 7 { return 1 }
        let c: Child = Child.make(9)
        if c.id() != 9 { return 2 }
        System.getOut().println("ok")
        return 0
    }
}
