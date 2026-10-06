func f(): Boolean {
    print("c")
    return true
}
var r = true || f()
var s = false || f()
print("or" .. r .. s)
