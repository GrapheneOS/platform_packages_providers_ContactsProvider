package com.android.providers.contacts;

import android.content.pm.GosPackageState;
import android.content.res.Resources;
import android.database.Cursor;
import android.ext.cscopes.ContactScope;
import android.ext.cscopes.ContactScopesApi;
import android.ext.cscopes.ContactScopesStorage;
import android.ext.cscopes.ContactsGroup;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;

import java.util.ArrayList;

import static android.ext.cscopes.ContactScope.TYPE_CONTACT;
import static android.ext.cscopes.ContactScope.TYPE_EMAIL;
import static android.ext.cscopes.ContactScope.TYPE_GROUP;
import static android.ext.cscopes.ContactScope.TYPE_NUMBER;
import static com.android.providers.contacts.ScopedContactsProvider.getUri;

// Helper methods for simplifying and speeding up implementation of Contact Scopes UI in
// PermissionController
class ContactScopesUiHelper {

    static Bundle getAppScopesViewModel(ScopedContactsProvider scp, String packageName) {
        ContactScopesStorage scopes = ContactScopesStorage
                .deserialize(GosPackageState.getOrDefault(packageName));

        var result = new Bundle();

        Resources res = scp.requireContext().getResources();

        for (int type = 0; type < ContactScope.TYPE_COUNT; ++type) {
            long[] ids = scopes.getIds(type);

            var list = new ArrayList<ContactScope>(ids.length);

            for (long id : ids) {
                String title = null;
                String summary = null;
                Uri detailsUri = null;

                switch (type) {
                    case TYPE_GROUP: {
                        ContactsGroup groupInfo = getGroupInfo(scp, id);
                        if (groupInfo != null) {
                            title = groupInfo.title;
                            summary = groupInfo.summary;
                            detailsUri = getUri(ContactsContract.Groups.CONTENT_URI, id);
                        }
                        break;
                    }
                    case TYPE_CONTACT: {
                        title = getContactDisplayName(scp, id);
                        detailsUri = getUri(ContactsContract.Contacts.CONTENT_URI, id);
                        break;
                    }
                    case TYPE_NUMBER:
                    case TYPE_EMAIL: {
                        String[] dataColumns = getDataColumns(scp, id);
                        if (dataColumns != null) {
                            long rawContactId = Long.parseLong(
                                    dataColumns[DATA_COLUMN_IDX_RAW_CONTACT_ID]);
                            title = getDisplayName(scp, rawContactId);
                            detailsUri = getUri(
                                    ContactsContract.RawContacts.CONTENT_URI, rawContactId);
                            int dataSubtype = Integer.parseInt(
                                    dataColumns[DATA_COLUMN_IDX_DATA_2]);
                            int dataSubtypeRes;
                            switch (type) {
                                case TYPE_NUMBER:
                                    dataSubtypeRes = ContactsContract.CommonDataKinds.Phone
                                            .getTypeLabelResource(dataSubtype);
                                    break;
                                case TYPE_EMAIL:
                                    dataSubtypeRes = ContactsContract.CommonDataKinds.Email
                                            .getTypeLabelResource(dataSubtype);
                                    break;
                                default:
                                    throw new IllegalStateException();
                            }
                            summary = res.getString(dataSubtypeRes) + ": "
                                    + dataColumns[DATA_COLUMN_IDX_DATA_1];
                        }
                        break;
                    }
                    default: throw new IllegalStateException();
                }

                list.add(new ContactScope(type, id, title, summary, detailsUri));
            }

            if (!list.isEmpty()) {
                result.putParcelableArrayList(Integer.toString(type), list);
            }
        }

        return result;
    }

