module nullability

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
        a: Long? = null
        b: Long? = 5
        stdout.println(a == null)
        stdout.println(b == null)
        // coalesce
        r1: Long = a ?? 7
        r2: Long = b ?? 7
        stdout.println(r1)
        stdout.println(r2)
        // nullable from a nullable source
        c: Long? = a
        d: Long = c ?? 9
        stdout.println(d)
        return 0
    }
}
