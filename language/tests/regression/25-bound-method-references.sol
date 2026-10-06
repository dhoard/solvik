// Bound method references: reading a declared instance method without calling it produces a bound
// method value (docs/LANGUAGE_SPEC.md section 6, "Bound method references").
//
// The receiver expression is evaluated exactly once at creation and retained by the value; ordinary
// virtual dispatch is preserved, so the implementation is the one the captured receiver's runtime
// class selects; `this.method` binds the current receiver; `super.method` binds `this` to the
// immediate superclass implementation without redispatch; a bare unqualified method name in a value
// position stays an unknown name; each creation is a fresh identity; and a `?.` reference through a
// nullable receiver yields a nullable function value.

mutable class Shape {
    mutable func name(): String {
        return "shape"
    }
    mutable func sides(): Integer {
        return 0
    }
    func viaThis(): func(): String {
        return this.name
    }
    func callBare(): String {
        return name()
    }
}

mutable class Polygon extends Shape {
    override mutable func name(): String {
        return "polygon"
    }
    func viaSuper(): func(): String {
        return super.name
    }
}

class Triangle extends Polygon {
    override func name(): String {
        return "triangle"
    }
    override func sides(): Integer {
        return 3
    }
}

interface Label {
    func text(): String
    func shout(): String {
        return text() .. "!"
    }
}

class Badge implements Label {
    func text(): String {
        return "hi"
    }
}

class ShapeCounter {
    var inner: Shape
    var mutable evaluations: Integer = 0
    ShapeCounter(inner: Shape) {
        this.inner = inner
    }
    func target(): Shape {
        this.evaluations = this.evaluations + 1
        return this.inner
    }
}

// The receiver's runtime class selects the implementation, whether the reference is written against
// the base type or the concrete one, and an inherited method binds what the table supplies.
var shape: Shape = Shape()
var triangle: Shape = Triangle()
var shapeName: func(): String = shape.name
var triangleName: func(): String = triangle.name
println(shapeName())
println(triangleName())
println(triangle.sides())

// `this.method` is a bound reference to the current receiver, so an inherited helper still dispatches
// on the runtime class of the receiver it was reached through.
println(Triangle().viaThis()())
println(Shape().viaThis()())

// An unqualified method name remains legal as an immediate call under the implicit-`this` rule.
println(Triangle().callBare())

// `super.method` binds `this` to the immediate superclass implementation and skips redispatch, which
// is what an immediate `super.method(...)` call does.
println(Polygon().viaSuper()())
println(Triangle().viaSuper()())

// Virtual dispatch reaches an interface member through an interface-typed receiver, including a
// defaulted one, and it runs against the conforming instance.
var badge: Label = Badge()
var text: func(): String = badge.text
var shout: func(): String = badge.shout
println(text())
println(shout())

// The receiver expression is evaluated exactly once, when the value is created.
var counter = ShapeCounter(Shape())
var before = counter.evaluations
var method: func(): String = counter.target().name
println(counter.evaluations - before)
println(method())
println(counter.evaluations - before)

// Each successful evaluation creates a distinct identity, even for one receiver and method, and
// copying through a binding preserves it. Display and hashing are the fixed function-value rules.
var receiver = Shape()
var first: func(): String = receiver.name
var second: func(): String = receiver.name
var copied = first
println(first === second)
println(copied === first)
println(copied.toString())
println("value: " .. first)
println(copied.hashCode() == first.hashCode())

// A reference through a nullable receiver is a nullable function value: null with a null receiver,
// the bound method otherwise. On a non-null receiver `?.` keeps the non-null type.
var absent: Shape? = null
var none: (func(): String)? = absent?.name
println(none)
var present: Shape? = Triangle()
var some: (func(): String)? = present?.name
if (some != null) {
    println(some())
}
var concrete = Shape()
var direct: func(): String = concrete?.name
println(direct())

// A property whose declared type is a function type reads its stored value; member resolution decides
// statically which kind of read a name is, because no property and method share one member name.
class Holder {
    var mutable stored: func(): Integer
    func storedMethod(): Integer {
        return 4
    }
    Holder() {
        this.stored = func(): Integer {
            return 9
        }
    }
}

var holder = Holder()
var fromProperty: func(): Integer = holder.stored
var fromMethod: func(): Integer = holder.storedMethod
println(fromProperty())
println(fromMethod())

// A generic method reference is instantiated contextually to one monomorphic function type, with the
// receiver's own type arguments already closed.
class Box {
    func pick<T>(value: T): T {
        return value
    }
}

class Cell<T> {
    var stored: T
    Cell(stored: T) {
        this.stored = stored
    }
    mutable func swap<E>(value: E): E {
        return value
    }
}

var box = Box()
var pickInteger: func(Integer): Integer = box.pick
var pickString: func(String): String = box.pick
println(pickInteger(42))
println(pickString("s"))
var cell: Cell<Integer> = Cell(1)
var swapString: func(String): String = cell.swap
println(swapString("swapped"))
println(cell.stored)
