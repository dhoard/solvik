// expected C125: static methods cannot be mutating
package reference_invalid

struct S {
    pub mut static func f() -> Int {
        return 1
    }
}

func main() -> Int {
    return 0
}
