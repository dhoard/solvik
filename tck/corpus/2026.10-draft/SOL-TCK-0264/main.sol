enum Opt {
    Some(Integer)
    None
}
val a: Opt = Opt.Some(3)
val b: Opt = Opt.Some(3)
val c: Opt = Opt.Some(4)
val d: Opt = Opt.None
print("en" .. (a == b) .. (a == c) .. (a == d))
