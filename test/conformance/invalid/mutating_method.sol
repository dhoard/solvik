// expect: C068
package conformance

struct Counter {
    pub mut value: int

    pub func increment() {
        self.value = self.value + 1
    }
}

func main() -> int {
    mut c: Counter = Counter { value: 0 }
    c.increment()
    return 0
}
