package com.android.providers.contacts;

import android.Manifest;
import android.annotation.Nullable;
import android.content.ContentProvider;
import android.content.IContentProvider;
import android.content.pm.GosPackageState;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.ext.cscopes.ContactScopesApi;
import android.ext.cscopes.ContactScopesStorage;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongArray;

import com.android.internal.app.RedirectedContentProvider;

import java.util.Arrays;
import java.util.Objects;

import libcore.util.EmptyArray;

import static android.ext.cscopes.ContactScope.TYPE_CONTACT;
import static android.ext.cscopes.ContactScope.TYPE_EMAIL;
import static android.ext.cscopes.ContactScope.TYPE_GROUP;
import static android.ext.cscopes.ContactScope.TYPE_NUMBER;
import static com.android.providers.contacts.ContactsProvider2.*;

public class ScopedContactsProvider extends RedirectedContentProvider {
    ContactsProvider2 provider;

    @Override
    public boolean onCreate() {
        TAG = "ScopedContactsProvider";
        authorityOverride = ContactsContract.AUTHORITY;

        super.onCreate();

        IContentProvider iprovider = requireContext().getContentResolver().acquireProvider(ContactsContract.AUTHORITY);
        provider = (ContactsProvider2) ContentProvider.coerceToLocalContentProvider(iprovider);

        return true;
    }

    private static void checkAccess(@Nullable GosPackageState ps) {
        boolean canAccess = ps != null && ps.hasFlag(GosPackageState.FLAG_CONTACT_SCOPES_ENABLED);
        if (!canAccess) {
            throw new SecurityException();
        }
    }

    @Nullable
    private GosPackageState getCallerGosPackageState() {
        return GosPackageState.get(Objects.requireNonNull(getCallingPackage()));
    }

