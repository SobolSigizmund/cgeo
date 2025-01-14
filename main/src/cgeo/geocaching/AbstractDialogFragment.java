package cgeo.geocaching;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.gcvote.GCVote;
import cgeo.geocaching.gcvote.GCVoteRating;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Units;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.CacheDetailsCreator;
import cgeo.geocaching.ui.LoggingUI;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.PopupMenu;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.ButterKnife;
import rx.Subscription;
import rx.android.app.AppObservable;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.subscriptions.Subscriptions;

public abstract class AbstractDialogFragment extends DialogFragment implements CacheMenuHandler.ActivityInterface, PopupMenu.OnMenuItemClickListener, MenuItem.OnMenuItemClickListener {
    protected Resources res = null;
    protected String geocode;
    protected CacheDetailsCreator details;

    private Subscription resumeSubscription = Subscriptions.empty();
    private TextView cacheDistance = null;

    protected static final String GEOCODE_ARG= "GEOCODE";
    protected static final String WAYPOINT_ARG= "WAYPOINT";

    protected Geocache cache;

    public static final int RESULT_CODE_SET_TARGET = Activity.RESULT_FIRST_USER;
    public static final int REQUEST_CODE_TARGET_INFO = 1;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        res = getResources();
        setHasOptionsMenu(true);
    }

    protected void initCustomActionBar(final View v) {
        final ImageView defaultNavigationImageView = ButterKnife.findById(v, R.id.defaultNavigation);
        defaultNavigationImageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                startDefaultNavigation2();
                return true;
            }
        });
        defaultNavigationImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                navigateTo();
            }
        });

        final View setAsTargetView = v.findViewById(R.id.setAsTarget);
        final View setAsTargetSep = v.findViewById(R.id.setAsTargetSep);
        if (getActivity().getCallingActivity() != null) {
            setAsTargetView.setVisibility(View.VISIBLE);
            setAsTargetSep.setVisibility(View.VISIBLE);
            setAsTargetView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    setAsTarget();
                }
            });
        } else {
            setAsTargetView.setVisibility(View.GONE);
            setAsTargetSep.setVisibility(View.GONE);
        }

        final View overflowActionBar = v.findViewById(R.id.overflowActionBar);
        overflowActionBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                showPopup(v);
            }
        });
        /* Use a context menu instead popup where the popup menu is not working */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            registerForContextMenu(overflowActionBar);
        }

    }

    public final void setTitle(final CharSequence title) {
        final TextView titleview = ButterKnife.findById(getView(), R.id.actionbar_title);
        if (titleview != null) {
            titleview.setText(title);

        }
    }

    @Override
    public void onStart() {
        super.onStart();
        geocode = getArguments().getString(GEOCODE_ARG);
    }


    protected void showPopup(final View view) {
        // For reason I totally not understand the PopupMenu from Appcompat is broken beyond
        // repair. Chicken out here and show the old menu on Gingerbread.
        // The "correct" way of implementing this is stil in
        // showPopupCompat(view)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            view.showContextMenu();
        } else {
            showPopupHoneycomb(view);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void showPopupHoneycomb(final View view) {
        final android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(getActivity(), view);
        CacheMenuHandler.addMenuItems(new MenuInflater(getActivity()), popupMenu.getMenu(), cache);
        popupMenu.setOnMenuItemClickListener(
                new android.widget.PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(final MenuItem item) {
                       return AbstractDialogFragment.this.onMenuItemClick(item);
                    }
                }
        );
        popupMenu.show();
    }

    protected void showPopupCompat(final View view) {
        final PopupMenu popupMenu = new PopupMenu(getActivity(), view);

        // Directly instantiate SupportMenuInflater instead of getActivity().getMenuinflator
        // getMenuinflator will throw a NPE since it tries to get the not displayed ActionBar
        // menuinflator = getActivity().getMenuInflater();
        // MenuInflater menuinflator = new SupportMenuInflater(getActivity());
        CacheMenuHandler.addMenuItems(popupMenu.getMenuInflater(), popupMenu.getMenu(), cache);
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.show();
    }


    protected void init() {
        cache = DataStore.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);

        if (cache == null) {
            ((AbstractActivity) getActivity()).showToast(res.getString(R.string.err_detail_cache_find));

            getActivity().finish();
            return;
        }

        geocode = cache.getGeocode();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.resumeSubscription = geoUpdate.start(GeoDirHandler.UPDATE_GEODATA);
        init();
    }


    @Override
    public void onPause() {
        resumeSubscription.unsubscribe();
        super.onPause();
    }


    private void acquireGCVote() {
        if (!Settings.isRatingWanted()) {
            return;
        }
        if (!cache.supportsGCVote()) {
            return;
        }
        AppObservable.bindActivity(getActivity(), RxUtils.deferredNullable(new Func0<GCVoteRating>() {
            @Override
            public GCVoteRating call() {
                return GCVote.getRating(cache.getGuid(), geocode);
            }
        })).subscribeOn(AndroidRxUtils.networkScheduler).subscribe(new Action1<GCVoteRating>() {
            @Override
            public void call(final GCVoteRating rating) {
                cache.setRating(rating.getRating());
                cache.setVotes(rating.getVotes());
                DataStore.saveChangedCache(cache);
                details.addRating(cache);
            }
        });
    }

    protected final void addCacheDetails() {
        assert cache != null;
        // cache type
        final String cacheType = cache.getType().getL10n();
        final String cacheSize = cache.showSize() ? " (" + cache.getSize().getL10n() + ")" : "";
        details.add(R.string.cache_type, cacheType + cacheSize);

        details.add(R.string.cache_geocode, cache.getGeocode());
        details.addCacheState(cache);

        details.addDistance(cache, cacheDistance);
        cacheDistance = details.getValueView();

        details.addDifficulty(cache);
        details.addTerrain(cache);
        details.addEventDate(cache);

        // rating
        if (cache.getRating() > 0) {
            details.addRating(cache);
        } else {
            acquireGCVote();
        }

        // favorite count
        details.add(R.string.cache_favorite, cache.getFavoritePoints() + "×");

        // more details
        final Button buttonMore = ButterKnife.findById(getView(), R.id.more_details);

        buttonMore.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View arg0) {
                CacheDetailActivity.startActivity(getActivity(), geocode);
                getActivity().finish();
            }
        });

        /* Only working combination as it seems */
        registerForContextMenu(buttonMore);
    }

    public final void showToast(final String text) {
        ActivityMixin.showToast(getActivity(), text);
    }

    private final GeoDirHandler geoUpdate = new GeoDirHandler() {

        @Override
        public void updateGeoData(final GeoData geo) {
            try {
                if (cache != null && cache.getCoords() != null) {
                    cacheDistance.setText(Units.getDistanceFromKilometers(geo.getCoords().distanceTo(cache.getCoords())));
                    cacheDistance.bringToFront();
                }
                onUpdateGeoData(geo);
            } catch (final RuntimeException e) {
                Log.w("Failed to update location", e);
            }
        }
    };

    /**
     * @param geo
     *            location
     */
    protected void onUpdateGeoData(final GeoData geo) {
        // do nothing by default
    }

    /**
     * Set the current popup coordinates as new navigation target on map
     */
    private void setAsTarget() {
        final Activity activity = getActivity();
        final Intent result = new Intent();
        result.putExtra(Intents.EXTRA_TARGET_INFO, getTargetInfo());
        activity.setResult(RESULT_CODE_SET_TARGET, result);
        activity.finish();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        CacheMenuHandler.addMenuItems(inflater, menu, cache);

    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        CacheMenuHandler.addMenuItems(new MenuInflater(getActivity()), menu, cache);
        for (int i=0; i<menu.size(); i++) {
            final MenuItem m = menu.getItem(i);
            m.setOnMenuItemClickListener(this);
        }
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        return onOptionsItemSelected(item);
    }


    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
        return onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (CacheMenuHandler.onMenuItemSelected(item, this, cache)) {
            return true;
        }
        if (LoggingUI.onMenuItemSelected(item, getActivity(), cache)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);

        try {
            CacheMenuHandler.onPrepareOptionsMenu(menu, cache);
            LoggingUI.onPrepareOptionsMenu(menu, cache);
        } catch (final RuntimeException ignored) {
            // nothing
        }
    }


    protected abstract TargetInfo getTargetInfo();

    protected abstract void startDefaultNavigation2();

    @Override
    public void cachesAround() {
        final TargetInfo targetInfo = getTargetInfo();
        if (targetInfo == null || targetInfo.coords == null) {
            showToast(res.getString(R.string.err_location_unknown));
            return;
        }
        CacheListActivity.startActivityCoordinates((AbstractActivity) getActivity(), targetInfo.coords, cache != null ? cache.getName() : null);
        getActivity().finish();
    }

    @Override
    public void onCancel(final DialogInterface dialog) {
        super.onCancel(dialog);
        getActivity().finish();
    }

    public static class TargetInfo implements Parcelable {

        public final Geopoint coords;
        public final String geocode;

        TargetInfo(final Geopoint coords, final String geocode) {
            this.coords = coords;
            this.geocode = geocode;
        }

        public TargetInfo(final Parcel in) {
            this.coords = in.readParcelable(Geopoint.class.getClassLoader());
            this.geocode = in.readString();
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeParcelable(coords, PARCELABLE_WRITE_RETURN_VALUE);
            dest.writeString(geocode);
        }

        public static final Parcelable.Creator<TargetInfo> CREATOR = new Parcelable.Creator<TargetInfo>() {
            @Override
            public TargetInfo createFromParcel(final Parcel in) {
                return new TargetInfo(in);
            }

            @Override
            public TargetInfo[] newArray(final int size) {
                return new TargetInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
