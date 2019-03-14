package com.zui.davdroid;

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.apache.commons.collections4.iterators.IteratorChain;
import org.apache.commons.collections4.iterators.SingletonIterator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.UrlUtils;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressbookHomeSet;
import at.bitfire.dav4android.property.CalendarHomeSet;
import at.bitfire.dav4android.property.CalendarProxyReadFor;
import at.bitfire.dav4android.property.CalendarProxyWriteFor;
import at.bitfire.dav4android.property.GroupMembership;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.HomeSets;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import at.bitfire.davdroid.model.ServiceDB.Services;
import lombok.Cleanup;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class RefreshCollections {
    public static final String KEY_SYNC_COLLECTION = "sync_collection";

    public static void refreshCollections(Context context, Account account, String serviceType, long serviceId) {
        OpenHelper dbHelper = null;
        try {
            dbHelper = new OpenHelper(context);
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // create authenticating OkHttpClient (credentials taken from account settings)
            OkHttpClient httpClient = HttpClient.create(context, account);

            // refresh home sets: principal
            Set<HttpUrl> homeSets = readHomeSets(db, serviceId);
            HttpUrl principal = readPrincipal(db, serviceId);
            if (principal != null) {
                App.log.fine("Querying principal for home sets");
                DavResource dav = new DavResource(httpClient, principal);
                queryHomeSets(serviceType, dav, homeSets);

                // refresh home sets: calendar-proxy-read/write-for
                CalendarProxyReadFor proxyRead = (CalendarProxyReadFor) dav.properties.get(CalendarProxyReadFor.NAME);
                if (proxyRead != null)
                    for (String href : proxyRead.principals) {
                        App.log.fine("Principal is a read-only proxy for " + href + ", checking for home sets");
                        queryHomeSets(serviceType, new DavResource(httpClient, dav.location.resolve(href)), homeSets);
                    }
                CalendarProxyWriteFor proxyWrite = (CalendarProxyWriteFor) dav.properties.get(CalendarProxyWriteFor.NAME);
                if (proxyWrite != null)
                    for (String href : proxyWrite.principals) {
                        App.log.fine("Principal is a read-write proxy for " + href + ", checking for home sets");
                        queryHomeSets(serviceType, new DavResource(httpClient, dav.location.resolve(href)), homeSets);
                    }

                // refresh home sets: direct group memberships
                GroupMembership groupMembership = (GroupMembership) dav.properties.get(GroupMembership.NAME);
                if (groupMembership != null)
                    for (String href : groupMembership.hrefs) {
                        App.log.fine("Principal is member of group " + href + ", checking for home sets");
                        DavResource group = new DavResource(httpClient, dav.location.resolve(href));
                        try {
                            queryHomeSets(serviceType, group, homeSets);
                        } catch (HttpException | DavException e) {
                            App.log.log(Level.WARNING, "Couldn't query member group ", e);
                        }
                    }
            }

            // now refresh collections (taken from home sets)
            Map<HttpUrl, CollectionInfo> collections = readCollections(db, serviceId);

            // (remember selections before)
            Set<HttpUrl> selectedCollections = new HashSet<>();
            for (CollectionInfo info : collections.values())
                if (info.selected)
                    selectedCollections.add(HttpUrl.parse(info.url));

            for (Iterator<HttpUrl> itHomeSets = homeSets.iterator(); itHomeSets.hasNext(); ) {
                HttpUrl homeSet = itHomeSets.next();
                App.log.fine("Listing home set " + homeSet);

                DavResource dav = new DavResource(httpClient, homeSet);
                try {
                    dav.propfind(1, CollectionInfo.DAV_PROPERTIES);
                    IteratorChain<DavResource> itCollections = new IteratorChain<>(dav.members.iterator(), new SingletonIterator(dav));
                    while (itCollections.hasNext()) {
                        DavResource member = itCollections.next();
                        CollectionInfo info = CollectionInfo.fromDavResource(member);
                        info.confirmed = true;
                        App.log.log(Level.FINE, "Found collection", info);

                        if ((serviceType.equals(Services.SERVICE_CARDDAV) && info.type == CollectionInfo.Type.ADDRESS_BOOK) ||
                                (serviceType.equals(Services.SERVICE_CALDAV) && info.type == CollectionInfo.Type.CALENDAR))
                            collections.put(member.location, info);
                    }
                } catch (HttpException e) {
                    if (e.status == 403 || e.status == 404 || e.status == 410)
                        // delete home set only if it was not accessible (40x)
                        itHomeSets.remove();
                }
            }

            // check/refresh unconfirmed collections
            for (Iterator<Map.Entry<HttpUrl, CollectionInfo>> iterator = collections.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<HttpUrl, CollectionInfo> entry = iterator.next();
                HttpUrl url = entry.getKey();
                CollectionInfo info = entry.getValue();

                if (!info.confirmed)
                    try {
                        DavResource dav = new DavResource(httpClient, url);
                        dav.propfind(0, CollectionInfo.DAV_PROPERTIES);
                        info = CollectionInfo.fromDavResource(dav);
                        info.confirmed = true;

                        // remove unusable collections
                        if ((serviceType.equals(Services.SERVICE_CARDDAV) && info.type != CollectionInfo.Type.ADDRESS_BOOK) ||
                                (serviceType.equals(Services.SERVICE_CALDAV) && info.type != CollectionInfo.Type.CALENDAR))
                            iterator.remove();
                    } catch (HttpException e) {
                        if (e.status == 403 || e.status == 404 || e.status == 410)
                            // delete collection only if it was not accessible (40x)
                            iterator.remove();
                        else
                            throw e;
                    }
            }

            // restore selections
            for (HttpUrl url : selectedCollections) {
                CollectionInfo info = collections.get(url);
                if (info != null)
                    info.selected = true;
            }

            db.beginTransactionNonExclusive();
            try {
                saveHomeSets(db, serviceId, homeSets);
                saveCollections(db, serviceId, collections.values());
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (InvalidAccountException e) {
            App.log.log(Level.SEVERE, "Invalid account", e);
        } catch (IOException | HttpException | DavException e) {
            App.log.log(Level.SEVERE, "Couldn't refresh collection list", e);
        } finally {
            if (null != dbHelper)
                dbHelper.close();
        }
    }

    /**
     * Checks if the given URL defines home sets and adds them to the home set list.
     *
     * @param serviceType CalDAV/CardDAV (calendar home set / addressbook home set)
     * @param dav         DavResource to check
     * @param homeSets    set where found home set URLs will be put into
     */
    private static void queryHomeSets(String serviceType, DavResource dav, Set<HttpUrl> homeSets) throws IOException, HttpException, DavException {
        if (Services.SERVICE_CARDDAV.equals(serviceType)) {
            dav.propfind(0, AddressbookHomeSet.NAME, GroupMembership.NAME);
            AddressbookHomeSet addressbookHomeSet = (AddressbookHomeSet) dav.properties.get(AddressbookHomeSet.NAME);
            if (addressbookHomeSet != null)
                for (String href : addressbookHomeSet.hrefs)
                    homeSets.add(UrlUtils.withTrailingSlash(dav.location.resolve(href)));
        } else if (Services.SERVICE_CALDAV.equals(serviceType)) {
            dav.propfind(0, CalendarHomeSet.NAME, CalendarProxyReadFor.NAME, CalendarProxyWriteFor.NAME, GroupMembership.NAME);
            CalendarHomeSet calendarHomeSet = (CalendarHomeSet) dav.properties.get(CalendarHomeSet.NAME);
            if (calendarHomeSet != null)
                for (String href : calendarHomeSet.hrefs)
                    homeSets.add(UrlUtils.withTrailingSlash(dav.location.resolve(href)));
        }
    }

    @Nullable
    private static HttpUrl readPrincipal(SQLiteDatabase db, long serviceId) {
        @Cleanup Cursor cursor = db.query(Services._TABLE, new String[]{Services.PRINCIPAL}, Services.ID + "=?", new String[]{String.valueOf(serviceId)}, null, null, null);
        if (cursor.moveToNext()) {
            String principal = cursor.getString(0);
            if (principal != null)
                return HttpUrl.parse(cursor.getString(0));
        }
        return null;
    }

    @NonNull
    private static Set<HttpUrl> readHomeSets(SQLiteDatabase db, long serviceId) {
        Set<HttpUrl> homeSets = new LinkedHashSet<>();
        @Cleanup Cursor cursor = db.query(HomeSets._TABLE, new String[]{HomeSets.URL}, HomeSets.SERVICE_ID + "=?", new String[]{String.valueOf(serviceId)}, null, null, null);
        while (cursor.moveToNext())
            homeSets.add(HttpUrl.parse(cursor.getString(0)));
        return homeSets;
    }

    private static void saveHomeSets(SQLiteDatabase db, long serviceId, Set<HttpUrl> homeSets) {
        db.delete(HomeSets._TABLE, HomeSets.SERVICE_ID + "=?", new String[]{String.valueOf(serviceId)});
        for (HttpUrl homeSet : homeSets) {
            ContentValues values = new ContentValues(1);
            values.put(HomeSets.SERVICE_ID, serviceId);
            values.put(HomeSets.URL, homeSet.toString());
            db.insertOrThrow(HomeSets._TABLE, null, values);
        }
    }

    @NonNull
    private static Map<HttpUrl, CollectionInfo> readCollections(SQLiteDatabase db, long serviceId) {
        Map<HttpUrl, CollectionInfo> collections = new LinkedHashMap<>();
        @Cleanup Cursor cursor = db.query(Collections._TABLE, null, Collections.SERVICE_ID + "=?", new String[]{String.valueOf(serviceId)}, null, null, null);
        while (cursor.moveToNext()) {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(cursor, values);
            collections.put(HttpUrl.parse(values.getAsString(Collections.URL)), CollectionInfo.fromDB(values));
        }
        return collections;
    }

    private static void saveCollections(SQLiteDatabase db, long serviceId, Iterable<CollectionInfo> collections) {
        db.delete(Collections._TABLE, HomeSets.SERVICE_ID + "=?", new String[]{String.valueOf(serviceId)});
        for (CollectionInfo collection : collections) {
            ContentValues values = collection.toDB();
            App.log.log(Level.FINE, "Saving collection", values);
            values.put(Collections.SERVICE_ID, serviceId);
            // Sync this collections default.
            values.put(Collections.SYNC, 1);
            db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
}
