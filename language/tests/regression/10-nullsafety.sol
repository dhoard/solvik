class Holder {
    val name: String?
    Holder(name: String?) {
        this.name = name
    }
}
func show(h: Holder): String {
    val name = h.name
    if (name == null) {
        return "none"
    }
    return name.toString()
}
val present = Holder("abc")
val missing = Holder(null)
println(show(present))
println(show(missing))
println(missing.name ?? "none")
println(present.name?.toString())
