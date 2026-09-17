// Solvik regular expressions: raw-string patterns, capture groups, find, findAll, and replace.
func main(): Unit {
    val pair = Regex(r#"(\w+)=(\d+)"#)
    val found = pair.find("count=42")
    if (found != null) {
        println(found.group(1) ?? "none")
        println(found.group(2) ?? "none")
    }

    val digits = Regex(r#"\d+"#)
    val all = digits.findAll("a1b22c333")
    println(all.size)
    println(digits.replace("a1b2", "#"))
}
