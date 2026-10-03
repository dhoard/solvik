abstract class Base {
}
class Sub extends Base {
}
val s: Base = Sub()
val n = match s {
    sub: Sub => 4
    _ => 0
}
print("same" .. n)
