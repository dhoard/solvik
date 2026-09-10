module control

class Main {

    public static run(args: String...): Long {
        // while + break + continue
        let mutable i: Long = 0
        while true {
            i += 1
            if i % 2 == 0 {
                continue
            }
            if i > 7 {
                break
            }
            stdout.print(i .. " ")
        }
        stdout.println("")
        // range for-in
        let mutable total: Long = 0
        for n in 1..6 {
            total += n
        }
        stdout.println(total)
        // nested loops with labeled-free break/continue
        for a in 0..3 {
            for b in 0..3 {
                if b == 1 {
                    continue
                }
                if a == 2 && b == 2 {
                    break
                }
                stdout.print((a * 10 + b) .. " ")
            }
        }
        stdout.println("")
        return 0
    }
}
