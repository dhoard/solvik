// Solvik switch: constants, regex cases, first-match order, and no fallthrough.
fun classify(value: String): String {
    switch (value) {
        case "zero":
            return "zero"
        case regex r#"^\d+$"#:
            return "number"
        case regex r#"^[A-Za-z]+$"#:
            return "word"
        default:
            return "other"
    }
}

fun main(): Unit {
    println(classify("zero"))
    println(classify("42"))
    println(classify("hello"))
    println(classify("!!"))
}
