package native_args

func main() -> Int {
    values: List<String> = args()
    if values.len() != 0 {
        return 1
    }
    return 0
}
