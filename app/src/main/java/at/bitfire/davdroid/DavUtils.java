/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.support.annotation.NonNull;

import org.apache.commons.lang3.StringUtils;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.SimpleResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import okhttp3.HttpUrl;

public class DavUtils {

    public static String ARGBtoCalDAVColor(int colorWithAlpha) {
        byte alpha = (byte)(colorWithAlpha >> 24);
        int color = colorWithAlpha & 0xFFFFFF;
        return String.format("#%06X%02X", color, alpha);
    }

    public static String lastSegmentOfUrl(@NonNull String url) {
        // the list returned by HttpUrl.pathSegments() is unmodifiable, so we have to create a copy
        List<String> segments = new LinkedList<>(HttpUrl.parse(url).pathSegments());
        Collections.reverse(segments);

        for (String segment : segments)
            if (!StringUtils.isEmpty(segment))
                return segment;

        return "/";
    }

    public static void prepareLookup(Context context , Lookup lookup) throws UnknownHostException {
        /* Since Android 8, the system properties net.dns1, net.dns2, ... are not available anymore.
            The current version of dnsjava relies on these properties to find the default name servers,
            so we have to add the servers explicitly (fortunately, there's an Android API to
            get the active DNS servers). */
        final ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService
                (Context.CONNECTIVITY_SERVICE);

        final LinkProperties activeLink = connectivity.getLinkProperties
                (connectivity.getActiveNetwork());
        if (null == activeLink) return;

        final List<InetAddress> inetAddresses = activeLink.getDnsServers();
        if (null == inetAddresses || 0 == inetAddresses.size()) return;

        int size = inetAddresses.size();
        SimpleResolver[] resolvers = new SimpleResolver[size];
        for (int i = 0; i < size; i++) {
            final InetAddress inetAddress = inetAddresses.get(i);
            App.log.fine("Using DNS server " + inetAddress.getHostAddress());
            SimpleResolver resolver = new SimpleResolver();
            resolver.setAddress(inetAddress);
            resolvers[i] = resolver;
        }

        lookup.setResolver(new ExtendedResolver(resolvers));
    }

}
