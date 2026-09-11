package nullability

class Box<T> {

    value: T

    public static new(value: T): Self {
        return Self { value: value, }
    }

    public get(): T {
        return self.value
    }
}

class Main {

    public static run(args: String...): Long {
        let a: Long? = null
        let b: Long? = 5
        System.out().println(a == null)
        System.out().println(b == null)
        // coalesce
        let r1: Long = a ?? 7
        let r2: Long = b ?? 7
        System.out().println(r1)
        System.out().println(r2)
        // nullable from a nullable source
        let c: Long? = a
        let d: Long = c ?? 9
        System.out().println(d)
        return 0
    }
}
