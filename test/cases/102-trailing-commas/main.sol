package trailingcommas

trait Named {}

trait Sized<T,> extends Named {

    func size(self, value: T,): Long
}

enum Color<T,> {

    red
    blue(T)
}

struct Pair<A, B,> implements Named {

    firstValue: A
    secondValue: B

    public func make(first: A, second: B,): Self {
        return Self {
            firstValue: first,
            secondValue: second,
        }
    }

    public func first(self): A {
        return self.firstValue
    }
}

struct Main {

    public func run(args: String...,): Integer {
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

        System.getOut().println(pair.first(),)
        System.getOut().println(empty.size(),)
        match values {
            [1, second,] => System.getOut().println(second,)
            _ => System.getOut().println(0,)
        }
        match color {
            Color.blue(value,) => System.getOut().println(value,)
            _ => System.getOut().println(0,)
        }
        return 0
    }
}
