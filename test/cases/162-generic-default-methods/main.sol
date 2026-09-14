package gendef

trait Boxed {

    func put<T>(self, v: T): T {
        return v
    }
}

struct Impl implements Boxed {

    pub func new(): Self {
        return Self {}
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let i: Impl = Impl.new()
        let r: Long = i.put(42)
        System.getOut().println(r)
        let b: Boxed = i
        let s: String = b.put("abc")
        System.getOut().println(s.length())
        return 0
    }
}
