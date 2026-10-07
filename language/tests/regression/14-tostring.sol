class Money {
    var amount: Integer
    Money(amount: Integer) {
        this.amount = amount
    }
    method override toString(): String {
        return "$" .. this.amount
    }
}
var m: Any = Money(5)
println(m.toString())
println(Money(9))
println(1.toString())
println(true.toString())
println('q'.toString())
