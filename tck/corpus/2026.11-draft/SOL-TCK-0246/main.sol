enum Opt {
    Some(Integer)
    None
}
var a: Opt = Opt.Some(1)
var b: Opt = Opt.Some(1)
print(a === b)

print("EXECUTED-INVALID")
