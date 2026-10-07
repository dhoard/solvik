func f(): Boolean {
    print("c")
    return true
}
var r: Boolean = true || f()
var s: Boolean = false || f()
print("or" .. r .. s)
