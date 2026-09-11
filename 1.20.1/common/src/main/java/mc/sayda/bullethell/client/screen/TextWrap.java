package mc.sayda.bullethell.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;

/**
 * Greedy word-wrap to at most 2 lines, for any screen that centers a short label - a
 * card's title, a character's description, whatever - inside a fixed-width box.
 *
 * Lines beyond the second are never produced: the caller has already sized its layout
 * for exactly 2 lines, so a 3rd would either overflow or need a reflow this class knows
 * nothing about. Text that still doesn't fit on line 2 is truncated with an ellipsis
 * instead.
 */
@Environment(EnvType.CLIENT)
public final class TextWrap {

    private TextWrap() {}

    /** Wraps {@code text} onto at most 2 lines that fit within {@code maxWidth}. */
    public static String[] twoLines(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty())
            return new String[] { "" };
        int nl = text.indexOf('\n');
        if (nl >= 0) {
            String a = text.substring(0, nl).trim();
            String b = text.substring(nl + 1).trim();
            if (font.width(b) > maxWidth)
                b = truncate(font, b, maxWidth);
            return new String[] { a, b };
        }
        if (font.width(text) <= maxWidth)
            return new String[] { text };

        String[] words = text.trim().split("\\s+");
        if (words.length == 0)
            return new String[] { text };
        if (words.length == 1)
            return splitLongToken(font, words[0], maxWidth);

        StringBuilder line1 = new StringBuilder(words[0]);
        int i = 1;
        while (i < words.length) {
            String candidate = line1 + " " + words[i];
            if (font.width(candidate) <= maxWidth) {
                line1 = new StringBuilder(candidate);
                i++;
            } else
                break;
        }
        if (i >= words.length) {
            // Single token consumed all words (should not happen); fall back
            return splitLongToken(font, line1.toString(), maxWidth);
        }
        StringBuilder line2 = new StringBuilder(words[i++]);
        while (i < words.length)
            line2.append(' ').append(words[i++]);
        String second = line2.toString();
        if (font.width(second) > maxWidth)
            second = truncate(font, second, maxWidth);
        return new String[] { line1.toString(), second };
    }

    /** A single word wider than {@code maxWidth} on its own: split mid-word instead of overflowing. */
    private static String[] splitLongToken(Font font, String word, int maxWidth) {
        int bestCut = 1;
        for (int cut = 1; cut < word.length(); cut++) {
            if (font.width(word.substring(0, cut)) <= maxWidth
                    && font.width(word.substring(cut)) <= maxWidth)
                bestCut = cut;
        }
        String a = word.substring(0, bestCut);
        String b = word.substring(bestCut);
        if (font.width(b) > maxWidth)
            b = truncate(font, b, maxWidth);
        return new String[] { a, b };
    }

    /**
     * Truncates a single author-written line to fit {@code maxWidth}, ellipsis-style.
     * Unlike {@link #twoLines}, this never reflows text onto a second line - it exists
     * for text the author already lays out as separate lines, as a guard against one
     * of those lines running a little long, not as a substitute for wrapping.
     */
    public static String truncateOneLine(Font font, String text, int maxWidth) {
        return text == null ? "" : truncate(font, text, maxWidth);
    }

    private static String truncate(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth)
            return s;
        String ell = "...";
        int budget = maxWidth - font.width(ell);
        if (budget <= 0)
            return ell;
        String t = s;
        while (t.length() > 0 && font.width(t) > budget)
            t = t.substring(0, t.length() - 1);
        return t + ell;
    }
}
