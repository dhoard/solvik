package conformance_generic_explicit

func identity<T>(value: T) -> T {
    return value
}

struct Pair<K, V> {
    pub key: K
    pub value: V

    pub func swapKey<U>(other: U) -> U {
        return other
    }
}

func main() -> int {
    a: int = identity<int>(42)
    b: Pair<int, string> = Pair<int, string> { key: 1, value: "one" }
    c: string = b.swapKey<string>("s")
    d: Pair<int?, string?> = Pair<int?, string?> { key: null, value: null }
    xs: list<Pair<byte, float>> = [Pair<byte, float> { key: byte(1), value: 2.5 }]
    return a + (d.key ?? 0)
}
