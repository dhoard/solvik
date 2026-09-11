package composition

interface Identified {

    id(): Long
}

class Parent implements Identified {

    idValue: Long

    public static new(id: Long): Self {
        return Self { idValue: id, }
    }

    public id(): Long {
        return self.idValue
    }
}

class Child implements Identified {

    parent: Parent

    delegate Identified to parent

    public static make(id: Long): Self {
        return Self { parent: Parent.new(id), }
    }
}

class Main {

    public static run(args: String...): Long {
        let p: Parent = Parent.new(7)
        if p.id() != 7 { return 1 }
        let c: Child = Child.make(9)
        if c.id() != 9 { return 2 }
        System.out().println("ok")
        return 0
    }
}
