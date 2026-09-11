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
        System.out().println(r)
        let b: Boxed = i
        let s: String = b.put("abc")
        System.out().println(s.length())
        return 0
    }
}
