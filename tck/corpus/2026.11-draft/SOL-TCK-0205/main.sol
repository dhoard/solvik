enum Color {
    RED
    GREEN
}
var c = Color.GREEN
var n = match c {
    RED => {
        var t = 1
        print("r")
        t
    }
    GREEN => {
        var t = 2
        print("g")
        t
    }
}
print(n)
