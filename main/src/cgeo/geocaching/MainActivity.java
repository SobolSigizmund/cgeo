package cgeo.geocaching;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.location.Address;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.support.v7.widget.SearchView.OnSuggestionListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.amlcurran.showcaseview.targets.ActionViewTarget;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import cgeo.geocaching.activity.AbstractActionBarActivity;
import cgeo.geocaching.activity.ShowcaseViewBuilder;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.capability.ILogin;
import cgeo.geocaching.connector.gc.PocketQueryListActivity;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.list.PseudoList;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.location.AndroidGeocoder;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Units;
import cgeo.geocaching.maps.DefaultMap;
import cgeo.geocaching.playservices.AppInvite;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.sensors.GpsStatusProvider;
import cgeo.geocaching.sensors.GpsStatusProvider.Status;
import cgeo.geocaching.sensors.Sensors;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.settings.SettingsActivity;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.dialog.Dialogs;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.DatabaseBackupUtils;
import cgeo.geocaching.utils.Formatter;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.TextUtils;
import cgeo.geocaching.utils.Version;
import rx.Observable;
import rx.android.app.AppObservable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

public class MainActivity extends AbstractActionBarActivity {
    @BindView(R.id.nav_satellites) protected TextView navSatellites;
    @BindView(R.id.filter_button_title) protected TextView filterTitle;
    @BindView(R.id.map) protected ImageView findOnMap;
    @BindView(R.id.search_offline) protected ImageView findByOffline;
    @BindView(R.id.advanced_button) protected ImageView advanced;
    @BindView(R.id.any_button) protected ImageView any;
    @BindView(R.id.filter_button) protected ImageView filter;
    @BindView(R.id.nearest) protected ImageView nearestView;
    @BindView(R.id.nav_type) protected TextView navType;
    @BindView(R.id.nav_accuracy) protected TextView navAccuracy;
    @BindView(R.id.nav_location) protected TextView navLocation;
    @BindView(R.id.offline_count) protected TextView countBubble;
    @BindView(R.id.info_area) protected LinearLayout infoArea;

    /**
     * view of the action bar search
     */
    private SearchView searchView;
    private MenuItem searchItem;
    private Geopoint addCoords = null;
    private boolean initialized = false;
    private ConnectivityChangeReceiver connectivityChangeReceiver;

    private final UpdateLocation locationUpdater = new UpdateLocation();

