module generics

class Box<T> {

    mutable value: T

    public static new(value: T): Self {
        return Self { value: value, }
    }

    public get(): T {
        return self.value
    }

    public set(v: T): Void {
        self.value = v
    }
}

class Pair<A, B> {

    public first: A
    public second: B

    public static new(first: A, second: B): Self {
        return Self { first: first, second: second, }
    }

    public swap(): Pair<B, A> {
        return Pair<B, A>.new(self.second, self.first)
    }
}

class Main {

    public static run(args: String...): Long {
        b: Box<Long> = Box<Long>.new(41)
        stdout.println(b.get())
        b.set(42)
        stdout.println(b.get())
        s: Box<String> = Box<String>.new("hi")
        stdout.println(s.get())
        p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        q: Pair<String, Long> = p.swap()
        stdout.println(q.first .. "=" .. q.second)
        return 0
    }
}
