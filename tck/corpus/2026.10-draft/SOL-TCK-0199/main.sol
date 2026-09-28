sealed class Base {
}
class Sub extends Base {
}
val s: Base = Sub()
val n = match s {
    sub: Sub => 4
}
print("same" .. n)
