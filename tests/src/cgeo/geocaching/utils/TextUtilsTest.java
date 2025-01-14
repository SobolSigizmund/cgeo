package cgeo.geocaching.utils;

import android.text.SpannableString;

import junit.framework.TestCase;

import java.util.regex.Pattern;

import cgeo.geocaching.connector.gc.GCConstants;
import cgeo.geocaching.test.mock.MockedCache;

import static org.assertj.core.api.Assertions.assertThat;

public class TextUtilsTest extends TestCase {
    public static void testRegEx() {
        final String page = MockedCache.readCachePage("GC2CJPF");
        assertThat(TextUtils.getMatch(page, GCConstants.PATTERN_LOGIN_NAME, true, "???")).isEqualTo("Bananeweizen");
    }

    public static void testReplaceWhitespaces() {
        assertThat(TextUtils.replaceWhitespace("  foo\n\tbar   \r   baz  ")).isEqualTo("foo bar baz ");
    }

    public static void testControlCharactersCleanup() {
        final Pattern patternAll = Pattern.compile("(.*)", Pattern.DOTALL);
        assertThat(TextUtils.getMatch("some" + '\u001C' + "control" + (char) 0x1D + "characters removed", patternAll, "")).isEqualTo("some control characters removed");
        assertThat(TextUtils.getMatch("newline\nalso\nremoved", patternAll, "")).isEqualTo("newline also removed");
    }

    public static void testGetMatch() {
        final Pattern patternAll = Pattern.compile("foo(...)");
        final String text = "abc-foobar-def-fooxyz-ghi-foobaz-jkl";
        assertThat(TextUtils.getMatch(text, patternAll, false, 1, null, false)).isEqualTo("bar");
        assertThat(TextUtils.getMatch(text, patternAll, false, 1, null, true)).isEqualTo("baz");
    }

    public static void testTrimSpanned() {
        assertTrimSpanned(" ","");
        assertTrimSpanned("\n", "");
        assertTrimSpanned("a ", "a");
        assertTrimSpanned("a\n", "a");
    }

    private static void assertTrimSpanned(String input, String expected) {
        assertThat(TextUtils.trimSpanned(new SpannableString(input)).toString()).isEqualTo(new SpannableString(expected).toString());
    }
}
