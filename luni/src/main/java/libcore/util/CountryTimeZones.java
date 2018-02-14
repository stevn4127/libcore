/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package libcore.util;

import android.icu.util.TimeZone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Information about a country's time zones.
 */
public final class CountryTimeZones {

    /**
     * The result of lookup up a time zone using offset information (and possibly more).
     */
    public final static class OffsetResult {

        /** A zone that matches the supplied criteria. See also {@link #mOneMatch}. */
        public final TimeZone mTimeZone;

        /** True if there is one match for the supplied criteria */
        public final boolean mOneMatch;

        public OffsetResult(TimeZone timeZone, boolean oneMatch) {
            mTimeZone = java.util.Objects.requireNonNull(timeZone);
            mOneMatch = oneMatch;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "mTimeZone='" + mTimeZone + '\'' +
                    ", mOneMatch=" + mOneMatch +
                    '}';
        }
    }

    private final String countryIso;
    private final String defaultTimeZoneId;
    private final List<String> timeZoneIds;
    private final boolean everUsesUtc;

    // Memoized frozen ICU TimeZone object for the default.
    private TimeZone icuDefaultTimeZone;
    // Memoized frozen ICU TimeZone objects for the timeZoneIds.
    private List<TimeZone> icuTimeZones;

    private CountryTimeZones(String countryIso, String defaultTimeZoneId, boolean everUsesUtc,
            List<String> timeZoneIds) {
        this.countryIso = java.util.Objects.requireNonNull(countryIso);
        this.defaultTimeZoneId = defaultTimeZoneId;
        this.everUsesUtc = everUsesUtc;
        // Create a defensive copy of the IDs list.
        this.timeZoneIds = Collections.unmodifiableList(new ArrayList<>(timeZoneIds));
    }

    /**
     * Creates a {@link CountryTimeZones} object containing only known time zone IDs.
     */
    public static CountryTimeZones createValidated(String countryIso, String defaultTimeZoneId,
            boolean everUsesUtc, List<String> countryTimeZoneIds, String debugInfo) {

        // We rely on ZoneInfoDB to tell us what the known valid time zone IDs are. ICU may
        // recognize more but we want to be sure that zone IDs can be used with java.util as well as
        // android.icu and ICU is expected to have a superset.
        String[] validTimeZoneIdsArray = ZoneInfoDB.getInstance().getAvailableIDs();
        HashSet<String> validTimeZoneIdsSet = new HashSet<>(Arrays.asList(validTimeZoneIdsArray));
        List<String> validCountryTimeZoneIds = new ArrayList<>();
        for (String countryTimeZoneId : countryTimeZoneIds) {
            if (!validTimeZoneIdsSet.contains(countryTimeZoneId)) {
                System.logW("Skipping invalid zone: " + countryTimeZoneId + " at " + debugInfo);
            } else {
                validCountryTimeZoneIds.add(countryTimeZoneId);
            }
        }

        // We don't get too strict at runtime about whether the defaultTimeZoneId must be
        // one of the country's time zones because this is the data we have to use (we also
        // assume the data was validated by earlier steps). The default time zone ID must just
        // be a recognized zone ID: if it's not valid we leave it null.
        if (!validTimeZoneIdsSet.contains(defaultTimeZoneId)) {
            System.logW("Invalid default time zone ID: " + defaultTimeZoneId
                    + " at " + debugInfo);
            defaultTimeZoneId = null;
        }

        String normalizedCountryIso = normalizeCountryIso(countryIso);
        return new CountryTimeZones(
                normalizedCountryIso, defaultTimeZoneId, everUsesUtc, validCountryTimeZoneIds);
    }

    /**
     * Returns the ISO code for the country.
     */
    public String getCountryIso() {
        return countryIso;
    }

    /**
     * Returns true if the ISO code for the country is a match for the one specified.
     */
    public boolean isForCountryCode(String countryIso) {
        return this.countryIso.equals(normalizeCountryIso(countryIso));
    }

    /**
     * Returns the default time zone ID for the country. Can return null in cases when no data is
     * available or the time zone ID provided to
     * {@link #createValidated(String, String, boolean, List, String)} was not recognized.
     */
    public synchronized TimeZone getDefaultTimeZone() {
        if (icuDefaultTimeZone == null) {
            TimeZone defaultTimeZone;
            if (defaultTimeZoneId == null) {
                defaultTimeZone = null;
            } else {
                defaultTimeZone = getValidFrozenTimeZoneOrNull(defaultTimeZoneId);
            }
            icuDefaultTimeZone = defaultTimeZone;
        }
        return icuDefaultTimeZone;
    }

