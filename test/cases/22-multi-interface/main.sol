module multiiface

interface Named {

    name(): String
}

interface Sized {

    size(): Long
}

class Box implements Named, Sized {

    public static new(): Self {
        return Self {}
    }

    override public name(): String {
        return "box"
    }

    override public size(): Long {
        return 42
    }
}

class Main {

    public static run(args: String...): Long {
        let b: Box = Box.new()
        // assignable through each interface type
        let n: Named = b
        let s: Sized = b
        if n.name() != "box" { return 1 }
        if s.size() != 42 { return 2 }
        stdout.println("ok")
        return 0
    }
}
