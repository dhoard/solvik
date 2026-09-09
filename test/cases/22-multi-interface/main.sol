package multiiface

interface Named {
    name(): String
}

interface Sized {
    size(): Int
}

class Box implements Named, Sized {
    pub static new(): Self {
        return Self {}
    }

    override pub name(): String {
        return "box"
    }

    override pub size(): Int {
        return 42
    }
}

class Main {
    pub static run(args: String...): Int {
        b: Box = Box::new()
        // assignable through each interface type
        n: Named = b
        s: Sized = b
        if n.name() != "box" { return 1 }
        if s.size() != 42 { return 2 }
        stdout.println("ok")
        return 0
    }
}