    @Override
    public Cursor queryInner(Uri uri, @Nullable String[] projection, @Nullable String selection,
                             @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        GosPackageState callerPackageState = getCallerGosPackageState();
        checkAccess(callerPackageState);

        if (ContactScopesStorage.isEmpty(callerPackageState)) {
            return ContactsProvider2.createEmptyCursor(uri, projection);
        }

        ContactScopesStorage css = ContactScopesStorage.deserialize(callerPackageState);

        final long token = Binder.clearCallingIdentity();
        try (Cursor c = queryFiltered(css, uri, projection, selection, selectionArgs, sortOrder)) {
            // create a new cursor to prevent direct connection between the caller and contacts
            // provider, and to remove cursor extras and other extraneous data
            return cursorToMatrixCursor(c);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Nullable
    Cursor queryFiltered(ContactScopesStorage css, Uri uri, @Nullable String[] projection, @Nullable String origSelection,
                               @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        StringBuilder sel = new StringBuilder(TextUtils.length(origSelection) + 200);
        sel.append('(');
        // see ContactsProvider2.queryLocal()
        switch (ContactsProvider2.sUriMatcher.match(uri)) {
            // setTablesAndProjectionMapForContacts()
            case CONTACTS:
            case CONTACTS_ID:
            case CONTACTS_LOOKUP:
            case CONTACTS_LOOKUP_ID:
            case CONTACTS_FILTER:
                getContactsViewSelection(css, sel);
                break;

            // setTablesAndProjectionMapForData()
            case CONTACTS_LOOKUP_DATA:
            case CONTACTS_LOOKUP_ID_DATA:
            case CONTACTS_LOOKUP_PHOTO:
            case CONTACTS_LOOKUP_ID_PHOTO:
            case CONTACTS_ID_DATA:
            case PHONES:
            case PHONES_ID:
            case CALLABLES:
            case CALLABLES_ID:
            case PHONES_FILTER:
            case CALLABLES_FILTER:
            case EMAILS:
            case EMAILS_ID:
            case EMAILS_LOOKUP:
            case EMAILS_FILTER:
            case CONTACTABLES:
            case CONTACTABLES_FILTER:
            case POSTALS:
            case POSTALS_ID:
            case RAW_CONTACTS_ID_DATA:
            case DATA:
            case DATA_ID:
                getDataViewSelection(css, sel);
                break;

            // setTablesAndProjectionMapForEntities()
            case CONTACTS_ID_ENTITIES:
            case CONTACTS_LOOKUP_ENTITIES:
            case CONTACTS_LOOKUP_ID_ENTITIES:
            // setTablesAndProjectionMapForRawEntities()
            case RAW_CONTACT_ENTITIES:
            case RAW_CONTACT_ID_ENTITY:
                getEntitiesViewSelection(css, sel);
                break;

            // setTablesAndProjectionMapForRawContacts()
            case RAW_CONTACTS:
            case RAW_CONTACTS_ID:
                getRawContactsViewSelection(css, sel);
                break;

            default:
                return ContactsProvider2.createEmptyCursor(uri, projection);
        }
        sel.append(')');
        if (origSelection != null) {
            sel.append(" AND (");
            String accountFragment = ContactsContract.RawContacts.ACCOUNT_NAME + " is null";
            String origSelectionLower = origSelection.toLowerCase();
            int idx = origSelectionLower.indexOf(accountFragment);
            if (idx >= 0) {
                // Since account names are hidden, callers might filter on null account name, which
                // denotes the local contacts storage. Negate this filter to match contacts from any
                // account.
                int insertionIdx = idx + accountFragment.length();
                sel.append(origSelection, 0, insertionIdx);
                sel.append(" OR " + ContactsContract.RawContacts.ACCOUNT_NAME + " IS NOT NULL");
                sel.append(origSelection, insertionIdx, origSelection.length());
            } else {
                sel.append(origSelection);
            }
            sel.append(')');
        }

        if (DEBUG) Log.w(TAG, "query selection " + sel);

        return provider.query(uri, projection, sel.toString(), selectionArgs, sortOrder);
    }

    private MatrixCursor cursorToMatrixCursor(@Nullable Cursor c) {
        if (c == null) {
            return null;
        }
        final String[] columnNames = c.getColumnNames();
        final int columnCount = columnNames.length;

        boolean[] skippedColumns = new boolean[columnCount];
        for (int i = 0; i < columnCount; ++i) {
            switch (columnNames[i]) {
                case ContactsContract.RawContacts.ACCOUNT_NAME:
                case ContactsContract.RawContacts.ACCOUNT_TYPE:
                case ContactsContract.RawContacts.ACCOUNT_TYPE_AND_DATA_SET:
                case ContactsContract.RawContacts.SOURCE_ID:
                case ContactsContract.RawContacts.SYNC1:
                case ContactsContract.RawContacts.SYNC2:
                case ContactsContract.RawContacts.SYNC3:
                case ContactsContract.RawContacts.SYNC4:
                case ContactsContract.Data.PHOTO_FILE_ID:
                case ContactsContract.Data.PHOTO_ID:
                case ContactsContract.Data.PHOTO_THUMBNAIL_URI:
                case ContactsContract.Data.PHOTO_URI:
                    skippedColumns[i] = true;
            }
        }

        Object[] columns = new Object[columnCount];

        var mc = new MatrixCursor(columnNames, c.getCount());

        while (c.moveToNext()) {
            Arrays.fill(columns, null);
            for (int i = 0; i < columnCount; ++i) {
                if (skippedColumns[i]) {
                    continue;
                }
                Object v;
                switch (c.getType(i)) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        v = Long.valueOf(c.getLong(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        v = Double.valueOf(c.getDouble(i));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        v = c.getString(i);
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        v = c.getBlob(i);
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                    default:
                        v = null;
                }
                columns[i] = v;
            }
            if (DEBUG) Log.w(TAG, "returning row " + Arrays.toString(columns));
            mc.addRow(columns);
        }

        Uri notificationUri = mc.getNotificationUri();
        if (notificationUri != null) {
            mc.setNotificationUri(requireContext().getContentResolver(), notificationUri);
        }

        return mc;
    }

    private static final int CHARS_PER_ID = 20; // up to 19 digits for signed 64-bit int, plus comma

    private long[] getGroupsContactIds(ContactScopesStorage css) {
        long[] groupIds = css.getIds(TYPE_GROUP);
        if (groupIds.length == 0) {
            return EmptyArray.LONG;
        }

        var sel = new StringBuilder(20 + (groupIds.length * CHARS_PER_ID));
        sel.append(ContactsContract.Data.MIMETYPE + '=' + ContactsContract.CommonDataKinds.GroupMembership.MIMETYPE);
        sel.append(" AND " + ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID);
        appendSelectionList(groupIds, sel);

        return queryContactIdsFromDataTable(sel.toString());
    }

    private static final int DEFAULT_IDS_ARR_LEN = 50;

    private long[] getDataTableContactIds(ContactScopesStorage css) {
        var ids = new LongArray(DEFAULT_IDS_ARR_LEN);
        addDataTableIds(css, ids);

        final int numIds = ids.size();

        if (numIds == 0) {
            return EmptyArray.LONG;
        }

        var sel = new StringBuilder(20 + (numIds * CHARS_PER_ID));

        sel.append(BaseColumns._ID);
        appendSelectionList(ids, sel);

        return queryContactIdsFromDataTable(sel.toString());
    }

    private long[] queryContactIdsFromDataTable(String selection) {
        String[] proj = { ContactsContract.Data.CONTACT_ID };
        return queryIds(ContactsContract.Data.CONTENT_URI, proj, selection);
    }

    private long[] queryIds(Uri uri, String[] projection, String selection) {
        try (Cursor c = provider.query(uri, projection, selection, null, null)) {
            if (c == null) {
                return EmptyArray.LONG;
            }

            var arr = new LongArray(c.getCount());
            while (c.moveToNext()) {
                arr.add(c.getLong(0));
            }
            return arr.toArray();
        }
    }

    private LongArray getContactIds(ContactScopesStorage css) {
        var ids = new LongArray(0);
        addAll(ids,
                getGroupsContactIds(css),
                getDataTableContactIds(css),
                css.getIds(TYPE_CONTACT)
        );
        return ids;
    }

    private void getContactsViewSelection(ContactScopesStorage css, StringBuilder b) {
        b.append(BaseColumns._ID);
        appendSelectionList(getContactIds(css), b);
    }

    private void getRawContactsViewSelection(ContactScopesStorage css, StringBuilder b) {
        b.append(ContactsContract.RawContacts.CONTACT_ID);
        appendSelectionList(getContactIds(css), b);
    }

    private void getDataViewSelection(ContactScopesStorage css, StringBuilder b) {
        getDataOrEntityViewSelection(css, VIEW_TYPE_DATA, b);
    }

    private void getEntitiesViewSelection(ContactScopesStorage css, StringBuilder b) {
        getDataOrEntityViewSelection(css, VIEW_TYPE_ENTITY, b);
    }

    private static final int VIEW_TYPE_DATA = 0;
    private static final int VIEW_TYPE_ENTITY = 1;

    private static void addDataTableIds(ContactScopesStorage css, LongArray dst) {
        addAll(dst, css.getIds(TYPE_NUMBER), css.getIds(TYPE_EMAIL));
    }

    private void getDataOrEntityViewSelection(ContactScopesStorage css, int type, StringBuilder b) {
        String dataColumn;
        String contactIdColumn;
        String mimeTypeColumn;
        switch (type) {
            case VIEW_TYPE_DATA:
                dataColumn = BaseColumns._ID;
                contactIdColumn = ContactsContract.Data.CONTACT_ID;
                mimeTypeColumn = ContactsContract.Data.MIMETYPE;
                break;
            case VIEW_TYPE_ENTITY:
                dataColumn = ContactsContract.RawContactsEntity.DATA_ID;
                contactIdColumn = ContactsContract.RawContactsEntity.CONTACT_ID;
                mimeTypeColumn = ContactsContract.RawContactsEntity.MIMETYPE;
                break;
            default:
                throw new IllegalArgumentException();
        }

        var ids = new LongArray(DEFAULT_IDS_ARR_LEN);
        addDataTableIds(css, ids);

        b.append(dataColumn);
        appendSelectionList(ids, b);
        ids.clear();

        addAll(ids, getGroupsContactIds(css), css.getIds(TYPE_CONTACT));

        {
            b.append(" OR (");
            b.append(contactIdColumn);
            appendSelectionList(ids, b);

            b.append(" AND ");

            String[] blockedMimes = {
                    ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
            };

            b.append(mimeTypeColumn);
            b.append(" NOT");
            appendSelectionList(blockedMimes, b);
            b.append(')');
        }
        {
            b.append(" OR (");
            b.append(contactIdColumn);
            {
                ids.clear();
                ids.addAll(getDataTableContactIds(css));
                appendSelectionList(ids, b);
                b.append(" AND ");
                b.append(mimeTypeColumn);

                String[] allowedMimes = {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                };
                appendSelectionList(allowedMimes, b);
            }

            b.append(')');
        }
    }

    static Uri getUri(Uri base, long id) {
        Uri.Builder b = base.buildUpon();
        b.appendPath(Long.toString(id));
        return b.build();
    }

    private static void appendSelectionList(LongArray longArray, StringBuilder dst) {
        appendSelectionList(longArray.toArray(), dst);
    }

    private static void appendSelectionList(long[] ids, StringBuilder dst) {
        // needed for detecting duplicates
        Arrays.sort(ids);

        dst.ensureCapacity(20 + (CHARS_PER_ID * ids.length));
        dst.append(" IN (");

        boolean firstIteration = true;
        long prevId = 0;
        for (long id : ids) {
            if (firstIteration) {
                firstIteration = false;
            } else {
                if (prevId == id) {
                    // skip duplicates
                    continue;
                }
                dst.append(',');
            }
            dst.append(id);
            prevId = id;
        }

        dst.append(')');
    }

    private static void appendSelectionList(String[] arr, StringBuilder b) {
        b.append(" IN (");

        boolean firstIteration = true;
        for (String s : arr) {
            if (firstIteration) {
                firstIteration = false;
            } else {
                b.append(',');
            }
            b.append('\'');
            if (s.indexOf('\'') >= 0) {
                throw new IllegalArgumentException(s);
            }
            b.append(s);
            b.append('\'');
        }
        b.append(')');
    }

    private static void addAll(LongArray dst, long[]... arrs) {
        int sumSize = 0;
        for (long[] arr : arrs) {
            sumSize += arr.length;
        }
        dst.ensureCapacity(sumSize);
        for (long[] arr : arrs) {
            dst.addAll(arr);
        }
    }
}