    private final Handler updateUserInfoHandler = new Handler() {

        @Override
        public void handleMessage(final Message msg) {
            updateAccountInfo();
        }

        private void updateAccountInfo() {
            // Get active connectors with login status
            final ILogin[] loginConns = ConnectorFactory.getActiveLiveConnectors();

            // Update UI
            infoArea.removeAllViews();
            final LayoutInflater inflater = getLayoutInflater();

            for (final ILogin conn : loginConns) {

                final TextView connectorInfo = (TextView) inflater.inflate(R.layout.main_activity_connectorstatus, infoArea, false);
                infoArea.addView(connectorInfo);

                final StringBuilder userInfo = new StringBuilder(conn.getName()).append(Formatter.SEPARATOR);
                if (conn.isLoggedIn()) {
                    userInfo.append(conn.getUserName());
                    if (conn.getCachesFound() >= 0) {
                        userInfo.append(" (").append(conn.getCachesFound()).append(')');
                    }
                    userInfo.append(Formatter.SEPARATOR);
                }
                userInfo.append(conn.getLoginStatusString());

                connectorInfo.setText(userInfo);
                connectorInfo.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(final View v) {
                        SettingsActivity.openForScreen(R.string.preference_screen_services, MainActivity.this);
                    }
                });
            }
        }
    };

    private final class ConnectivityChangeReceiver extends BroadcastReceiver {
        private boolean isConnected = app.isNetworkConnected();

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final boolean wasConnected = isConnected;
            isConnected = app.isNetworkConnected();
            if (isConnected && !wasConnected) {
                startBackgroundLogin();
            }
        }
    }

    private static String formatAddress(final Address address) {
        final List<String> addressParts = new ArrayList<>();

        final String countryName = address.getCountryName();
        if (countryName != null) {
            addressParts.add(countryName);
        }
        final String locality = address.getLocality();
        if (locality != null) {
            addressParts.add(locality);
        } else {
            final String adminArea = address.getAdminArea();
            if (adminArea != null) {
                addressParts.add(adminArea);
            }
        }
        return StringUtils.join(addressParts, ", ");
    }

    private final Action1<GpsStatusProvider.Status> satellitesHandler = new Action1<Status>() {
        @Override
        public void call(final Status gpsStatus) {
            if (gpsStatus.gpsEnabled) {
                navSatellites.setText(res.getString(R.string.loc_sat) + ": " + gpsStatus.satellitesFixed + '/' + gpsStatus.satellitesVisible);
            } else {
                navSatellites.setText(res.getString(R.string.loc_gps_disabled));
            }
        }
    };

    private final Handler firstLoginHandler = new Handler() {

        @Override
        public void handleMessage(final Message msg) {
            try {
                final StatusCode reason = (StatusCode) msg.obj;

                if (reason != null && reason != StatusCode.NO_ERROR) { //LoginFailed
                    showToast(res.getString(reason == StatusCode.MAINTENANCE ? reason.getErrorString() : R.string.err_login_failed_toast));
                }
            } catch (final Exception e) {
                Log.w("MainActivity.firstLoginHander", e);
            }
        }
    };

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        // don't call the super implementation with the layout argument, as that would set the wrong theme
        super.onCreate(savedInstanceState);

        // Disable the up navigation for this activity
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);

        setContentView(R.layout.main_activity);
        ButterKnife.bind(this);

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            // If we had been open already, start from the last used activity.
            finish();
            return;
        }

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL); // type to search

        Log.i("Starting " + getPackageName() + ' ' + Version.getVersionCode(this) + " a.k.a " + Version.getVersionName(this));

        init();

        checkShowChangelog();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        init();
    }

    @Override
    public void onResume() {
        super.onResume(locationUpdater.start(GeoDirHandler.UPDATE_GEODATA | GeoDirHandler.LOW_POWER),
                Sensors.getInstance().gpsStatusObservable().observeOn(AndroidSchedulers.mainThread()).subscribe(satellitesHandler));
        updateUserInfoHandler.sendEmptyMessage(-1);
        startBackgroundLogin();
        init();

        connectivityChangeReceiver = new ConnectivityChangeReceiver();
        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void startBackgroundLogin() {
        assert app != null;

        final boolean mustLogin = app.mustRelog();

        for (final ILogin conn : ConnectorFactory.getActiveLiveConnectors()) {
            if (mustLogin || !conn.isLoggedIn()) {
                AndroidRxUtils.networkScheduler.createWorker().schedule(new Action0() {
                    @Override
                    public void call() {
                        if (mustLogin) {
                            // Properly log out from geocaching.com
                            conn.logout();
                        }
                        conn.login(firstLoginHandler, MainActivity.this);
                        updateUserInfoHandler.sendEmptyMessage(-1);
                    }
                });
            }
        }
    }

    @Override
    public void onDestroy() {
        initialized = false;
        app.showLoginToast = true;

        super.onDestroy();
    }

    @Override
    public void onStop() {
        initialized = false;
        super.onStop();
    }

    @Override
    public void onPause() {
        initialized = false;
        unregisterReceiver(connectivityChangeReceiver);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_options, menu);
        final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchItem = menu.findItem(R.id.menu_gosearch);
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        hideKeyboardOnSearchClick(searchItem);
        presentShowcase();
        return true;
    }

    private void hideKeyboardOnSearchClick(final MenuItem searchItem) {
        searchView.setOnSuggestionListener(new OnSuggestionListener() {

            @Override
            public boolean onSuggestionSelect(final int arg0) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(final int arg0) {
                MenuItemCompat.collapseActionView(searchItem);
                searchView.setIconified(true);
                // return false to invoke standard behavior of launching the intent for the search result
                return false;
            }
        });

        // Used to collapse searchBar on submit from virtual keyboard
        searchView.setOnQueryTextListener(new OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String s) {
                MenuItemCompat.collapseActionView(searchItem);
                searchView.setIconified(true);
                return false;
            }

            @Override
            public boolean onQueryTextChange(final String s) {
                return false;
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_pocket_queries).setVisible(Settings.isGCConnectorActive() && Settings.isGCPremiumMember());
        menu.findItem(R.id.menu_app_invite).setVisible(AppInvite.isAvailable());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                // this activity must handle the home navigation different than all others
                showAbout(null);
                return true;
            case R.id.menu_about:
                showAbout(null);
                return true;
            case R.id.menu_helpers:
                startActivity(new Intent(this, UsefulAppsActivity.class));
                return true;
            case R.id.menu_settings:
                startActivityForResult(new Intent(this, SettingsActivity.class), Intents.SETTINGS_ACTIVITY_REQUEST_CODE);
                return true;
            case R.id.menu_history:
                startActivity(CacheListActivity.getHistoryIntent(this));
                return true;
            case R.id.menu_scan:
                startScannerApplication();
                return true;
            case R.id.menu_pocket_queries:
                if (!Settings.isGCPremiumMember()) {
                    return true;
                }
                startActivity(new Intent(this, PocketQueryListActivity.class));
                return true;
            case R.id.menu_app_invite:
                AppInvite.send(this, getString(R.string.invitation_message));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startScannerApplication() {
        final IntentIntegrator integrator = new IntentIntegrator(this);
        // integrator dialog is English only, therefore localize it
        integrator.setButtonYesByID(android.R.string.yes);
        integrator.setButtonNoByID(android.R.string.no);
        integrator.setTitleByID(R.string.menu_scan_geo);
        integrator.setMessageByID(R.string.menu_scan_description);
        integrator.initiateScan(IntentIntegrator.QR_CODE_TYPES);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == Intents.SETTINGS_ACTIVITY_REQUEST_CODE) {
            if (resultCode == SettingsActivity.RESTART_NEEDED) {
                CgeoApplication.getInstance().restartApplication();
            }
        } else {
            final IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult != null) {
                final String scan = scanResult.getContents();
                if (StringUtils.isBlank(scan)) {
                    return;
                }
                SearchActivity.startActivityScan(scan, this);
            } else if (requestCode == Intents.SEARCH_REQUEST_CODE) {
                // SearchActivity activity returned without making a search
                if (resultCode == RESULT_CANCELED) {
                    String query = intent.getStringExtra(SearchManager.QUERY);
                    if (query == null) {
                        query = "";
                    }
                    Dialogs.message(this, res.getString(R.string.unknown_scan) + "\n\n" + query);
                }
            }
        }
    }

    private void setFilterTitle() {
        filterTitle.setText(Settings.getCacheType().getL10n());
    }

    private void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        findOnMap.setClickable(true);
        findOnMap.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                cgeoFindOnMap(v);
            }
        });

        findByOffline.setClickable(true);
        findByOffline.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                cgeoFindByOffline(v);
            }
        });
        findByOffline.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(final View v) {
                new StoredList.UserInterface(MainActivity.this).promptForListSelection(R.string.list_title, new Action1<Integer>() {

                    @Override
                    public void call(final Integer selectedListId) {
                        Settings.saveLastList(selectedListId);
                        CacheListActivity.startActivityOffline(MainActivity.this);
                    }
                }, false, PseudoList.HISTORY_LIST.id);
                return true;
            }
        });
        findByOffline.setLongClickable(true);

        advanced.setClickable(true);
        advanced.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                cgeoSearch(v);
            }
        });

        any.setClickable(true);
        any.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                cgeoPoint(v);
            }
        });

        filter.setClickable(true);
        filter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                selectGlobalTypeFilter();
            }
        });
        filter.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(final View v) {
                Settings.setCacheType(CacheType.ALL);
                setFilterTitle();
                return true;
            }
        });

        updateCacheCounter();

        setFilterTitle();
        checkRestore();
        DataStore.cleanIfNeeded(this);
    }

    protected void selectGlobalTypeFilter() {
        final List<CacheType> cacheTypes = new ArrayList<>();

        //first add the most used types
        cacheTypes.add(CacheType.ALL);
        cacheTypes.add(CacheType.TRADITIONAL);
        cacheTypes.add(CacheType.MULTI);
        cacheTypes.add(CacheType.MYSTERY);

        // then add all other cache types sorted alphabetically
        final List<CacheType> sorted = new ArrayList<>(Arrays.asList(CacheType.values()));
        sorted.removeAll(cacheTypes);

        Collections.sort(sorted, new Comparator<CacheType>() {
            @Override
            public int compare(final CacheType left, final CacheType right) {
                return TextUtils.COLLATOR.compare(left.getL10n(), right.getL10n());
            }
        });

        cacheTypes.addAll(sorted);

        int checkedItem = cacheTypes.indexOf(Settings.getCacheType());
        if (checkedItem < 0) {
            checkedItem = 0;
        }

        final String[] items = new String[cacheTypes.size()];
        for (int i = 0; i < cacheTypes.size(); i++) {
            items[i] = cacheTypes.get(i).getL10n();
        }

        final Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_filter);
        builder.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int position) {
                final CacheType cacheType = cacheTypes.get(position);
                Settings.setCacheType(cacheType);
                setFilterTitle();
                dialog.dismiss();
            }

        });
        builder.create().show();
    }

    public void updateCacheCounter() {
        AppObservable.bindActivity(this, DataStore.getAllCachesCountObservable()).subscribe(new Action1<Integer>() {
            @Override
            public void call(final Integer countBubbleCnt1) {
                if (countBubbleCnt1 == 0) {
                    countBubble.setVisibility(View.GONE);
                } else {
                    countBubble.setText(Integer.toString(countBubbleCnt1));
                    countBubble.bringToFront();
                    countBubble.setVisibility(View.VISIBLE);
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(final Throwable throwable) {
                Log.e("Unable to add bubble count", throwable);
            }
        });
    }

    private void checkRestore() {
        if (!DataStore.isNewlyCreatedDatebase() || DatabaseBackupUtils.getRestoreFile() == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(res.getString(R.string.init_backup_restore))
                .setMessage(res.getString(R.string.init_restore_confirm))
                .setCancelable(false)
                .setPositiveButton(getString(android.R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.dismiss();
                        DataStore.resetNewlyCreatedDatabase();
                        DatabaseBackupUtils.restoreDatabase(MainActivity.this);
                    }
                })
                .setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                        DataStore.resetNewlyCreatedDatabase();
                    }
                })
                .create()
                .show();
    }

    private class UpdateLocation extends GeoDirHandler {

        @Override
        public void updateGeoData(final GeoData geo) {
            if (!nearestView.isClickable()) {
                nearestView.setFocusable(true);
                nearestView.setClickable(true);
                nearestView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        cgeoFindNearest(v);
                    }
                });
                nearestView.setBackgroundResource(R.drawable.main_nearby);
            }

            navType.setText(res.getString(geo.getLocationProvider().resourceId));

            if (geo.getAccuracy() >= 0) {
                final int speed = Math.round(geo.getSpeed()) * 60 * 60 / 1000;
                navAccuracy.setText("±" + Units.getDistanceFromMeters(geo.getAccuracy()) + Formatter.SEPARATOR + Units.getSpeed(speed));
            } else {
                navAccuracy.setText(null);
            }

            final Geopoint currentCoords = geo.getCoords();
            if (Settings.isShowAddress()) {
                if (addCoords == null) {
                    navLocation.setText(R.string.loc_no_addr);
                }
                if (addCoords == null || (currentCoords.distanceTo(addCoords) > 0.5)) {
                    addCoords = currentCoords;
                    final Observable<String> address = (new AndroidGeocoder(MainActivity.this).getFromLocation(currentCoords)).map(new Func1<Address, String>() {
                        @Override
                        public String call(final Address address) {
                            return formatAddress(address);
                        }
                    }).onErrorResumeNext(Observable.just(currentCoords.toString()));
                    AppObservable.bindActivity(MainActivity.this, address)
                            .subscribeOn(AndroidRxUtils.networkScheduler)
                            .subscribe(new Action1<String>() {
                                @Override
                                public void call(final String address) {
                                    navLocation.setText(address);
                                }
                            });
                }
            } else {
                navLocation.setText(currentCoords.toString());
            }
        }
    }

    /**
     * @param v
     *            unused here but needed since this method is referenced from XML layout
     */
    public void cgeoFindOnMap(final View v) {
        findOnMap.setPressed(true);
        startActivity(DefaultMap.getLiveMapIntent(this));
    }

    /**
     * @param v
     *            unused here but needed since this method is referenced from XML layout
     */
    public void cgeoFindNearest(final View v) {
        nearestView.setPressed(true);
        startActivity(CacheListActivity.getNearestIntent(this));
    }

    /**
     * @param v
     *            unused here but needed since this method is referenced from XML layout
     */
    public void cgeoFindByOffline(final View v) {
        findByOffline.setPressed(true);
        CacheListActivity.startActivityOffline(this);
    }

    /**
     * @param v
     *            unused here but needed since this method is referenced from XML layout
     */
    public void cgeoSearch(final View v) {
        advanced.setPressed(true);
        startActivity(new Intent(this, SearchActivity.class));
    }

    /**
     * @param v
     *            unused here but needed since this method is referenced from XML layout
     */
    public void cgeoPoint(final View v) {
        any.setPressed(true);
        startActivity(new Intent(this, NavigateAnyPointActivity.class));
    }

    /**
     * @param v
     *            unused here but needed since this method is referenced from XML layout
     */
    public void cgeoFilter(final View v) {
        filter.setPressed(true);
        filter.performClick();
    }

    /**
     * @param v
     *            unused here but needed since this method is referenced from XML layout
     */
    public void cgeoNavSettings(final View v) {
        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    private void checkShowChangelog() {
        // temporary workaround for #4143
        //TODO: understand and avoid if possible
        try {
            final long lastChecksum = Settings.getLastChangelogChecksum();
            final long checksum = TextUtils.checksum(getString(R.string.changelog_master) + getString(R.string.changelog_release));
            Settings.setLastChangelogChecksum(checksum);
            // don't show change log after new install...
            if (lastChecksum > 0 && lastChecksum != checksum) {
                AboutActivity.showChangeLog(this);
            }
        } catch (final Exception ex) {
            Log.e("Error checking/showing changelog!", ex);
        }
    }

    /**
     * @param view
     *            unused here but needed since this method is referenced from XML layout
     */
    public void showAbout(final View view) {
        startActivity(new Intent(this, AboutActivity.class));
    }

    @Override
    public ShowcaseViewBuilder getShowcase() {
        return new ShowcaseViewBuilder(this)
                .setTarget(new ActionViewTarget(this, ActionViewTarget.Type.OVERFLOW))
                .setContent(R.string.showcase_main_title, R.string.showcase_main_text);
    }

    @Override
    public void onBackPressed() {
        // back may exit the app instead of closing the search action bar
        if (searchView != null && !searchView.isIconified()) {
            searchView.setIconified(true);
            MenuItemCompat.collapseActionView(searchItem);
        } else {
            super.onBackPressed();
        }
    }
}
