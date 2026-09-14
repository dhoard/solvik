package jsoncycle
struct Main {

    pub func run(args: String...): Integer {
        let items: List<Object> = []
        items.add(items)
        System.getOut().println(Json.stringify(items))
        return 0
    }
}
