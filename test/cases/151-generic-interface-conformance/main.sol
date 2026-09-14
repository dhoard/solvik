package genifconforms

trait Collection<T> {
    func first(self): T
}

struct Box<T> implements Collection<T> {
    value: T
    public func new(value: T): Self {
        return Self { value: value, }
    }
    public func first(self): T {
        return self.value
    }
}

struct Main {
    public func run(args: String...): Long {
        // A generic struct conforms to its trait binding after
        // substituting the struct's type arguments.
        let c: Collection<Long> = Box<Long>.new(42)
        System.getOut().println(c.first())
        let s: Collection<String> = Box<String>.new("ok")
        System.getOut().println(s.first())
        return 0
    }
}
