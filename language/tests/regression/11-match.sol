enum Grade {
    A
    B
    Other(Integer)
}
func classify(value: Grade): String {
    return match value {
        A => "excellent"
        B => "good"
        Other(n) => "other " .. n
    }
}
println(classify(Grade.A))
println(classify(Grade.B))
println(classify(Grade.Other(3)))
