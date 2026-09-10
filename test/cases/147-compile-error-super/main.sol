module usesuper

class Base {
    public static new(): Self { return Self {} }
    public value(): Long { return 1 }
}

class Sub {
    public go(): Long { return super.value() }
}

class Main { public static run(args: String...): Long { return 0 } }
