package genret

trait Boxed {

    func put<T>(self, v: T): T
}

struct Inner implements Boxed {

    public func new(): Self {
        return Self {}
    }

    public func put<T>(self, v: T): T {
        return v
    }
}

struct Outer implements Boxed {

    inner: Inner

    delegate Boxed to inner

    public func new(): Self {
        return Self { inner: Inner.new(), }
    }
}

struct Pair<A, B> {

    firstValue: A
    secondValue: B

    public func of(x: A, y: B): Self {
        return Self { firstValue: x, secondValue: y, }
    }

    public func first(self): A {
        return self.firstValue
    }
}

struct Main {

    public func run(args: String...): Long {
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
