package example

enum Color {
    Red,
    Green,
    Blue,
}

enum HttpStatus {
    OK = 200,
    NotFound = 404,
    InternalError = 500,
}

enum Permission {
    Read = 4,
    Write = 2,
    Execute = 1,
}

enum Mixed {
    A = 10,
    B,
    C,
}

enum WithTrailing {
    X,
    Y,
    Z,
}

func main() -> int {
    // --- Basic enum assignment and comparison ---
    c: Color = Color.Red
    if c != Color.Red {
        return 1
    }

    // --- Enum with explicit values ---
    s: HttpStatus = HttpStatus.NotFound
    if s != 404 {
        return 2
    }

    // --- Enum to int comparison ---
    if Color.Green != 1 {
        return 3
    }

    // --- Int to enum comparison ---
    if 0 != Color.Red {
        return 4
    }

    // --- Auto-assigned values ---
    // Red=0, Green=1, Blue=2
    if Color.Blue != 2 {
        return 5
    }

    // --- Explicit + auto-assigned mix ---
    if Mixed.A != 10 || Mixed.B != 11 || Mixed.C != 12 {
        return 6
    }

    // --- Trailing comma ---
    if WithTrailing.Z != 2 {
        return 7
    }

    // --- Enum as integer ---
    count: int = Color.Red
    if count != 0 {
        return 8
    }

    // --- Bitwise flags pattern ---
    perms: int = Permission.Read | Permission.Write
    if perms & Permission.Read == 0 {
        return 9
    }
    if perms & Permission.Execute != 0 {
        return 10
    }

    // --- Enum as map key ---
    scores: map<Color, int> = {
        Color.Red: 10,
        Color.Green: 20,
        Color.Blue: 30,
    }
    if scores[Color.Red] != 10 {
        return 11
    }

    // --- Enum in switch ---
    switch Color.Blue {
        case Color.Red: {
            return 12
        }
        case Color.Green: {
            return 13
        }
        case Color.Blue: {
            // expected
        }
        default: {
            return 14
        }
    }

    return 0
}
