package cgeo.geocaching.location;

import cgeo.geocaching.utils.MatcherWrapper;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;

import java.util.regex.Pattern;

/**
 * Parse coordinates.
 */
class GeopointParser {

    private GeopointParser() {
        // utility class
    }

    private static class ResultWrapper {
        final double result;
        final int matcherPos;
        final int matcherLength;

        ResultWrapper(final double result, final int matcherPos, final int stringLength) {
            this.result = result;
            this.matcherPos = matcherPos;
            this.matcherLength = stringLength;
        }
    }

    //                                                            ( 1  )       ( 2  )         ( 3  )       ( 4  )       (        5        )
    private static final Pattern PATTERN_LAT = Pattern.compile("\\b([NS]|)\\s*(\\d+°?|°)(?:\\s*(\\d+)(?:[.,](\\d+)|'?\\s*(\\d+(?:[.,]\\d+)?)(?:''|\")?)?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_LON = Pattern.compile("\\b([WE]|)\\s*(\\d+°?|°)(?:\\s*(\\d+)(?:[.,](\\d+)|'?\\s*(\\d+(?:[.,]\\d+)?)(?:''|\")?)?)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_BAD_BLANK = Pattern.compile("(\\d)[,.] (\\d{2,})");

    enum LatLon {
        LAT,
        LON
    }

    /**
     * Parses a pair of coordinates (latitude and longitude) out of a String.
     * Accepts following formats and combinations of it:
     * X DD
     * X DD°
     * X DD° MM
     * X DD° MM.MMM
     * X DD° MM SS
     *
     * as well as:
     * DD.DDDDDDD
     *
     * Both . and , are accepted, also variable count of spaces (also 0)
     *
     * @param text
     *            the string to parse
     * @return an Geopoint with parsed latitude and longitude
     * @throws Geopoint.ParseException
     *             if lat or lon could not be parsed
     */
    public static Geopoint parse(@NonNull final String text) {
        // first try if these are simply 2 double values
        try {
            final String[] parts = StringUtils.split(StringUtils.trim(text));
            if (parts.length == 2) {
                // strip space, tab and &nbsp;
                final double lat = Double.parseDouble(StringUtils.strip(parts[0], " \t\u00a0"));
                final double lon = Double.parseDouble(StringUtils.strip(parts[1], " \t\u00a0"));
                return new Geopoint(lat, lon);
            }
        } catch (final NumberFormatException e) {
            // ignore and continue parsing textual formats
        }

        final ResultWrapper latitudeWrapper = parseHelper(text, LatLon.LAT);
        // cut away the latitude part when parsing the longitude
        final ResultWrapper longitudeWrapper = parseHelper(text.substring(latitudeWrapper.matcherPos + latitudeWrapper.matcherLength), LatLon.LON);

        if (longitudeWrapper.matcherPos - (latitudeWrapper.matcherPos + latitudeWrapper.matcherLength) >= 10) {
            throw new Geopoint.ParseException("Distance between latitude and longitude text is to large.", LatLon.LON);
        }

        final double lat = latitudeWrapper.result;
        final double lon = longitudeWrapper.result;
        if (!Geopoint.isValidLatitude(lat)) {
            throw new Geopoint.ParseException(text, LatLon.LAT);
        }
        if (!Geopoint.isValidLongitude(lon)) {
            throw new Geopoint.ParseException(text, LatLon.LON);
        }
        return new Geopoint(lat, lon);
    }

    /**
     * Helper for coordinates-parsing
     *
     * @param text the text to parse
     * @param latlon the kind of coordinate to parse
     * @return a wrapper with the result and the corresponding matching part
     * @throws Geopoint.ParseException if the text cannot be parsed
     */
    private static ResultWrapper parseHelper(@NonNull final String text, final LatLon latlon) {
        final String replaceSpaceAfterComma = new MatcherWrapper(PATTERN_BAD_BLANK, text).replaceAll("$1.$2");
        final MatcherWrapper matcher = new MatcherWrapper(latlon == LatLon.LAT ? PATTERN_LAT : PATTERN_LON, replaceSpaceAfterComma);

        try {
            return new ResultWrapper(Double.parseDouble(replaceSpaceAfterComma), 0, text.length());
        } catch (final NumberFormatException ignored) {
            // fall through to advanced parsing
        }

        try {
            if (matcher.find()) {
                final double sign = matcher.group(1).equalsIgnoreCase("S") || matcher.group(1).equalsIgnoreCase("W") ? -1.0 : 1.0;
                final double degree = Integer.valueOf(StringUtils.defaultIfEmpty(StringUtils.stripEnd(matcher.group(2), "°"), "0")).doubleValue();

                double minutes = 0.0;
                double seconds = 0.0;

                if (matcher.group(3) != null) {
                    minutes = Integer.valueOf(matcher.group(3)).doubleValue();

                    if (matcher.group(4) != null) {
                        seconds = Double.parseDouble("0." + matcher.group(4)) * 60.0;
                    } else if (matcher.group(5) != null) {
                        seconds = Double.parseDouble(matcher.group(5).replace(",", "."));
                    }
                }

                return new ResultWrapper(sign * (degree + minutes / 60.0 + seconds / 3600.0), matcher.start(), matcher.group().length());
            }
        } catch (final NumberFormatException ignored) {
            // We might have encountered too large a number. This was not the right way to do it, try another.
        }

        // Nothing found with "N 52...", try to match string as decimal degree parts (i.e. multiple doubles)
        try {
            final String[] items = StringUtils.split(StringUtils.trimToEmpty(text));
            final int length = ArrayUtils.getLength(items);
            if (length > 0 && length <= 2) {
                final int index = latlon == LatLon.LON ? length - 1 : 0;
                final String textPart = items[index];
                final int pos = latlon == LatLon.LON ? text.lastIndexOf(textPart) : text.indexOf(textPart);
                return new ResultWrapper(Double.parseDouble(textPart), pos, textPart.length());
            }
        } catch (final NumberFormatException ignored) {
            // The right exception will be raised below.
        }

        throw new Geopoint.ParseException("Could not parse coordinates as " + latlon + ": \"" + text + "\"", latlon);
    }

    /**
     * Parses latitude out of a given string.
     *
     * @see #parse(String)
     * @param text
     *            the string to be parsed
     * @return the latitude as decimal degree
     * @throws Geopoint.ParseException
     *             if latitude could not be parsed
     */
    public static double parseLatitude(final String text) {
        return parseHelper(text, LatLon.LAT).result;
    }

    /**
     * Parses longitude out of a given string.
     *
     * @see #parse(String)
     * @param text
     *            the string to be parsed
     * @return the longitude as decimal degree
     * @throws Geopoint.ParseException
     *             if longitude could not be parsed
     */
    public static double parseLongitude(final String text) {
        return parseHelper(text, LatLon.LON).result;
    }
}
