enum Color {
    RED
    GREEN
}
var c: Color = Color.GREEN
var n: Integer = match c {
    RED => {
        var t: Integer = 1
        print("r")
        t
    }
    GREEN => {
        var t: Integer = 2
        print("g")
        t
    }
}
print(n)