    /**
     * Returns the default time zone ID for the country. Can return null in cases when no data is
     * available or the time zone ID provided to
     * {@link #createValidated(String, String, boolean, List, String)} was not recognized.
     */
    public String getDefaultTimeZoneId() {
        return defaultTimeZoneId;
    }

    /**
     * Returns an ordered list of time zone IDs for the country in an undefined but "priority"
     * order for a country. The list can be empty if there were no zones configured or the
     * configured zone IDs were not recognized.
     */
    public List<String> getTimeZoneIds() {
        return timeZoneIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CountryTimeZones that = (CountryTimeZones) o;

        if (everUsesUtc != that.everUsesUtc) {
            return false;
        }
        if (!countryIso.equals(that.countryIso)) {
            return false;
        }
        if (defaultTimeZoneId != null ? !defaultTimeZoneId.equals(that.defaultTimeZoneId)
                : that.defaultTimeZoneId != null) {
            return false;
        }
        return timeZoneIds.equals(that.timeZoneIds);
    }

    @Override
    public int hashCode() {
        int result = countryIso.hashCode();
        result = 31 * result + (defaultTimeZoneId != null ? defaultTimeZoneId.hashCode() : 0);
        result = 31 * result + timeZoneIds.hashCode();
        result = 31 * result + (everUsesUtc ? 1 : 0);
        return result;
    }

    /**
     * Returns an ordered list of time zones for the country in an undefined but "priority"
     * order for a country. The list can be empty if there were no zones configured or the
     * configured zone IDs were not recognized.
     */
    public synchronized List<TimeZone> getIcuTimeZones() {
        if (icuTimeZones == null) {
            ArrayList<TimeZone> mutableList = new ArrayList<>(timeZoneIds.size());
            for (String timeZoneId : timeZoneIds) {
                TimeZone timeZone;
                if (timeZoneId.equals(defaultTimeZoneId)) {
                    timeZone = getDefaultTimeZone();
                } else {
                    timeZone = getValidFrozenTimeZoneOrNull(timeZoneId);
                }
                // This shouldn't happen given the validation that takes place in
                // createValidatedCountryTimeZones().
                if (timeZone == null) {
                    System.logW("Skipping invalid zone: " + timeZoneId);
                    continue;
                }
                mutableList.add(timeZone);
            }
            icuTimeZones = Collections.unmodifiableList(mutableList);
        }
        return icuTimeZones;
    }

