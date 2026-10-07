// Solvik TCK SOL-TCK-0108
// The offsets are computed by hand: in "a1b22c333" the digit runs occupy indices 1, 3-4 and 6-8, so with `end` exclusive the spans are 1:2, 3:5 and 6:9; an inclusive reading would report 1:3, 3:6 and 6:10, which this oracle would reject. Order is asserted here because this is the one place section 14 states it, unlike section 11 where `Map` is given no iteration order. Each match is emitted as one `value:start:end` token so a different grouping of the same characters stays falsifiable. The trailing 2 and 1 are the non-overlapping counts for `aa` over "aaaa" and "aaa".
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `find` returns the first non-overlapping match and `findAll` returns all non-overlapping matches from left to right
//   - Offsets are zero-based character offsets and `end` is exclusive.
//
var r: Regex = Regex(r#"\d+"#)
var all: List<RegexMatch> = r.findAll("a1b22c333")
print(all.size)
for (i in 0..<all.size) {
  var m: RegexMatch = all.get(i)
  print(" ")
  print(m.value)
  print(":")
  print(m.start)
  print(":")
  print(m.end)
}
print(" ")
print(Regex(r#"aa"#).findAll("aaaa").size)
print(" ")
print(Regex(r#"aa"#).findAll("aaa").size)
