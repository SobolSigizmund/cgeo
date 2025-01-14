package cgeo.geocaching;

import static org.assertj.core.api.Assertions.assertThat;

import cgeo.CGeoTestCase;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.gc.GCLogin;
import cgeo.geocaching.connector.gc.GCMemberState;
import cgeo.geocaching.connector.gc.GCParser;
import cgeo.geocaching.connector.gc.MapTokens;
import cgeo.geocaching.connector.gc.Tile;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LogType;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.loaders.RecaptchaReceiver;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.maps.LivemapStrategy;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.LogEntry;
import cgeo.geocaching.models.Trackable;
import cgeo.geocaching.settings.Credentials;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.settings.TestSettings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.test.mock.GC1ZXX2Android;
import cgeo.geocaching.test.mock.GC2CJPFAndroid;
import cgeo.geocaching.test.mock.GC2JVEHAndroid;
import cgeo.geocaching.test.mock.MockedCacheAndroid;
import cgeo.geocaching.utils.CancellableHandler;
import cgeo.geocaching.utils.Log;
import cgeo.test.Compare;

import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.GregorianCalendar;

/**
 * The c:geo application test. It can be used for tests that require an
 * application and/or context.
 */

public class CgeoApplicationTest extends CGeoTestCase {

    private static final MapTokens INVALID_TOKEN = null;

    /**
     * The name 'test preconditions' is a convention to signal that if this test
     * doesn't pass, the test case was not set up properly and it might explain
     * any and all failures in other tests. This is not guaranteed to run before
     * other tests, as junit uses reflection to find the tests.
     */
    @SuppressWarnings("static-method")
    @SmallTest
    public void testPreconditions() {
        assertThat(GCLogin.getInstance().login()).as("User and password must be provided").isEqualTo(StatusCode.NO_ERROR);
        assertThat(Settings.isGCPremiumMember()).as("User must be premium member for some of the tests to succeed").isTrue();
    }

    /**
     * Test {@link GCParser#searchTrackable(String, String, String)}
     */
    @MediumTest
    public static void testSearchTrackableNotExisting() {
        final Trackable tb = GCParser.searchTrackable("123456", null, null);
        assertThat(tb).isNull();
    }

    /**
     * Test {@link GCParser#searchTrackable(String, String, String)}
     */
    @MediumTest
    public static void testSearchTrackable() {
        final Trackable tb = GCParser.searchTrackable("TB2J1VZ", null, null);
        assertThat(tb).isNotNull();
        assert tb != null; // eclipse bug
        // fix data
        assertThat(tb.getGuid()).isEqualTo("aefffb86-099f-444f-b132-605436163aa8");
        assertThat(tb.getGeocode()).isEqualTo("TB2J1VZ");
        assertThat(tb.getIconUrl()).endsWith("://www.geocaching.com/images/wpttypes/21.gif");
        assertThat(tb.getName()).isEqualTo("blafoo's Children Music CD");
        assertThat(tb.getType()).isEqualTo("Travel Bug Dog Tag");
        assertThat(tb.getReleased()).isEqualTo(new GregorianCalendar(2009, 8 - 1, 24).getTime());
        assertThat(tb.getOrigin()).isEqualTo("Niedersachsen, Germany");
        assertThat(tb.getOwner()).isEqualTo("blafoo");
        assertThat(tb.getOwnerGuid()).isEqualTo("0564a940-8311-40ee-8e76-7e91b2cf6284");
        assertThat(tb.getGoal()).isEqualTo("Kinder erfreuen.<br /><br />Make children happy.");
        assertThat(tb.getDetails()).startsWith("Auf der CD sind");
        // the host of the image can vary
        assertThat(tb.getImage()).endsWith("geocaching.com/track/large/38382780-87a7-4393-8393-78841678ee8c.jpg");
        // Following data can change over time
        assertThat(tb.getDistance()).isGreaterThanOrEqualTo(10617.8f);
        assertThat(tb.getLogs().size()).isGreaterThanOrEqualTo(10);
        assertThat(tb.getSpottedType() == Trackable.SPOTTED_CACHE || tb.getSpottedType() == Trackable.SPOTTED_USER || tb.getSpottedType() == Trackable.SPOTTED_UNKNOWN).isTrue();
        // no assumption possible: assertThat(tb.getSpottedGuid()).isEqualTo("faa2d47d-19ea-422f-bec8-318fc82c8063");
        // no assumption possible: assertThat(tb.getSpottedName()).isEqualTo("Nice place for a break cache");

        // we can't check specifics in the log entries since they change, but we can verify data was parsed
        for (final LogEntry log : tb.getLogs()) {
            assertThat(log.date).isGreaterThan(0);
            assertThat(log.author).isNotEmpty();
            if (log.getType() == LogType.PLACED_IT || log.getType() == LogType.RETRIEVED_IT) {
                assertThat(log.cacheName).isNotEmpty();
                assertThat(log.cacheGuid).isNotEmpty();
            } else {
                assertThat(log.getType()).isNotEqualTo(LogType.UNKNOWN);
            }
        }
    }

