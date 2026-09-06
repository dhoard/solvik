package native_enum

enum Result<T> {
    Ok(T)
    Error(String)
}

func main() -> Int {
    result: Result<Int> = Result.Ok(41)
    switch result {
        case Result.Ok(value) {
            if value != 41 {
                return 1
            }
        }
        case Result.Error(message) {
            return 2
        }
    }
    if typeOf(result) != "Result" {
        return 3
    }
    if result != Result.Ok(41) {
        return 4
    }
    return 0
}
