package native_struct

struct Box<T> {
    value: T

    func get() -> T {
        return self.value
    }
}

func main() -> int {
    box: Box<int> = Box { value: 41 }
    if box.get() != 41 {
        return 1
    }
    return 0
}
