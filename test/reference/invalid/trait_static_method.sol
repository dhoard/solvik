// expected C125: traits cannot declare static methods
package reference_invalid

trait T {
    static func f() -> Int
}

func main() -> Int {
    return 0
}
