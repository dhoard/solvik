package native_map

func main() -> Int {
    mut values: Map<String, Int> = { "answer": 41, "other": 1 }
    values["answer"] = values["answer"] + values["other"]
    mut total: Int = 0
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
