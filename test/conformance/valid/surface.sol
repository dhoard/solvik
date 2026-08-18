package conformance

trait Resizable {
    mut func scale(factor: int)
}

struct Counter {
    pub mut value: int

    pub func read() -> int {
        return self.value
    }

    pub mut func scale(factor: int) {
        self.value = self.value * factor
    }
}

func main() -> int {
    readOnly: Counter = Counter { value: 4 }
    if readOnly.read() != 4 {
        return 1
    }

    mut c: Counter = Counter { value: 2 }
    c.scale(3)

    values: map<string, int> = { "answer": c.read() }
    mut sum: int = 0
    for key, value in values {
        sum = sum + value
    }

    if values.len() != 1 || "ok".len() != 2 || sum != 6 {
        return 1
    }
    return 0
}
