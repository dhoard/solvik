package generics

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

    firstValue: A
    secondValue: B

    public static new(first: A, second: B): Self {
        return Self { firstValue: first, secondValue: second, }
    }

    public first(): A {
        return self.firstValue
    }

    public second(): B {
        return self.secondValue
    }

    public swap(): Pair<B, A> {
        return Pair<B, A>.new(self.secondValue, self.firstValue)
    }
}

class Main {

    public static run(args: String...): Long {
        let b: Box<Long> = Box<Long>.new(41)
        System.out().println(b.get())
        b.set(42)
        System.out().println(b.get())
        let s: Box<String> = Box<String>.new("hi")
        System.out().println(s.get())
        let p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        let q: Pair<String, Long> = p.swap()
        System.out().println(q.first() .. "=" .. q.second())
        return 0
    }
}
