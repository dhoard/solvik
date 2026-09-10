module genret

interface Boxed {

    put<T>(v: T): T
}

class Inner implements Boxed {

    public static new(): Self {
        return Self {}
    }

    public put<T>(v: T): T {
        return v
    }
}

class Outer implements Boxed {

    inner: Inner

    delegate Boxed to inner

    public static new(): Self {
        return Self { inner: Inner.new(), }
    }
}

class Pair<A, B> {

    firstValue: A
    secondValue: B

    public static of(x: A, y: B): Self {
        return Self { firstValue: x, secondValue: y, }
    }

    public first(): A {
        return self.firstValue
    }
}

class Main {

    public static run(args: String...): Long {
        let i: Inner = Inner.new()
        let r: Long = i.put(42)
        stdout.println(r)
        let o: Outer = Outer.new()
        let s: String = o.put("abc")
        stdout.println(s.length())
        let p: Pair<Long, String> = Pair.of(7, "seven")
        stdout.println(p.first())
        return 0
    }
}
