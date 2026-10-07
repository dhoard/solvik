func f(): Boolean {
    print("c")
    return true
}
var r: Boolean = false && f()
var s: Boolean = true && f()
print("and" .. r .. s)
