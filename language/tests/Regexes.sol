// Solvik regular expressions: raw-string patterns, capture groups, find, findAll, and replace.
var pair: Regex = Regex(r#"(\w+)=(\d+)"#)
var found: RegexMatch? = pair.find("count=42")
if (found != null) {
    println(found.group(1) ?? "none")
    println(found.group(2) ?? "none")
}

var digits: Regex = Regex(r#"\d+"#)
var all: List<RegexMatch> = digits.findAll("a1b22c333")
println(all.size)
println(digits.replace("a1b2", "#"))
