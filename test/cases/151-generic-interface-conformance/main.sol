package genifconforms

interface Collection<T> {
    first(): T
}

class Box<T> implements Collection<T> {
    value: T
    public static new(value: T): Self {
        return Self { value: value, }
    }
    public first(): T {
        return self.value
    }
}

class Main {
    public static run(args: String...): Long {
        // A generic class conforms to its interface binding after
        // substituting the class's type arguments.
        let c: Collection<Long> = Box<Long>.new(42)
        stdout.println(c.first())
        let s: Collection<String> = Box<String>.new("ok")
        stdout.println(s.first())
        return 0
    }
}
