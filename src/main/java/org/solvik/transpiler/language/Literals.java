package org.solvik.transpiler.language;

import java.math.BigInteger;

/** Backend-neutral helpers for Solvik literal text. */
public final class Literals {
    private Literals() {}

    /**
     * Normalizes a Solvik integer literal (underscore separators, {@code 0x}/
     * {@code 0o}/{@code 0b} bases, and a leading sign) to plain decimal text.
     */
    public static String cleanInteger(String text) {
        String s = text.replace("_", "");
        boolean neg = s.startsWith("-");
        if (neg) s = s.substring(1);
        int radix = 10;
        if (s.startsWith("0x") || s.startsWith("0X")) { radix = 16; s = s.substring(2); }
        else if (s.startsWith("0o") || s.startsWith("0O")) { radix = 8; s = s.substring(2); }
        else if (s.startsWith("0b") || s.startsWith("0B")) { radix = 2; s = s.substring(2); }
        BigInteger value = new BigInteger(s, radix);
        return (neg ? "-" : "") + value;
    }
}
