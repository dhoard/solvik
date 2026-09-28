val a = List<Integer>(1, 2)
val b = List<Integer>(1, 2)
val c = a
print("ce" .. (a == b) .. (a == c) .. (a.hashCode() == a.hashCode()))
