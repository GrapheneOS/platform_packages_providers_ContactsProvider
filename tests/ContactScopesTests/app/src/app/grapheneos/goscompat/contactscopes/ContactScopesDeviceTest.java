package app.grapheneos.goscompat.contactscopes;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

public final class ContactScopesDeviceTest {
    private ContentResolver mResolver;
    private long mScopedContactId;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mResolver = context.getContentResolver();
        Bundle arguments = InstrumentationRegistry.getArguments();
        mScopedContactId = Long.parseLong(arguments.getString("scopedContactId"));
    }

    @Test
    public void countProjectionAllowsSelectionColumns() {
        try (Cursor cursor =
                mResolver.query(
                        Data.CONTENT_URI,
                        new String[] {BaseColumns._COUNT},
                        Data.MIMETYPE + " = ? AND " + Data.DATA1 + " = ?",
                        new String[] {Phone.CONTENT_ITEM_TYPE, TestContactData.SCOPED_NUMBER},
                        null)) {
            assertThat(cursor).isNotNull();
            assertThat(cursor.getCount()).isEqualTo(1);
            assertThat(cursor.moveToFirst()).isTrue();
            int countColumn = cursor.getColumnIndexOrThrow(BaseColumns._COUNT);
            assertThat(cursor.getInt(countColumn)).isEqualTo(1);
        }
    }

    @Test
    public void trailingLimitInSortOrderIsAccepted() {
        try (Cursor cursor =
                mResolver.query(
                        Data.CONTENT_URI,
                        new String[] {Data._ID, Data.CONTACT_ID, Data.RAW_CONTACT_ID},
                        null,
                        null,
                        Data.CONTACT_ID + " ASC, " + Data.RAW_CONTACT_ID + " ASC LIMIT 500")) {
            assertThat(cursor).isNotNull();
            assertThat(cursor.getCount()).isEqualTo(2);
            while (cursor.moveToNext()) {
                assertThat(cursor.getLong(cursor.getColumnIndexOrThrow(Data.CONTACT_ID)))
                        .isEqualTo(mScopedContactId);
            }
        }
    }

    @Test
    public void accountTypeProjectionReturnsOnlyScopedContact() {
        try (Cursor cursor =
                mResolver.query(
                        Phone.CONTENT_URI,
                        new String[] {
                            Data.RAW_CONTACT_ID,
                            Phone.DISPLAY_NAME,
                            Phone.NUMBER,
                            Phone.TYPE,
                            Phone.LABEL,
                            Phone.SORT_KEY_PRIMARY,
                            RawContacts.ACCOUNT_TYPE,
                            Phone.STARRED,
                        },
                        "("
                                + RawContacts.ACCOUNT_TYPE
                                + " IS NULL OR ("
                                + RawContacts.ACCOUNT_TYPE
                                + " NOT IN (?, ?)))",
                        new String[] {"test.account.type", "test.business.account.type"},
                        null)) {
            assertThat(cursor).isNotNull();
            assertThat(cursor.getCount()).isEqualTo(1);
            assertThat(cursor.moveToFirst()).isTrue();
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)))
                    .isEqualTo(TestContactData.SCOPED_NAME);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)))
                    .isEqualTo(TestContactData.SCOPED_NUMBER);
            assertThat(cursor.isNull(cursor.getColumnIndexOrThrow(RawContacts.ACCOUNT_TYPE)))
                    .isTrue();
        }
    }
}
