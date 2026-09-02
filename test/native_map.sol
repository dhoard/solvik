package native_map

func main() -> int {
    mut values: map<string, int> = { "answer": 41, "other": 1 }
    values["answer"] = values["answer"] + values["other"]
    mut total: int = 0
    for key, value in values {
        if key == "answer" {
            total = total + value
        }
    }
    if values["answer"] != 42 || total != 42 {
        return 1
    }
    return 0
}
