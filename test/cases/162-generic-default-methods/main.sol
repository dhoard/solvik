package gendef

interface Boxed {

    func put<T>(self, v: T): T {
        return v
    }
}

struct Impl implements Boxed {

    public static func new(): Self {
        return Self {}
    }
}

struct Main {

    public static func run(args: String...): Long {
        let i: Impl = Impl.new()
        let r: Long = i.put(42)
        System.getOut().println(r)
        let b: Boxed = i
        let s: String = b.put("abc")
        System.getOut().println(s.length())
        return 0
    }
}
