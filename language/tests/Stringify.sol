// Solvik toString and `..` concatenation: a class-specific representation plus scalar rendering.
class Money {
    val cents: Int

    Money(cents: Int) {
        this.cents = cents
    }

    override func toString(): String {
        return "$" .. this.cents
    }
}

val price = Money(1250)
println(price)
println(price.toString())
println("price=" .. price)
println("scalars: " .. 3 .. ", " .. true .. ", " .. 'x' .. ", " .. 2.5)

val missing: String? = null
println(missing)
