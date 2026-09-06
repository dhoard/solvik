// expect: C068
package conformance

struct Counter {
    pub mut value: Int

    pub func increment() {
        self.value = self.value + 1
    }
}

func main() -> Int {
    mut c: Counter = Counter { value: 0 }
    c.increment()
    return 0
}
