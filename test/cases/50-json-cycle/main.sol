package jsoncycle
struct Main {

    public func run(args: String...): Long {
        let items: List<Object> = []
        items.add(items)
        System.getOut().println(Json.stringify(items))
        return 0
    }
}
