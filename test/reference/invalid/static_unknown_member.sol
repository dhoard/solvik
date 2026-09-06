// expected C126: unknown associated function on a struct type
package reference_invalid

struct User {
    pub name: String
}

func main() -> Int {
    User.nope()
    return 0
}
