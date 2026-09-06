// test/random_test.sol — built-in random module tests
//
// Tests: random.float, random.int, random.range, random.uniform,
//        random.choice, random.shuffle, random.sample, random.seed

package test

func main() -> Int {
    // === random.float ===

    f: Float = random.float()
    if f < 0.0 || f >= 1.0 {
        println("FAIL: random.float() out of range: " .. string(f))
    }

    // Multiple calls should generally differ (not a strict test, just sanity)
    g: Float = random.float()
    if f == g {
        // extremely unlikely — acceptable only if truly unlucky
    }

    // === random.int (inclusive bounds) ===

    mut i: Int = 0
    while i < 200 {
        r: Int = random.int(1, 6)
        if r < 1 || r > 6 {
            println("FAIL: random.int(1,6) out of range: " .. string(r))
        }
        i = i + 1
    }

    // Single-value range
    s: Int = random.int(5, 5)
    if s != 5 {
        println("FAIL: random.int(5,5) should be 5, got " .. string(s))
    }

    // === random.range (exclusive upper bound) ===

    i = 0
    while i < 200 {
        r: Int = random.range(0, 10)
        if r < 0 || r >= 10 {
            println("FAIL: random.range(0,10) out of range: " .. string(r))
        }
        i = i + 1
    }

    // === random.uniform ===

    u: Float = random.uniform(2.0, 3.0)
    if u < 2.0 || u > 3.0 {
        println("FAIL: random.uniform(2.0,3.0) out of range: " .. string(u))
    }

    // === random.choice ===

    names: List<String> = ["Alice", "Bob", "Charlie"]
    picked: String = random.choice(names)
    if picked != "Alice" && picked != "Bob" && picked != "Charlie" {
        println("FAIL: random.choice returned unexpected value: " .. picked)
    }

    // random.choice on empty list returns null
    empty: List<String> = []
    nothing: String? = random.choice(empty)
    if nothing != null {
        println("FAIL: random.choice on empty list should return null")
    }

    // random.choice on int list
    ints: List<Int> = [10, 20, 30]
    chosen: Int = random.choice(ints)
    if chosen != 10 && chosen != 20 && chosen != 30 {
        println("FAIL: random.choice on int list returned unexpected: " .. string(chosen))
    }

    // === random.shuffle ===

    nums: List<Int> = [1, 2, 3, 4, 5]
    shuffled: List<Int> = random.shuffle(nums)

    // Length must be preserved
    if shuffled.len() != 5 {
        println("FAIL: shuffle length mismatch: " .. string(shuffled.len()))
    }

    // Original list must be unchanged
    if nums[0] != 1 || nums[1] != 2 || nums[2] != 3 {
        println("FAIL: shuffle mutated original list")
    }

    // Shuffle empty list
    emptyShuffled: List<Int> = random.shuffle([])
    if emptyShuffled.len() != 0 {
        println("FAIL: shuffle of empty list should be empty")
    }

    // === random.sample ===

    pool: List<String> = ["A", "B", "C", "D", "E"]
    picks: List<String> = random.sample(pool, 3)
    if picks.len() != 3 {
        println("FAIL: sample size mismatch: expected 3, got " .. string(picks.len()))
    }

    // All picked elements must come from the original pool
    mut j: Int = 0
    while j < picks.len() {
        if picks[j] != "A" && picks[j] != "B" && picks[j] != "C" && picks[j] != "D" && picks[j] != "E" {
            println("FAIL: sample returned element not in pool: " .. picks[j])
        }
        j = j + 1
    }

    // Sample k=0 returns empty
    zeroSample: List<Int> = random.sample([1, 2, 3], 0)
    if zeroSample.len() != 0 {
        println("FAIL: sample(0) should be empty")
    }

    // Sample k > len returns all elements (shuffled)
    allSample: List<Int> = random.sample([1, 2, 3], 10)
    if allSample.len() != 3 {
        println("FAIL: sample(k > n) should return all elements, got " .. string(allSample.len()))
    }

    // === seed reproducibility ===

    random.seed(99)
    a1: Int = random.int(1, 1000)
    a2: Float = random.float()
    a3: Int = random.int(1, 6)

    random.seed(99)
    b1: Int = random.int(1, 1000)
    b2: Float = random.float()
    b3: Int = random.int(1, 6)

    if a1 != b1 {
        println("FAIL: seed reproducibility: int mismatch " .. string(a1) .. " vs " .. string(b1))
    }
    if a2 != b2 {
        println("FAIL: seed reproducibility: float mismatch " .. string(a2) .. " vs " .. string(b2))
    }
    if a3 != b3 {
        println("FAIL: seed reproducibility: int(1,6) mismatch " .. string(a3) .. " vs " .. string(b3))
    }

    // Seed with different values should produce different sequences
    random.seed(1)
    c1: Int = random.int(1, 1000000)
    random.seed(2)
    d1: Int = random.int(1, 1000000)
    if c1 == d1 {
        // Not impossible, but extremely unlikely with range 1..1000000
        println("WARN: seed(1) and seed(2) produced same first draw")
    }

    println("random tests passed")
    return 0
}
