// Solvik TCK SOL-TCK-0118
// Both table rows are quoted because the `Regex` row answers two opposite questions: identical pattern text must compare equal, differing text must not. The two `RegexMatch` comparisons match the same digit run `12` at two different offsets: `ab12y` places it at 2:4 while `zzab12y` places it at 4:6, so `value` is equal and only `start`/`end` differ. An equality reading only `value` would report true where the snapshot rule requires false. Changing the digits instead -- the first version of this test used `cd12y`, where `12` sits at the same offset 2:4 -- would have made `value` differ too, and the oracle would then have been satisfied without ever exercising an offset; that version also derived `false` where the snapshot rule actually requires `true`, and the runner caught it. The compared regexes come from distinct raw strings, so pattern text rather than object identity is the variable.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - | `Regex` | exact pattern source text |
//   - | `RegexMatch` | immutable snapshot: `value`, `start`, `end`, `groupCount`, and every captured group |
//
print(Regex(r#"\d+"#) == Regex(r#"\d+"#))
print(" ")
print(Regex(r#"\d+"#) == Regex(r#"\w+"#))
print(" ")
var r: Regex = Regex(r#"\d+"#)
print(r.find("ab12y") == r.find("ab12y"))
print(" ")
print(r.find("ab12y") == r.find("zzab12y"))
