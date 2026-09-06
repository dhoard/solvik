package conformance

trait Resizable {
    mut func scale(factor: Int)
}

struct Counter {
    pub mut value: Int

    pub func read() -> Int {
        return self.value
    }

    pub mut func scale(factor: Int) {
        self.value = self.value * factor
    }
}

func main() -> Int {
    readOnly: Counter = Counter { value: 4 }
    if readOnly.read() != 4 {
        return 1
    }

    mut c: Counter = Counter { value: 2 }
    c.scale(3)

    values: Map<String, Int> = { "answer": c.read() }
    mut sum: Int = 0
    for key, value in values {
        sum = sum + value
    }

    if values.len() != 1 || "ok".len() != 2 || sum != 6 {
        return 1
    }
    return 0
}
