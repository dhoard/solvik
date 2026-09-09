package jsoncycle
class Main {
    pub static run(args: String...): Int {
        items: List<Object> = []
        items.add(items)
        stdout.println(Json::stringify(items))
        return 0
    }
}
