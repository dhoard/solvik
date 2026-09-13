package jsoncycle
struct Main {

    public static func run(args: String...): Long {
        let items: List<Object> = []
        items.add(items)
        System.getOut().println(Json.stringify(items))
        return 0
    }
}
