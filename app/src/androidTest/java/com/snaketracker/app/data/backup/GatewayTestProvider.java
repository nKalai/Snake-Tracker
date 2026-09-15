package com.snaketracker.app.data.backup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Minimal document-provider stand-in, declared only in the test APK. The
 * first path segment selects the behavior the export gateway should meet:
 *
 * <ul>
 *   <li>{@code named/<file name>}: answers the DISPLAY_NAME projection query
 *       with {@link #PROVIDER_DISPLAY_NAME}, deliberately different from the
 *       URI's name so a reported-name assertion cannot pass via the
 *       last-path-segment fallback.</li>
 *   <li>{@code throws/<file name>}: answers the query with a
 *       {@link SecurityException}.</li>
 *   <li>{@code nullstream/<file name>}: {@link #openFile} returns a null
 *       descriptor, which {@code ContentResolver.openOutputStream} surfaces
 *       as the null stream the gateway must type as DESTINATION_UNOPENABLE.</li>
 * </ul>
 *
 * <p>Writes land in the test package's cache dir; tests that assert on the
 * written bytes use a {@code file://} destination instead.
 *
 * <p>Deliberately plain Java: Android may host a test-APK provider in a
 * process of its own, and that process starts without the test APK's
 * secondary dex files, so any Kotlin-stdlib reference (Intrinsics and
 * friends) crashes it mid-binder-call and hangs the caller. Only
 * boot-classpath types are safe here.
 */
public class GatewayTestProvider extends ContentProvider {

    public static final String AUTHORITY = "com.snaketracker.app.test.gateway";

    /** Display name {@code named} mode reports; different from any URI name on purpose. */
    public static final String PROVIDER_DISPLAY_NAME = "Renamed Backup.json";

    /** {@code content://<authority>/<mode>/<name>} for the behavior {@code mode}. */
    public static Uri uri(String mode, String name) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(mode)
                .appendPath(name)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return openFile(uri, mode, null);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if ("nullstream".equals(firstSegment(uri))) {
            return null;
        }
        List<String> segments = uri.getPathSegments();
        StringBuilder name = new StringBuilder();
        for (int i = 1; i < segments.size(); i++) {
            if (name.length() > 0) {
                name.append('-');
            }
            name.append(segments.get(i));
        }
        return ParcelFileDescriptor.open(
                new File(getContext().getCacheDir(), name.toString()),
                ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        String mode = firstSegment(uri);
        if ("throws".equals(mode)) {
            throw new SecurityException("grant only covers writing");
        }
        if ("named".equals(mode)) {
            MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
            cursor.addRow(new Object[] {PROVIDER_DISPLAY_NAME});
            return cursor;
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private static String firstSegment(Uri uri) {
        List<String> segments = uri.getPathSegments();
        return segments.isEmpty() ? "" : segments.get(0);
    }
}
