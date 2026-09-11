package jsoncycle
class Main {

    public static run(args: String...): Long {
        let items: List<Object> = []
        items.add(items)
        System.out().println(Json.stringify(items))
        return 0
    }
}
