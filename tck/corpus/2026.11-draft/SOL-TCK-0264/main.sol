enum Opt {
    Some(Integer)
    None
}
var a: Opt = Opt.Some(3)
var b: Opt = Opt.Some(3)
var c: Opt = Opt.Some(4)
var d: Opt = Opt.None
print("en" .. (a == b) .. (a == c) .. (a == d))
