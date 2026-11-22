package th.park.targetinfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Number and height rules, kept free of Bukkit so they are unit-testable. */
public final class Fmt {
    private Fmt() {}

    /** Health with at most one decimal; anything alive shows at least 0.1 so it never reads as dead. */
    public static String hp(double v) {
        if (v <= 0) return "0";
        double shown = Math.max(0.1, v);
        return BigDecimal.valueOf(shown).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Height above the entity's feet for the text: just over the head, or over its name tag if it has one. */
    public static double lift(double height, boolean nameTag) {
        return height + (nameTag ? 0.6 : 0.3);
    }
}
