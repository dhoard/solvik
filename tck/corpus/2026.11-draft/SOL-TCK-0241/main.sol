var l: List<Integer> = List<Integer>(1, 2)
var m: List<Integer> = List<Integer>(1, 2)
var n: List<Integer> = l
print("col" .. (l === m) .. (l === n))
