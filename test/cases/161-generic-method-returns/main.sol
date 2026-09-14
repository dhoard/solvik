package genret

trait Boxed {

    func put<T>(self, v: T): T
}

struct Inner implements Boxed {

    pub func new(): Self {
        return Self {}
    }

    pub func put<T>(self, v: T): T {
        return v
    }
}

struct Outer implements Boxed {

    inner: Inner

    delegate Boxed to inner

    pub func new(): Self {
        return Self { inner: Inner.new(), }
    }
}

struct Pair<A, B> {

    firstValue: A
    secondValue: B

    pub func of(x: A, y: B): Self {
        return Self { firstValue: x, secondValue: y, }
    }

    pub func first(self): A {
        return self.firstValue
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let i: Inner = Inner.new()
        let r: Long = i.put(42)
        System.getOut().println(r)
        let o: Outer = Outer.new()
        let s: String = o.put("abc")
        System.getOut().println(s.length())
        let p: Pair<Integer, String> = Pair.of(7, "seven")
        System.getOut().println(p.first())
        return 0
    }
}
