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

func main() -> Int {
    a: Int = identity<Int>(42)
    b: Pair<Int, String> = Pair<Int, String> { key: 1, value: "one" }
    c: String = b.swapKey<String>("s")
    d: Pair<Int?, String?> = Pair<Int?, String?> { key: null, value: null }
    xs: List<Pair<Byte, Float>> = [Pair<Byte, Float> { key: Byte(1), value: 2.5 }]
    return a + (d.key ?? 0)
}
