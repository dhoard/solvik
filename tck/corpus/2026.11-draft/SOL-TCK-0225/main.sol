func f(): Boolean {
    print("c")
    return true
}
var r = false && f()
var s = true && f()
print("and" .. r .. s)
