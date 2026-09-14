package generics

struct Box<T> {

    var value: T

    pub func new(value: T): Self {
        return Self { value: value, }
    }

    pub func get(self): T {
        return self.value
    }

    pub func set(self, v: T) {
        self.value = v
    }
}

struct Pair<A, B> {

    firstValue: A
    secondValue: B

    pub func new(first: A, second: B): Self {
        return Self { firstValue: first, secondValue: second, }
    }

    pub func first(self): A {
        return self.firstValue
    }

    pub func second(self): B {
        return self.secondValue
    }

    pub func swap(self): Pair<B, A> {
        return Pair<B, A>.new(self.secondValue, self.firstValue)
    }
}

struct Main {

    pub func run(args: String...): Integer {
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
