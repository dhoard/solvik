package native_args

func main() -> int {
    values: list<string> = process.args()
    if values.len() != 0 {
        return 1
    }
    return 0
}