    static String getDisplayName(ScopedContactsProvider scp, long rawContactId) {
        Uri uri = getUri(ContactsContract.RawContacts.CONTENT_URI, rawContactId);
        String[] displayNameProjection = { ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY };

        try (Cursor c = scp.provider.query(uri, displayNameProjection, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        }
        return null;
    }

    static String getContactDisplayName(ScopedContactsProvider scp, long contactId) {
        Uri uri = getUri(ContactsContract.Contacts.CONTENT_URI, contactId);
        String[] proj = { ContactsContract.Contacts.NAME_RAW_CONTACT_ID };

        try (Cursor c = scp.provider.query(uri, proj, null, null)) {
            if (c != null && c.moveToFirst()) {
                long nameRawContactId = c.getLong(0);
                return getDisplayName(scp, nameRawContactId);
            }
        }
        return null;
    }

    private static final int DATA_COLUMN_IDX_RAW_CONTACT_ID = 0;
    private static final int DATA_COLUMN_IDX_DATA_1 = 1;
    private static final int DATA_COLUMN_IDX_DATA_2 = 2;

    private static String[] getDataColumns(ScopedContactsProvider scp, long dataId) {
        Uri uri = getUri(ContactsContract.Data.CONTENT_URI, dataId);

        String[] dataTableProjection = { ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.DATA1, ContactsContract.Data.DATA2 };

        try (Cursor c = scp.provider.query(uri, dataTableProjection, null, null)) {
            if (c != null && c.moveToFirst()) {
                int l = dataTableProjection.length;
                String[] arr = new String[l];
                for (int i = 0; i < l; ++i) {
                    arr[i] = c.getString(i);
                }
                return arr;
            }
        }

        return null;
    }

    static Bundle getIdsFromUris(ScopedContactsProvider scp, Uri[] uris) {
        String[] proj = { BaseColumns._ID };
        final int len = uris.length;
        long[] ids = new long[len];
        for (int i = 0; i < len; ++i) {
            try (Cursor c = scp.provider.query(uris[i], proj, null, null)) {
                if (c != null && c.moveToFirst()) {
                    ids[i] = c.getLong(0);
                } else {
                    return null;
                }
            }
        }
        var res = new Bundle();
        res.putLongArray(ContactScopesApi.KEY_IDS, ids);
        return res;
    }

    private static final String[] GROUP_PROJECTION = { BaseColumns._ID,
            ContactsContract.Groups.RES_PACKAGE, ContactsContract.Groups.TITLE_RES,
            ContactsContract.Groups.TITLE, ContactsContract.Groups.ACCOUNT_NAME, ContactsContract.Groups.SUMMARY_COUNT };

    private static ContactsGroup getGroupInfo(ScopedContactsProvider scp, Cursor c) {
        long id = c.getLong(0);
        String resPackage = c.getString(1);
        String titleResStr = c.getString(2);
        String title = null;
        if (resPackage != null && titleResStr != null) {
            int titleRes = Integer.parseInt(titleResStr);
            var pm = scp.requireContext().getPackageManager();
            CharSequence s = pm.getText(resPackage, titleRes, null);
            if (s != null) {
                title = s.toString();
            }
        }
        if (title == null) {
            title = c.getString(3);
        }
        String accountName = c.getString(4);
        return new ContactsGroup(id, title, accountName);
    }

    static Bundle getGroups(ScopedContactsProvider scp) {
        Uri uri = ContactsContract.Groups.CONTENT_SUMMARY_URI;
        ArrayList<ContactsGroup> list;

        // selection and sortOrder are copied from AOSP Contacts app,
        // com.android.contacts.GroupListLoader
        String selection = ContactsContract.Groups.DELETED + "=0 AND "
                + ContactsContract.Groups.AUTO_ADD + "=0 AND "
                + ContactsContract.Groups.FAVORITES + "=0 AND "
                + ContactsContract.Groups.GROUP_IS_READ_ONLY + "=0";
        String sortOrder = ContactsContract.Groups.TITLE + " COLLATE LOCALIZED ASC";

        try (Cursor c = scp.provider.query(uri, GROUP_PROJECTION, selection, null, sortOrder)) {
            if (c == null) {
                return null;
            }
            list = new ArrayList<>(c.getCount());
            while (c.moveToNext()) {
                list.add(getGroupInfo(scp, c));
            }
        }
        var res = new Bundle();
        res.putParcelableArrayList(ContactScopesApi.KEY_RESULT, list);
        return res;
    }

    static ContactsGroup getGroupInfo(ScopedContactsProvider scp, long groupId) {
        Uri uri = ContactsContract.Groups.CONTENT_SUMMARY_URI;

        String sel = BaseColumns._ID + '=' + groupId +
                " AND " + ContactsContract.Groups.DELETED + "=0";

        try (Cursor c = scp.provider.query(uri, GROUP_PROJECTION, sel, null, null)) {
            if (c != null && c.getCount() == 1 && c.moveToFirst()) {
                return getGroupInfo(scp, c);
            }
        }
        return null;
    }
}
