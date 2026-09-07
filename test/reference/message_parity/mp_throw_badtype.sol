package mp_throw_badtype
func boom() -> Int {
    throw 5
}
func main() -> Int {
    return boom()
}
