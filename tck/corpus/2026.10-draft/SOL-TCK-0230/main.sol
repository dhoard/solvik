func p(): Integer {
    print("P")
    return 1
}

func q(): Integer {
    print("Q")
    return 2
}
print(p() < q())
print(q() < p())
