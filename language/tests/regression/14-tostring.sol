class Money {
    val amount: Int
    Money(amount: Int) {
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
