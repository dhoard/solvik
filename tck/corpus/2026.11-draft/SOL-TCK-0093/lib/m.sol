module geom

class Point {
    mutable val x: Integer

    Point(v: Integer) {
        this.x = v
    }
}

func scale(v: Integer): Integer {
    return v * 2
}
