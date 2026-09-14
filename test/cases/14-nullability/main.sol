package nullability

struct Box<T> {

    value: T

    pub func new(value: T): Self {
        return Self { value: value, }
    }

    pub func get(self): T {
        return self.value
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let a: Long? = null
        let b: Long? = 5
        System.getOut().println(a == null)
        System.getOut().println(b == null)
        // coalesce
        let r1: Long = a ?? 7
        let r2: Long = b ?? 7
        System.getOut().println(r1)
        System.getOut().println(r2)
        // nullable from a nullable source
        let c: Long? = a
        let d: Long = c ?? 9
        System.getOut().println(d)
        return 0
    }
}
