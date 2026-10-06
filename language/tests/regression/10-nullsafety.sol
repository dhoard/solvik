class Holder {
    var name: String?
    Holder(name: String?) {
        this.name = name
    }
}
func show(h: Holder): String {
    var name = h.name
    if (name == null) {
        return "none"
    }
    return name.toString()
}
var present = Holder("abc")
var missing = Holder(null)
println(show(present))
println(show(missing))
println(missing.name ?? "none")
println(present.name?.toString())
