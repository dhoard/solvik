package reference_generic_ambient

struct Box<T> {
    pub value: T
}

struct Wrap<T: Stringable> {
    pub inner: T

    pub func boxed() -> Box<T> {
        return Box<T> { value: self.inner }
    }
}

// Explicit type arguments may reference an enclosing declaration's type
// parameter; they resolve to the caller's instantiation at runtime.
func wrap<T: Stringable>(v: T) -> Box<T> {
    return Box<T> { value: v }
}

func main() -> int {
    b: Box<int> = wrap(42)
    if b.value != 42 {
        return 1
    }
    w: Wrap<string> = Wrap { inner: "solvik" }
    c: Box<string> = w.boxed()
    if c.value != "solvik" {
        return 2
    }
    if typeOf(c) != "box" {
        return 3
    }
    return 0
}
