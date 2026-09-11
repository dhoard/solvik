package competingdefaults

interface A {

    f(): Long {
        return 1
    }
}

interface B extends A {

    f(): Long {
        return 2
    }
}

interface C extends A {

    f(): Long {
        return 3
    }
}

class X implements B, C {

    public static new(): Self {
        return Self {}
    }
}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
