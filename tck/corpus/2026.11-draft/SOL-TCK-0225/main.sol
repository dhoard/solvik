func f(): Boolean {
    print("c")
    return true
}
val r = false && f()
val s = true && f()
print("and" .. r .. s)