    /**
     * Test {@link Geocache#searchByGeocode(String, String, boolean, CancellableHandler)}
     */
    @MediumTest
    public static Geocache testSearchByGeocode(final String geocode) {
        final SearchResult search = Geocache.searchByGeocode(geocode, null, true, null);
        assertThat(search).isNotNull();
        if (Settings.isGCPremiumMember() || search.getError() == StatusCode.NO_ERROR) {
            assertThat(search.getGeocodes()).containsExactly(geocode);
            return DataStore.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);
        }
        assertThat(search.getGeocodes()).isEmpty();
        return null;
    }

    /**
     * Test {@link Geocache#searchByGeocode(String, String, boolean, CancellableHandler)}
     */
    @MediumTest
    public static void testSearchByGeocodeNotExisting() {
        final SearchResult search = Geocache.searchByGeocode("GC123456", null, true, null);
        assertThat(search).isNotNull();
        assertThat(search.getError()).isEqualTo(StatusCode.CACHE_NOT_FOUND);
    }

    /**
     * Set the login data to the cgeo login, run the given Runnable, and restore the login.
     *
     */
    private static void withMockedLoginDo(final Runnable runnable) {
        final Credentials credentials = Settings.getGcCredentials();
        final GCMemberState memberStatus = Settings.getGCMemberStatus();

        try {
            runnable.run();
        } finally {
            // restore user and password
            TestSettings.setLogin(credentials);
            Settings.setGCMemberStatus(memberStatus);
            GCLogin.getInstance().login();
        }
    }

    /**
     * Test {@link Geocache#searchByGeocode(String, String, boolean, CancellableHandler)}
     */
    @MediumTest
    public static void testSearchByGeocodeNotLoggedIn() {
        withMockedLoginDo(new Runnable() {

            @Override
            public void run() {
                // non premium cache
                MockedCacheAndroid cache = new GC2CJPFAndroid();

                deleteCacheFromDBAndLogout(cache.getGeocode());

                SearchResult search = Geocache.searchByGeocode(cache.getGeocode(), null, true, null);
                assertThat(search).isNotNull();
                assertThat(search.getGeocodes()).containsExactly(cache.getGeocode());
                final Geocache searchedCache = search.getFirstCacheFromResult(LoadFlags.LOAD_CACHE_OR_DB);
                // coords must be null if the user is not logged in
                assertThat(searchedCache).isNotNull();
                assert searchedCache != null; // eclipse bug
                assertThat(searchedCache.getCoords()).isNull();

                // premium cache. Not visible to guests
                cache = new GC2JVEHAndroid();

                deleteCacheFromDBAndLogout(cache.getGeocode());

                search = Geocache.searchByGeocode(cache.getGeocode(), null, true, null);
                assertThat(search).isNotNull();
                assertThat(search.getGeocodes()).isEmpty();
            }
        });
    }

    /**
     * Test {@link Geocache#searchByGeocode(String, String, boolean, CancellableHandler)}
     */
    @MediumTest
    public static void testSearchErrorOccured() {
        withMockedLoginDo(new Runnable() {

            @Override
            public void run() {
                // non premium cache
                final MockedCacheAndroid cache = new GC1ZXX2Android();

                deleteCacheFromDBAndLogout(cache.getGeocode());

                final SearchResult search = Geocache.searchByGeocode(cache.getGeocode(), null, true, null);
                assertThat(search).isNotNull();
                assertThat(search.getGeocodes()).isEmpty();
            }
        });
    }

    /**
     * mock the "exclude disabled caches" and "exclude my caches" options for the execution of the runnable
     *
     */
    private static void withMockedFilters(final Runnable runnable) {
        // backup user settings
        final boolean excludeMine = Settings.isExcludeMyCaches();
        final boolean excludeDisabled = Settings.isExcludeDisabledCaches();
        try {
            // set up settings required for test
            TestSettings.setExcludeMine(false);
            TestSettings.setExcludeDisabledCaches(false);

            runnable.run();

        } finally {
            // restore user settings
            TestSettings.setExcludeMine(excludeMine);
            TestSettings.setExcludeDisabledCaches(excludeDisabled);
        }
    }

    /**
     * Test {@link GCParser#searchByCoords(Geopoint, CacheType, boolean, RecaptchaReceiver)}
     */
    @MediumTest
    public static void testSearchByCoords() {
        withMockedFilters(new Runnable() {

            @Override
            public void run() {
                final SearchResult search = GCParser.searchByCoords(new Geopoint("N 50° 06.654 E 008° 39.777"), CacheType.MYSTERY, false, null);
                assertThat(search).isNotNull();
                assertThat(search.getGeocodes().size()).isGreaterThanOrEqualTo(20);
                assertThat(search.getGeocodes()).contains("GC1HBMY");
            }
        });
    }

    /**
     * Test {@link GCParser#searchByOwner(String, CacheType, boolean, RecaptchaReceiver)}
     */
    @MediumTest
    public static void testSearchByOwner() {
        withMockedFilters(new Runnable() {

            @Override
            public void run() {
                final SearchResult search = GCParser.searchByOwner("blafoo", CacheType.MYSTERY, false, null);
                assertThat(search).isNotNull();
                assertThat(search.getGeocodes()).hasSize(7);
                assertThat(search.getGeocodes()).contains("GC36RT6");
            }
        });
    }

    /**
     * Test {@link GCParser#searchByUsername(String, CacheType, boolean, RecaptchaReceiver)}
     */
    @MediumTest
    public static void testSearchByUsername() {
        withMockedFilters(new Runnable() {

            @Override
            public void run() {
                final SearchResult search = GCParser.searchByUsername("blafoo", CacheType.WEBCAM, false, null);
                assertThat(search).isNotNull();
                assertThat(search.getTotalCountGC()).isEqualTo(5);
                assertThat(search.getGeocodes()).contains("GCP0A9");
            }
        });
    }

    /**
     * Test {@link ConnectorFactory#searchByViewport(Viewport, MapTokens)}
     */
    @MediumTest
    public static void testSearchByViewport() {
        withMockedFilters(new Runnable() {

            @Override
            public void run() {
                // backup user settings
                final LivemapStrategy strategy = Settings.getLiveMapStrategy();
                final CacheType cacheType = Settings.getCacheType();

                try {
                    // set up settings required for test
                    TestSettings.setExcludeMine(false);
                    Settings.setCacheType(CacheType.ALL);

                    final GC2CJPFAndroid mockedCache = new GC2CJPFAndroid();
                    deleteCacheFromDB(mockedCache.getGeocode());

                    final MapTokens tokens = GCLogin.getInstance().getMapTokens();
                    final Viewport viewport = new Viewport(mockedCache, 0.003, 0.003);

                    // check coords for DETAILED
                    Settings.setLiveMapStrategy(LivemapStrategy.DETAILED);
                    SearchResult search = ConnectorFactory.searchByViewport(viewport, tokens);
                    assertThat(search).isNotNull();
                    assertThat(search.getGeocodes()).contains(mockedCache.getGeocode());
                    Geocache parsedCache = DataStore.loadCache(mockedCache.getGeocode(), LoadFlags.LOAD_CACHE_OR_DB);
                    assert parsedCache != null;
                    assertThat(parsedCache).isNotNull();

                    assertThat(mockedCache.getCoords().equals(parsedCache.getCoords())).isEqualTo(Settings.isGCPremiumMember());
                    assertThat(parsedCache.isReliableLatLon()).isEqualTo(Settings.isGCPremiumMember());

                    // check update after switch strategy to FAST
                    Settings.setLiveMapStrategy(LivemapStrategy.FAST);
                    Tile.cache.removeFromTileCache(mockedCache);

                    search = ConnectorFactory.searchByViewport(viewport, tokens);
                    assertThat(search).isNotNull();
                    assertThat(search.getGeocodes()).contains(mockedCache.getGeocode());
                    parsedCache = DataStore.loadCache(mockedCache.getGeocode(), LoadFlags.LOAD_CACHE_OR_DB);
                    assert parsedCache != null;
                    assertThat(parsedCache).isNotNull();

                    assertThat(mockedCache.getCoords().equals(parsedCache.getCoords())).isEqualTo(Settings.isGCPremiumMember());
                    assertThat(parsedCache.isReliableLatLon()).isEqualTo(Settings.isGCPremiumMember());

                } finally {
                    // restore user settings
                    Settings.setLiveMapStrategy(strategy);
                    Settings.setCacheType(cacheType);
                }
            }
        });
    }

    /**
     * Test {@link ConnectorFactory#searchByViewport(Viewport, MapTokens)}
     */
    @MediumTest
    public static void testSearchByViewportNotLoggedIn() {
        withMockedLoginDo(new Runnable() {

            @Override
            public void run() {
                final LivemapStrategy strategy = Settings.getLiveMapStrategy();
                final LivemapStrategy testStrategy = LivemapStrategy.FAST; // FASTEST, FAST or DETAILED for tests
                Settings.setLiveMapStrategy(testStrategy);
                final CacheType cacheType = Settings.getCacheType();

                try {

                    // non premium cache
                    MockedCacheAndroid cache = new GC2CJPFAndroid();
                    deleteCacheFromDBAndLogout(cache.getGeocode());
                    Tile.cache.removeFromTileCache(cache);
                    Settings.setCacheType(CacheType.ALL);

                    Viewport viewport = new Viewport(cache, 0.003, 0.003);
                    SearchResult search = ConnectorFactory.searchByViewport(viewport, INVALID_TOKEN);

                    assertThat(search).isNotNull();
                    assertThat(search.getGeocodes()).contains(cache.getGeocode());
                    // coords differ
                    final Geocache cacheFromViewport = DataStore.loadCache(cache.getGeocode(), LoadFlags.LOAD_CACHE_OR_DB);
                    assert cacheFromViewport != null;
                    assertThat(cacheFromViewport).isNotNull();
                    Log.d("cgeoApplicationTest.testSearchByViewportNotLoggedIn: Coords expected = " + cache.getCoords());
                    Log.d("cgeoApplicationTest.testSearchByViewportNotLoggedIn: Coords actual = " + cacheFromViewport.getCoords());
                    assertThat(cache.getCoords().distanceTo(cacheFromViewport.getCoords()) <= 1e-3).isFalse();
                    // depending on the chosen strategy the coords can be reliable or not
                    assertThat(cacheFromViewport.isReliableLatLon()).isEqualTo(testStrategy == LivemapStrategy.DETAILED);

                    // premium cache
                    cache = new GC2JVEHAndroid();
                    deleteCacheFromDBAndLogout(cache.getGeocode());

                    viewport = new Viewport(cache, 0.003, 0.003);
                    search = ConnectorFactory.searchByViewport(viewport, INVALID_TOKEN);

                    assertThat(search).isNotNull();
                    // In the meantime, premium-member caches are also shown on map when not logged in
                    assertThat(search.getGeocodes()).contains(cache.getGeocode());

                } finally {
                    Settings.setLiveMapStrategy(strategy);
                    Settings.setCacheType(cacheType);
                }
            }
        });
    }

    /**
     * Test cache parsing. Esp. useful after a GC.com update
     */
    public static void testSearchByGeocodeBasis() {
        for (final MockedCacheAndroid mockedCache : MockedCacheAndroid.MOCKED_CACHES) {
            final String oldUser = mockedCache.getMockedDataUser();
            try {
                mockedCache.setMockedDataUser(Settings.getUserName());
                final Geocache parsedCache = CgeoApplicationTest.testSearchByGeocode(mockedCache.getGeocode());
                Compare.assertCompareCaches(mockedCache, parsedCache, true);
            } finally {
                mockedCache.setMockedDataUser(oldUser);
            }
        }
    }

    /**
     * Caches that are good test cases
     */
    public static void testSearchByGeocodeSpecialties() {
        final Geocache gcv2r9 = CgeoApplicationTest.testSearchByGeocode("GCV2R9");
        assertThat(gcv2r9.getLocation()).isEqualTo("California, United States");

        final Geocache gc1zxez = CgeoApplicationTest.testSearchByGeocode("GC1ZXEZ");
        assertThat(gc1zxez.getOwnerUserId()).isEqualTo("Ms.Marple/Mr.Stringer");
    }

    /** Remove cache from DB and cache to ensure that the cache is not loaded from the database */
    private static void deleteCacheFromDBAndLogout(final String geocode) {
        deleteCacheFromDB(geocode);

        GCLogin.getInstance().logout();
        // Modify login data to avoid an automatic login again
        TestSettings.setLogin(new Credentials("c:geo", "c:geo"));
        Settings.setGCMemberStatus(GCMemberState.BASIC);
    }

}
