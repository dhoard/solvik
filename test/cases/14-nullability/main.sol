package nullability

class Box<T> {
    value: T

    pub static new(value: T): Self {
        return Self { value }
    }

    pub get(): T {
        return value
    }
}

class Main {
    pub static run(args: String...): Int {
        a: Int? = null
        b: Int? = 5
        stdout.println(a == null)
        stdout.println(b == null)
        // coalesce
        r1: Int = a ?? 7
        r2: Int = b ?? 7
        stdout.println(r1)
        stdout.println(r2)
        // nullable from a nullable source
        c: Int? = a
        d: Int = c ?? 9
        stdout.println(d)
        return 0
    }
}
