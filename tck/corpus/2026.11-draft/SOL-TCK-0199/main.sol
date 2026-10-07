class abstract Base {
}
class Sub extends Base {
}
var s: Base = Sub()
var n: Integer = match s {
    sub: Sub => 4
    _ => 0
}
print("same" .. n)
