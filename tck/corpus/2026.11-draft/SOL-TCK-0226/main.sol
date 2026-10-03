func f(): Boolean {
    print("c")
    return true
}
val r = true || f()
val s = false || f()
print("or" .. r .. s)