    /**
     * Returns true if the country has at least one zone that is the same as UTC at the given time.
     */
    public boolean hasUtcZone(long whenMillis) {
        // If the data tells us the country never uses UTC we don't have to check anything.
        if (!everUsesUtc) {
            return false;
        }

        for (TimeZone zone : getIcuTimeZones()) {
            if (zone.getOffset(whenMillis) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the default time zone for the country is either the only zone used or
     * if it has the same offsets as all other zones used by the country <em>at the specified time
     * </em> making the default equivalent to all other zones used by the country <em>at that time
     * </em>.
     */
    public boolean isDefaultOkForCountryTimeZoneDetection(long whenMillis) {
        if (timeZoneIds.isEmpty()) {
            // Should never happen unless there's been an error loading the data.
            return false;
        } else if (timeZoneIds.size() == 1) {
            // The default is the only zone so it's a good candidate.
            return true;
        } else {
            TimeZone countryDefault = getDefaultTimeZone();
            if (countryDefault == null) {
                return false;
            }

            int countryDefaultOffset = countryDefault.getOffset(whenMillis);
            List<TimeZone> candidates = getIcuTimeZones();
            for (TimeZone candidate : candidates) {
                if (candidate == countryDefault) {
                    continue;
                }

                int candidateOffset = candidate.getOffset(whenMillis);
                if (countryDefaultOffset != candidateOffset) {
                    // Multiple different offsets means the default should not be used.
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Returns a time zone for the country, if there is one, that has the desired properties. If
     * there are multiple matches and the {@code bias} is one of them then it is returned, otherwise
     * an arbitrary match is returned based on the {@link #getTimeZoneIds()} ordering.
     *
     * @param offsetMillis the offset from UTC at {@code whenMillis}
     * @param isDst whether the zone is in DST
     * @param whenMillis the UTC time to match against
     * @param bias the time zone to prefer, can be null
     * @deprecated Use {@link #lookupByOffsetWithBias(int, Integer, long, TimeZone)} instead
     */
    @Deprecated
    public OffsetResult lookupByOffsetWithBias(int offsetMillis, boolean isDst, long whenMillis,
            TimeZone bias) {
        if (timeZoneIds == null || timeZoneIds.isEmpty()) {
            return null;
        }

        List<TimeZone> candidates = getIcuTimeZones();

        TimeZone firstMatch = null;
        boolean biasMatched = false;
        boolean oneMatch = true;
        for (TimeZone match : candidates) {
            if (!offsetMatchesAtTime(match, offsetMillis, isDst, whenMillis)) {
                continue;
            }

            if (firstMatch == null) {
                firstMatch = match;
            } else {
                oneMatch = false;
            }
            if (bias != null && match.getID().equals(bias.getID())) {
                biasMatched = true;
            }
            if (firstMatch != null && !oneMatch && (bias == null || biasMatched)) {
                break;
            }
        }
        if (firstMatch == null) {
            return null;
        }

        TimeZone toReturn = biasMatched ? bias : firstMatch;
        return new OffsetResult(toReturn, oneMatch);
    }

    /**
     * Returns {@code true} if the specified offset, DST state and time would be valid in the
     * timeZone.
     */
    private static boolean offsetMatchesAtTime(TimeZone timeZone, int offsetMillis, boolean isDst,
            long whenMillis) {
        int[] offsets = new int[2];
        timeZone.getOffset(whenMillis, false /* local */, offsets);

        // offsets[1] == 0 when the zone is not in DST.
        boolean zoneIsDst = offsets[1] != 0;
        if (isDst != zoneIsDst) {
            return false;
        }
        return offsetMillis == (offsets[0] + offsets[1]);
    }

    /**
     * Returns a time zone for the country, if there is one, that has the desired properties. If
     * there are multiple matches and the {@code bias} is one of them then it is returned, otherwise
     * an arbitrary match is returned based on the {@link #getTimeZoneIds()} ordering.
     *
     * @param offsetMillis the offset from UTC at {@code whenMillis}
     * @param dstOffsetMillis the part of {@code offsetMillis} contributed by DST, {@code null}
     *                        means unknown
     * @param whenMillis the UTC time to match against
     * @param bias the time zone to prefer, can be null
     */
    public OffsetResult lookupByOffsetWithBias(int offsetMillis, Integer dstOffsetMillis,
            long whenMillis, TimeZone bias) {
        if (timeZoneIds == null || timeZoneIds.isEmpty()) {
            return null;
        }

        List<TimeZone> candidates = getIcuTimeZones();

        TimeZone firstMatch = null;
        boolean biasMatched = false;
        boolean oneMatch = true;
        for (TimeZone match : candidates) {
            if (!offsetMatchesAtTime(match, offsetMillis, dstOffsetMillis, whenMillis)) {
                continue;
            }

            if (firstMatch == null) {
                firstMatch = match;
            } else {
                oneMatch = false;
            }
            if (bias != null && match.getID().equals(bias.getID())) {
                biasMatched = true;
            }
            if (firstMatch != null && !oneMatch && (bias == null || biasMatched)) {
                break;
            }
        }
        if (firstMatch == null) {
            return null;
        }

        TimeZone toReturn = biasMatched ? bias : firstMatch;
        return new OffsetResult(toReturn, oneMatch);
    }

    /**
     * Returns {@code true} if the specified offset, DST and time would be valid in the
     * timeZone.
     */
    private static boolean offsetMatchesAtTime(TimeZone timeZone, int offsetMillis,
            Integer dstOffsetMillis, long whenMillis) {
        int[] offsets = new int[2];
        timeZone.getOffset(whenMillis, false /* local */, offsets);

        if (dstOffsetMillis != null) {
            if (dstOffsetMillis.intValue() != offsets[1]) {
                return false;
            }
        }
        return offsetMillis == (offsets[0] + offsets[1]);
    }

    private static TimeZone getValidFrozenTimeZoneOrNull(String timeZoneId) {
        TimeZone timeZone = TimeZone.getFrozenTimeZone(timeZoneId);
        if (timeZone.getID().equals(TimeZone.UNKNOWN_ZONE_ID)) {
            return null;
        }
        return timeZone;
    }

    private static String normalizeCountryIso(String countryIso) {
        // Lowercase ASCII is normalized for the purposes of the code in this class.
        return countryIso.toLowerCase(Locale.US);
    }
}
