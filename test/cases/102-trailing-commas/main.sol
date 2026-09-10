module trailingcommas

interface Named {}

interface Sized<T,> extends Named {

    size(value: T,): Long
}

enum Color<T,> {

    red
    blue(T)
}

class Pair<A, B,> implements Named {

    public first: A
    public second: B

    public static make(first: A, second: B,): Self {
        return Self {
            first: first,
            second: second,
        }
    }
}

class Main {

    public static run(args: String...,): Long {
        let values: List<Long,> = [
        1,
        2,
        ]
        let map: Map<String, Long,> = {
            "a": 1,
            "b": 2,
        }
        let empty: List<Long> = List<Long,>.new()
        let pair: Pair<Long, Long,> = Pair<Long, Long,>.make(3, 4,)
        let color: Color<Long,> = Color<Long,>.blue(7,)

        stdout.println(pair.first,)
        stdout.println(empty.size(),)
        match values {
            [1, second,] => stdout.println(second,)
            _ => stdout.println(0,)
        }
        match color {
            Color.blue(value,) => stdout.println(value,)
            _ => stdout.println(0,)
        }
        return 0
    }
}
