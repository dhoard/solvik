package parentcon

class Parent {
    pub id: Int

    pub static new(id: Int): Self {
        return Self { id }
    }
}

class Child extends Parent {
    // subclass factory uses super: Parent::new(...) to initialize parent fields
    pub static make(id: Int): Self {
        return Self { super: Parent::new(id) }
    }
}

class Main {
    pub static run(args: String...): Int {
        p: Parent = Parent::new(7)
        if p.id != 7 { return 1 }
        c: Child = Child::make(9)
        if c.id != 9 { return 2 }
        stdout.println("ok")
        return 0
    }
}
