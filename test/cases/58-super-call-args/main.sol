module supercall

class Base {

    mutable n: Long

    public static new(): Self {
        return Self { n: 0, }
    }

    public bump(): Long {
        self.n += 1
        return self.n
    }

    public useBump(x: Long): Long {
        return x + bump()
    }

    public count(): Long {
        return self.n
    }
}

class Sub extends Base {

    public static new(): Self {
        return Self { super: Base.new(), }
    }

    public go(): Long {
        return super.useBump(bump())
    }
}

class Main {

    public static run(args: String...): Long {
        let s: Sub = Sub.new()
        let r: Long = s.go()
        stdout.println(r)
        stdout.println(s.count())
        return 0
    }
}
