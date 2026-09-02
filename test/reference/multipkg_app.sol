package app

use file:multipkg_lib/lib

// A same-package type may share a local name with a cross-package type.
struct User {
    pub name: string
}

func measure<T: lib.Measurer>(x: T) -> int {
    return x.measure()
}

func main() -> int {
    // Qualified annotations and construction.
    u: lib.User = lib.makeUser("alice")
    if u.name != "alice" {
        return 1
    }
    if u.age != 0 {
        return 2
    }
    // Same-package type with the same local name stays distinct.
    own: User = User { name: "x" }
    if own.name != "x" || u.name != "alice" {
        return 3
    }
    // Qualified generic types and literals.
    b: lib.Box<int> = lib.Box<int> { value: 42 }
    if b.value != 42 {
        return 4
    }
    // Cross-package generic function with inference.
    s: lib.Box<string> = lib.makeBox("hi")
    if s.value != "hi" {
        return 5
    }
    // Qualified enum members and switches.
    st: lib.Status = lib.Status.Active
    if st != lib.Status.Active {
        return 6
    }
    mut active: bool = false
    switch st {
        case lib.Status.Active {
            active = true
        }
        case lib.Status.Inactive {
            return 7
        }
    }
    if !active {
        return 8
    }
    // Qualified generic enum construction + pattern matching.
    r: lib.Outcome<int, string> = lib.Outcome.Good(5)
    switch r {
        case lib.Outcome.Good(v) {
            if v != 5 {
                return 9
            }
        }
        case lib.Outcome.Bad(e) {
            return 10
        }
    }
    // Qualified trait constraint.
    m: Measurable = Measurable { n: 3 }
    if measure(m) != 3 {
        return 11
    }
    // Qualified function call.
    if lib.two() != 2 {
        return 12
    }
    // typeOf / isType display the local name.
    if typeOf(u) != "user" {
        return 13
    }
    return 0
}

struct Measurable {
    pub n: int

    pub func measure() -> int {
        return self.n
    }
}
