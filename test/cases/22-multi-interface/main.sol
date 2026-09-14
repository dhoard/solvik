package multiiface

trait Named {

    func name(self): String
}

trait Sized {

    func size(self): Long
}

struct Box implements Named, Sized {

    pub func new(): Self {
        return Self {}
    }

    pub func name(self): String {
        return "box"
    }

    pub func size(self): Long {
        return 42
    }
}

struct Main {

    pub func run(args: String...): Integer {
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
