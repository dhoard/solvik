// expected C091: duplicate method name in a struct (no overloading)
package reference_invalid

struct S {
    pub func f() -> Int {
        return 1
    }
    pub static func f() -> Int {
        return 2
    }
}

func main() -> Int {
    return 0
}
