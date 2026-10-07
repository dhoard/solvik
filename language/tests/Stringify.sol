// Solvik toString and `..` concatenation: a class-specific representation plus scalar rendering.
class Money {
    var cents: Integer

    Money(cents: Integer) {
        this.cents = cents
    }

    method override toString(): String {
        return "$" .. this.cents
    }
}

var price: Money = Money(1250)
println(price)
println(price.toString())
println("price=" .. price)
println("scalars: " .. 3 .. ", " .. true .. ", " .. 'x' .. ", " .. 2.5)

var missing: String? = null
println(missing)
