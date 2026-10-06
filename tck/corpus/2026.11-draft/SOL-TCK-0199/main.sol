abstract class Base {
}
class Sub extends Base {
}
var s: Base = Sub()
var n = match s {
    sub: Sub => 4
    _ => 0
}
print("same" .. n)
