package app.grapheneos.goscompat.contactscopes.writer;

import static com.google.common.truth.Truth.assertThat;

import app.grapheneos.goscompat.contactscopes.TestContactData;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public final class ContactScopesWriterDeviceTest {
    private static final String SCOPED_CONTACT_ID_METRIC = "scoped_contact_id";
    private static final String PREFERENCES = "created_contacts";
    private static final String SCOPED_RAW_CONTACT_ID = "scoped_raw_contact_id";
    private static final String OTHER_RAW_CONTACT_ID = "other_raw_contact_id";

    private ContentResolver mResolver;
    private SharedPreferences mPreferences;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mResolver = context.getContentResolver();
        mPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    @Test
    public void createContacts() throws Exception {
        deleteStoredContacts();

        long scopedContactId =
                createContact(
                        SCOPED_RAW_CONTACT_ID,
                        TestContactData.SCOPED_NAME,
                        TestContactData.SCOPED_NUMBER);
        long otherContactId =
                createContact(
                        OTHER_RAW_CONTACT_ID,
                        TestContactData.OTHER_NAME,
                        TestContactData.OTHER_NUMBER);
        assertThat(scopedContactId).isNotEqualTo(otherContactId);

        Bundle results = new Bundle();
        results.putString(SCOPED_CONTACT_ID_METRIC, Long.toString(scopedContactId));
        InstrumentationRegistry.getInstrumentation().addResults(results);
    }

    @Test
    public void deleteContacts() throws Exception {
        deleteStoredContacts();
    }

    private long createContact(String preferenceKey, String displayName, String phoneNumber)
            throws Exception {
        // Follow the local raw contact setup and cleanup pattern used by
        // cts/tests/tests/contactsprovider/strictsqlcheck/src/android/provider/cts/contacts/
        // strictsqlcheck/ContactsContractStrictSqlCheckTest.java.
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        int rawContactOperation = operations.size();
        operations.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_TYPE, null)
                        .withValue(RawContacts.ACCOUNT_NAME, null)
                        .build());
        operations.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOperation)
                        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.DISPLAY_NAME, displayName)
                        .build());
        operations.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOperation)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, phoneNumber)
                        .build());

        ContentProviderResult[] results =
                mResolver.applyBatch(ContactsContract.AUTHORITY, operations);
        assertThat(results[rawContactOperation].uri).isNotNull();
        long rawContactId = ContentUris.parseId(results[rawContactOperation].uri);
        assertThat(mPreferences.edit().putLong(preferenceKey, rawContactId).commit()).isTrue();

        try (Cursor cursor =
                mResolver.query(
                        RawContacts.CONTENT_URI,
                        new String[] {RawContacts.CONTACT_ID},
                        RawContacts._ID + " = ?",
                        new String[] {Long.toString(rawContactId)},
                        null)) {
            assertThat(cursor).isNotNull();
            assertThat(cursor.moveToFirst()).isTrue();
            return cursor.getLong(cursor.getColumnIndexOrThrow(RawContacts.CONTACT_ID));
        }
    }

    private void deleteStoredContacts() throws Exception {
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        addDeleteOperation(operations, SCOPED_RAW_CONTACT_ID);
        addDeleteOperation(operations, OTHER_RAW_CONTACT_ID);
        if (!operations.isEmpty()) {
            mResolver.applyBatch(ContactsContract.AUTHORITY, operations);
        }
        assertThat(mPreferences.edit().clear().commit()).isTrue();
    }

    private void addDeleteOperation(
            ArrayList<ContentProviderOperation> operations, String preferenceKey) {
        long rawContactId = mPreferences.getLong(preferenceKey, -1);
        if (rawContactId >= 0) {
            operations.add(
                    ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                            .withSelection(
                                    RawContacts._ID + " = ?",
                                    new String[] {Long.toString(rawContactId)})
                            .build());
        }
    }
}
