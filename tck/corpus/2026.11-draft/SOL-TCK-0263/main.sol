var a = List<Integer>(1, 2)
var b = List<Integer>(1, 2)
var c = a
print("ce" .. (a == b) .. (a == c) .. (a.hashCode() == a.hashCode()))
