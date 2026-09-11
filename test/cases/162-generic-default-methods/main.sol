package gendef

interface Boxed {

    put<T>(v: T): T {
        return v
    }
}

class Impl implements Boxed {

    public static new(): Self {
        return Self {}
    }
}

class Main {

    public static run(args: String...): Long {
        let i: Impl = Impl.new()
        let r: Long = i.put(42)
        stdout.println(r)
        let b: Boxed = i
        let s: String = b.put("abc")
        stdout.println(s.length())
        return 0
    }
}
