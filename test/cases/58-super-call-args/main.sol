package supercall

class Base {
    mut {
        n: Int
    }

    pub static new(): Base {
        return Self { n: 0 }
    }

    pub bump(): Int {
        n += 1
        return n
    }

    pub useBump(x: Int): Int {
        return x + bump()
    }

    pub count(): Int {
        return n
    }
}

class Sub extends Base {
    pub static new(): Sub {
        return Self { super: Base::new() }
    }

    pub go(): Int {
        return super.useBump(bump())
    }
}

class Main {
    pub static run(args: String...): Int {
        s: Sub = Sub::new()
        r: Int = s.go()
        stdout.println(r)
        stdout.println(s.count())
        return 0
    }
}
