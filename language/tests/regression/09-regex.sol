var re: Regex = Regex(r#"^\d+$"#)
println(re.matches("123"))
println(re.matches("12a"))
var found: RegexMatch? = re.find("abc123def")
if (found != null) {
    println(found.value)
    println(found.start)
    println(found.end)
}
println(Regex(r#"a"#).replace("aba", "X"))
