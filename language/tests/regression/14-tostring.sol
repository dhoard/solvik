class Money {
    val amount: Integer
    Money(amount: Integer) {
        this.amount = amount
    }
    override func toString(): String {
        return "$" .. this.amount
    }
}
val m: Any = Money(5)
println(m.toString())
println(Money(9))
println(1.toString())
println(true.toString())
println('q'.toString())
