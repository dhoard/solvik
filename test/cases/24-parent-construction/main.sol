module parentcon

class Parent {

    public id: Long

    public static new(id: Long): Self {
        return Self { id: id, }
    }
}

class Child extends Parent {

    // subclass factory uses super: Parent.new(...) to initialize parent fields
    public static make(id: Long): Self {
        return Self { super: Parent.new(id), }
    }
}

class Main {

    public static run(args: String...): Long {
        let p: Parent = Parent.new(7)
        if p.id != 7 { return 1 }
        let c: Child = Child.make(9)
        if c.id != 9 { return 2 }
        stdout.println("ok")
        return 0
    }
}
