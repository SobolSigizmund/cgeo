package cgeo.geocaching.filter;

import cgeo.geocaching.R;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.LogEntry;
import cgeo.geocaching.utils.CalendarUtils;
import cgeo.geocaching.utils.TextUtils;

import org.eclipse.jdt.annotation.NonNull;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class StateFilterFactory implements IFilterFactory {

    @Override
    @NonNull
    public List<? extends IFilter> getFilters() {
        final List<AbstractFilter> filters = new ArrayList<>(6);
        filters.add(new StateFoundFilter());
        filters.add(new StateNotFoundFilter());
        filters.add(new StateNeverFoundFilter());
        filters.add(new StateFoundLastMonthFilter());
        filters.add(new StateArchivedFilter());
        filters.add(new StateDisabledFilter());
        filters.add(new StatePremiumFilter());
        filters.add(new StateNonPremiumFilter());
        filters.add(new StateStoredFilter());
        filters.add(new StateNotStoredFilter());
        filters.add(new RatingFilter());
        filters.add(new TrackablesFilter());
        filters.add(new MultiListingFilter());

        Collections.sort(filters, new Comparator<AbstractFilter>() {

            @Override
            public int compare(final AbstractFilter filter1, final AbstractFilter filter2) {
                return TextUtils.COLLATOR.compare(filter1.getName(), filter2.getName());
            }
        });

        return filters;
    }

    static class StateFoundFilter extends AbstractFilter {

        StateFoundFilter() {
            super(R.string.cache_status_found);
        }

        protected StateFoundFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return cache.isFound();
        }

        public static final Creator<StateFoundFilter> CREATOR
                = new Parcelable.Creator<StateFoundFilter>() {

            @Override
            public StateFoundFilter createFromParcel(final Parcel in) {
                return new StateFoundFilter(in);
            }

            @Override
            public StateFoundFilter[] newArray(final int size) {
                return new StateFoundFilter[size];
            }
        };
    }

    static class StateNotFoundFilter extends AbstractFilter {

        StateNotFoundFilter() {
            super(R.string.cache_not_status_found);
        }

        protected StateNotFoundFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return !cache.isFound();
        }

        public static final Creator<StateNotFoundFilter> CREATOR
                = new Parcelable.Creator<StateNotFoundFilter>() {

            @Override
            public StateNotFoundFilter createFromParcel(final Parcel in) {
                return new StateNotFoundFilter(in);
            }

            @Override
            public StateNotFoundFilter[] newArray(final int size) {
                return new StateNotFoundFilter[size];
            }
        };
    }

    static class StateNeverFoundFilter extends AbstractFilter {

        StateNeverFoundFilter() {
            super(R.string.cache_never_found);
        }

        protected StateNeverFoundFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            if (cache.getFindsCount() > 0) {
                return false;
            }
            // find counts don't exist for every connector, so we also check the logs
            for (final LogEntry log : cache.getLogs()) {
                if (log.getType().isFoundLog()) {
                    return false;
                }
            }
            return true;
        }

        public static final Creator<StateNeverFoundFilter> CREATOR = new Parcelable.Creator<StateNeverFoundFilter>() {

            @Override
            public StateNeverFoundFilter createFromParcel(final Parcel in) {
                return new StateNeverFoundFilter(in);
            }

            @Override
            public StateNeverFoundFilter[] newArray(final int size) {
                return new StateNeverFoundFilter[size];
            }
        };
    }

    static class StateFoundLastMonthFilter extends AbstractFilter {

        private static final double THIRTY_DAYS_MSECS = 30d * 86400d * 1000d;
        private final long today;

        StateFoundLastMonthFilter() {
            super(R.string.cache_found_last_30_days);
            today = Calendar.getInstance().getTimeInMillis();
        }

        protected StateFoundLastMonthFilter(final Parcel in) {
            super(in);
            today = Calendar.getInstance().getTimeInMillis();
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            for (final LogEntry log : cache.getLogs()) {
                if (log.getType().isFoundLog() && foundLastMonth(log)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Inline version of {@link CalendarUtils#daysSince(long) to avoid performance issues} with {@link Calendar}
         * instance creation. We totally neglect the time of day correction, since it is acceptable to have an error of
         * +/- 1 day with this 30 days filter.
         */
        private boolean foundLastMonth(final LogEntry log) {
            return today - log.date <= THIRTY_DAYS_MSECS;
        }

        public static final Creator<StateNeverFoundFilter> CREATOR = new Parcelable.Creator<StateNeverFoundFilter>() {

            @Override
            public StateNeverFoundFilter createFromParcel(final Parcel in) {
                return new StateNeverFoundFilter(in);
            }

            @Override
            public StateNeverFoundFilter[] newArray(final int size) {
                return new StateNeverFoundFilter[size];
            }
        };
    }

    static class StateArchivedFilter extends AbstractFilter {

        StateArchivedFilter() {
            super(R.string.cache_status_archived);
        }

        protected StateArchivedFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return cache.isArchived();
        }

        public static final Creator<StateArchivedFilter> CREATOR
                = new Parcelable.Creator<StateArchivedFilter>() {

            @Override
            public StateArchivedFilter createFromParcel(final Parcel in) {
                return new StateArchivedFilter(in);
            }

            @Override
            public StateArchivedFilter[] newArray(final int size) {
                return new StateArchivedFilter[size];
            }
        };
    }

    static class StateDisabledFilter extends AbstractFilter {

        StateDisabledFilter() {
            super(R.string.cache_status_disabled);
        }

        protected StateDisabledFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return cache.isDisabled();
        }

        public static final Creator<StateDisabledFilter> CREATOR
                = new Parcelable.Creator<StateDisabledFilter>() {

            @Override
            public StateDisabledFilter createFromParcel(final Parcel in) {
                return new StateDisabledFilter(in);
            }

            @Override
            public StateDisabledFilter[] newArray(final int size) {
                return new StateDisabledFilter[size];
            }
        };
    }

    static class StatePremiumFilter extends AbstractFilter {

        StatePremiumFilter() {
            super(R.string.cache_status_premium);
        }

        protected StatePremiumFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return cache.isPremiumMembersOnly();
        }

        public static final Creator<StatePremiumFilter> CREATOR
                = new Parcelable.Creator<StatePremiumFilter>() {

            @Override
            public StatePremiumFilter createFromParcel(final Parcel in) {
                return new StatePremiumFilter(in);
            }

            @Override
            public StatePremiumFilter[] newArray(final int size) {
                return new StatePremiumFilter[size];
            }
        };
    }

    static class StateNonPremiumFilter extends AbstractFilter {

        StateNonPremiumFilter() {
            super(R.string.cache_status_not_premium);
        }

        protected StateNonPremiumFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return !cache.isPremiumMembersOnly();
        }

        public static final Creator<StateNonPremiumFilter> CREATOR
                = new Parcelable.Creator<StateNonPremiumFilter>() {

            @Override
            public StateNonPremiumFilter createFromParcel(final Parcel in) {
                return new StateNonPremiumFilter(in);
            }

            @Override
            public StateNonPremiumFilter[] newArray(final int size) {
                return new StateNonPremiumFilter[size];
            }
        };
    }

    static class StateStoredFilter extends AbstractFilter {

        StateStoredFilter() {
            super(R.string.cache_status_stored);
        }

        protected StateStoredFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return cache.isOffline();
        }

        public static final Creator<StateStoredFilter> CREATOR
                = new Parcelable.Creator<StateStoredFilter>() {

            @Override
            public StateStoredFilter createFromParcel(final Parcel in) {
                return new StateStoredFilter(in);
            }

            @Override
            public StateStoredFilter[] newArray(final int size) {
                return new StateStoredFilter[size];
            }
        };
    }

    static class StateNotStoredFilter extends AbstractFilter {

        StateNotStoredFilter() {
            super(R.string.cache_status_not_stored);
        }

        protected StateNotStoredFilter(final Parcel in) {
            super(in);
        }

        @Override
        public boolean accepts(@NonNull final Geocache cache) {
            return !cache.isOffline();
        }

        public static final Creator<StateNotStoredFilter> CREATOR
                = new Parcelable.Creator<StateNotStoredFilter>() {

            @Override
            public StateNotStoredFilter createFromParcel(final Parcel in) {
                return new StateNotStoredFilter(in);
            }

            @Override
            public StateNotStoredFilter[] newArray(final int size) {
                return new StateNotStoredFilter[size];
            }
        };
    }

}
