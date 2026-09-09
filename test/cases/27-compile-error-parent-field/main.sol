package parentfield

class Parent {
    secret: Int

    pub static new(): Self {
        return Self { secret: 5 }
    }
}

class Child extends Parent {
    pub static make(): Self {
        // ERROR: 'secret' is a private parent field, not accessible here
        return Self { super: Parent::new(), secret: 9 }
    }
}

class Main {
    pub static run(args: String...): Int {
        return 0
    }
}
