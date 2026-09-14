package jsonparse

struct Main {

    public func run(args: String...): Integer {
        // Round trip through stringify/parse.
        let m: Map<String, Object> = { "a": 1, "b": "two" }
        let text: String = Json.stringify(m)
        System.getOut().println(text)
        let back: Object = Json.parse(text)
        System.getOut().println(back)

        // Parsed scalars and lists.
        let n: Object = Json.parse("42")
        System.getOut().println(n)
        let l: Object = Json.parse("[1, 2]")
        System.getOut().println(l)

        // Time.sleep(0) is a no-op pause that still exercises the native.
        Time.sleep(0)
        System.getOut().println("slept")
        return 0
    }
}
