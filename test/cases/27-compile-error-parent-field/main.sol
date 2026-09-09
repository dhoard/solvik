module parentfield

class Parent {

    secret: Long

    public static new(): Self {
        return Self { secret: 5, }
    }
}

class Child extends Parent {

    public static make(): Self {
        // ERROR: 'secret' is a private parent field, not accessible here
        return Self { super: Parent.new(), secret: 9, }
    }
}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
