package multiiface

trait Named {

    func name(self): String
}

trait Sized {

    func size(self): Long
}

struct Box implements Named, Sized {

    public func new(): Self {
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

    public func run(args: String...): Long {
        let b: Box = Box.new()
        // assignable through each trait type
        let n: Named = b
        let s: Sized = b
        if n.name() != "box" { return 1 }
        if s.size() != 42 { return 2 }
        System.getOut().println("ok")
        return 0
    }
}
