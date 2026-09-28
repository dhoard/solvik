enum Color {
    RED
    GREEN
}
val c = Color.GREEN
val n = match c {
    RED => {
        val t = 1
        print("r")
        t
    }
    GREEN => {
        val t = 2
        print("g")
        t
    }
}
print(n)
