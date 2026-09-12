package mutablekey

class Main {

    public static run(args: String...): Long {
        let k: List<Long> = [1]
        let m: Map<List<Long>, Long> = Map.new()
        m.put(k, 1)
        return 0
    }
}
