module geom {

    class Point {
        var mutable x: Integer

        Point(v: Integer) {
            this.x = v
        }
    }

    func scale(v: Integer): Integer {
        return v * 2
    }
}
