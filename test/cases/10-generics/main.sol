package generics

class Box<T> {
    value: T

    pub static new(value: T): Self {
        return Self { value }
    }

    pub get(): T {
        return value
    }

    pub set(v: T): Void {
        value = v
    }
}

class Pair<A, B> {
    pub first: A
    pub second: B

    pub static new(first: A, second: B): Self {
        return Self { first, second }
    }

    pub swap(): Pair<B, A> {
        return Pair<B, A>::new(second, first)
    }
}

class Main {
    pub static run(args: String...): Int {
        b: Box<Int> = Box<Int>::new(41)
        stdout.println(b.get())
        b.set(42)
        stdout.println(b.get())
        s: Box<String> = Box<String>::new("hi")
        stdout.println(s.get())
        p: Pair<Int, String> = Pair<Int, String>::new(7, "seven")
        q: Pair<String, Int> = p.swap()
        stdout.println(q.first .. "=" .. q.second)
        return 0
    }
}
