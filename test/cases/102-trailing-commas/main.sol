package trailing_commas

interface Named {}

interface Sized<T,> extends Named, {
    size(value: T,): Int
}

enum Color<T,> {
    Red
    Blue(T),
}

class Pair<A, B,> implements Named, {
    pub first: A
    pub second: B

    pub static make(first: A, second: B,): Self {
        return Self {
            first,
            second,
        }
    }
}

class Main {
    pub static run(args: String...,): Int {
        values: List<Int,> = [
            1,
            2,
        ]
        map: Map<String, Int,> = {
            "a": 1,
            "b": 2,
        }
        empty: List<Int> = List<Int,>::new()
        pair: Pair<Int, Int,> = Pair<Int, Int,>::make(3, 4,)
        color: Color<Int,> = Color<Int,>::Blue(7,)

        stdout.println(pair.first,)
        stdout.println(empty.size(),)
        match values {
            [1, second,] => stdout.println(second,),
            _ => stdout.println(0,),
        }
        match color {
            Color::Blue(value,) => stdout.println(value,),
            _ => stdout.println(0,),
        }
        return 0
    }
}
