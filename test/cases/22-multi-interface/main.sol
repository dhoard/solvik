package multiiface

interface Named {

    func name(self): String
}

interface Sized {

    func size(self): Long
}

struct Box implements Named, Sized {

    public static func new(): Self {
        return Self {}
    }

    public func name(self): String {
        return "box"
    }

    public func size(self): Long {
        return 42
    }
}

struct Main {

    public static func run(args: String...): Long {
        let b: Box = Box.new()
        // assignable through each interface type
        let n: Named = b
        let s: Sized = b
        if n.name() != "box" { return 1 }
        if s.size() != 42 { return 2 }
        System.getOut().println("ok")
        return 0
    }
}
