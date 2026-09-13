package generics

struct Box<T> {

    mutable value: T

    public static func new(value: T): Self {
        return Self { value: value, }
    }

    public func get(self): T {
        return self.value
    }

    public func set(self, v: T): Void {
        self.value = v
    }
}

struct Pair<A, B> {

    firstValue: A
    secondValue: B

    public static func new(first: A, second: B): Self {
        return Self { firstValue: first, secondValue: second, }
    }

    public func first(self): A {
        return self.firstValue
    }

    public func second(self): B {
        return self.secondValue
    }

    public func swap(self): Pair<B, A> {
        return Pair<B, A>.new(self.secondValue, self.firstValue)
    }
}

struct Main {

    public static func run(args: String...): Long {
        let b: Box<Long> = Box<Long>.new(41)
        System.getOut().println(b.get())
        b.set(42)
        System.getOut().println(b.get())
        let s: Box<String> = Box<String>.new("hi")
        System.getOut().println(s.get())
        let p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        let q: Pair<String, Long> = p.swap()
        System.getOut().println(q.first() .. "=" .. q.second())
        return 0
    }
}
