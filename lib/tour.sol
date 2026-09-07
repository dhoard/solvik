package tour

// Cross-package surface exercised by example.sol: pub struct / pub enum /
// pub trait with qualified construction, qualified and bare case patterns,
// and a cross-package trait constraint.  Kept in its own file because the
// legacy compiler (which still parses lib/format.sol for benchmark.sol)
// predates top-level pub type declarations.

pub struct Widget {
    pub id: Int

    pub func label() -> String {
        return "w" .. self.id
    }

    pub func measure() -> Int {
        return self.id * 2
    }
}

pub enum Grade {
    Low
    Mid
    High
}

pub enum Verdict<T> {
    Pass(T)
    Fail(String)
}

pub trait Measurable {
    func measure() -> Int
}

pub func gradeWord(g: Grade) -> String {
    switch g {
        case Grade.Low {
            return "low"
        }
        case Grade.Mid {
            return "mid"
        }
        case Grade.High {
            return "high"
        }
    }
}

pub func verdictText(v: Verdict<Int>) -> String {
    switch v {
        case Pass(n) {
            return "pass" .. n
        }
        case Fail(msg) {
            return "fail:" .. msg
        }
    }
}
