// expected C101: wrong argument type for an associated function
package reference_invalid

struct Duration {
    pub millis: Int

    pub static func seconds(n: Int) -> Duration {
        return Duration { millis: n * 1000 }
    }
}

func main() -> Int {
    Duration.seconds("five")
    return 0
}
