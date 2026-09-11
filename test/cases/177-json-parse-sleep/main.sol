package jsonparse

class Main {

    public static run(args: String...): Long {
        // Round trip through stringify/parse.
        let m: Map<String, Object> = { "a": 1, "b": "two" }
        let text: String = Json.stringify(m)
        System.out().println(text)
        let back: Object = Json.parse(text)
        System.out().println(back)

        // Parsed scalars and lists.
        let n: Object = Json.parse("42")
        System.out().println(n)
        let l: Object = Json.parse("[1, 2]")
        System.out().println(l)

        // Time.sleep(0) is a no-op pause that still exercises the native.
        Time.sleep(0)
        System.out().println("slept")
        return 0
    }
}
