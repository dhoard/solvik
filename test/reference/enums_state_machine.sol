package reference_enums_state_machine

// A practical sum-type use: a turnstile state machine with events.
enum State {
    Locked
    Unlocked
}

enum Event {
    Coin
    Push
    Reset
}

enum Transition {
    Stay(State)
    Move(State)
    Error(string)
}

func step(current: State, event: Event) -> Transition {
    switch current {
        case State.Locked {
            switch event {
                case Event.Coin {
                    return Transition.Move(State.Unlocked)
                }
                case Event.Push {
                    return Transition.Stay(State.Locked)
                }
                case Event.Reset {
                    return Transition.Error("cannot reset a locked turnstile")
                }
            }
        }
        case State.Unlocked {
            switch event {
                case Event.Coin {
                    return Transition.Stay(State.Unlocked)
                }
                case Event.Push {
                    return Transition.Move(State.Locked)
                }
                case Event.Reset {
                    return Transition.Stay(State.Unlocked)
                }
            }
        }
    }
}

func simulate() -> int {
    mut state: State = State.Locked
    mut coins: int = 0
    mut pushes: int = 0
    events: list<Event> = [Event.Coin, Event.Push, Event.Coin, Event.Push]
    for event in events {
        switch step(state, event) {
            case Transition.Move(next) {
                state = next
                switch event {
                    case Event.Coin {
                        coins = coins + 1
                    }
                    case Event.Push {
                        pushes = pushes + 1
                    }
                    case Event.Reset {
                        return 99
                    }
                }
            }
            case Transition.Stay(next) {
                state = next
            }
            case Transition.Error(msg) {
                return 98
            }
        }
    }
    if coins != 2 || pushes != 2 {
        return 1
    }
    if state != State.Locked {
        return 2
    }
    return 0
}

// Compiler-AST style recursive type.
enum Expr {
    IntLit(int)
    Add(Expr, Expr)
    Mul(Expr, Expr)
    Var(string)
}

func eval(e: Expr, scope: map<string, int>) -> int {
    switch e {
        case Expr.IntLit(v) {
            return v
        }
        case Expr.Var(name) {
            return scope[name]
        }
        case Expr.Add(a, b) {
            return eval(a, scope) + eval(b, scope)
        }
        case Expr.Mul(a, b) {
            return eval(a, scope) * eval(b, scope)
        }
    }
}

func main() -> int {
    if simulate() != 0 {
        return 1
    }
    scope: map<string, int> = { "x": 2, "y": 3 }
    expr: Expr = Expr.Add(Expr.Mul(Expr.Var("x"), Expr.IntLit(4)), Expr.Var("y"))
    if eval(expr, scope) != 11 {
        return 2
    }
    expr2: Expr = Expr.Add(Expr.IntLit(1), Expr.IntLit(1))
    if eval(expr2, scope) != 2 {
        return 3
    }
    return 0
}
