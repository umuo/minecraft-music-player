package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.CatalogException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class PlaylistUrlSupport {
    private static final Pattern NUMERIC = Pattern.compile("[1-9][0-9]{0,19}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private PlaylistUrlSupport() {}

    static String numericIdOrUrl(String value, Set<String> hosts, String... queryNames) {
        String input = checked(value);
        if (NUMERIC.matcher(input).matches()) return input;
        URI uri = publicUri(input, hosts);
        for (String name : queryNames) {
            String id = query(uri, name);
            if (id != null && NUMERIC.matcher(id).matches()) return id;
        }
        throw new CatalogException("Playlist URL does not contain a valid numeric id");
    }

    static String pathNumericId(String value, Set<String> hosts, Pattern pathPattern, String... queryNames) {
        String input = checked(value);
        if (NUMERIC.matcher(input).matches()) return input;
        URI uri = publicUri(input, hosts);
        var matcher = pathPattern.matcher(uri.getPath());
        if (matcher.find() && NUMERIC.matcher(matcher.group(1)).matches()) return matcher.group(1);
        for (String name : queryNames) {
            String id = query(uri, name);
            if (id != null && NUMERIC.matcher(id).matches()) return id;
        }
        throw new CatalogException("Playlist URL does not contain a valid playlist id");
    }

    static String tokenIdOrUrl(String value, Set<String> hosts, Pattern pathPattern, String... queryNames) {
        String input = checked(value);
        if (TOKEN.matcher(input).matches()) return input;
        URI uri = publicUri(input, hosts);
        var matcher = pathPattern.matcher(uri.getPath());
        if (matcher.find() && TOKEN.matcher(matcher.group(1)).matches()) return matcher.group(1);
        for (String name : queryNames) {
            String id = query(uri, name);
            if (id != null && TOKEN.matcher(id).matches()) return id;
        }
        throw new CatalogException("Playlist URL does not contain a valid playlist id");
    }

    private static String checked(String value) {
        String input = value == null ? "" : value.strip();
        if (input.isEmpty() || input.length() > 512 || input.indexOf('\0') >= 0) {
            throw new CatalogException("Playlist URL or id is empty or too long");
        }
        return input;
    }

    private static URI publicUri(String input, Set<String> hosts) {
        final URI uri;
        try { uri = URI.create(input); } catch (RuntimeException error) { throw new CatalogException("Invalid playlist URL", error); }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http")) || uri.getUserInfo() != null || uri.getPort() != -1
                || !hosts.contains(host)) throw new CatalogException("Playlist URL host is not allowed");
        return uri;
    }

    private static String query(URI uri, String wanted) {
        String query = uri.getRawQuery();
        if (query == null && uri.getRawFragment() != null && uri.getRawFragment().contains("?")) {
            query = uri.getRawFragment().substring(uri.getRawFragment().indexOf('?') + 1);
        }
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int split = pair.indexOf('=');
            if (split > 0 && pair.substring(0, split).equalsIgnoreCase(wanted)) return pair.substring(split + 1);
        }
        return null;
    }
}
