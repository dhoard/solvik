enum Opt {
    Some(Integer)
    None
}
val a: Opt = Opt.Some(1)
val b: Opt = Opt.Some(1)
print(a === b)

print("EXECUTED-INVALID")
