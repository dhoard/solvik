module jsoncycle
class Main {

    public static run(args: String...): Long {
        items: List<Object> = []
        items.add(items)
        stdout.println(Json.stringify(items))
        return 0
    }
}
