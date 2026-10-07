var a: List<Integer> = List<Integer>(1, 2)
var b: List<Integer> = List<Integer>(1, 2)
var c: List<Integer> = a
print("ce" .. (a == b) .. (a == c) .. (a.hashCode() == a.hashCode()))
