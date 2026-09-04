package app.grapheneos.goscompat.contactscopes;

public final class TestContactData {
    // Keep the names dissimilar so contact aggregation creates two distinct contacts.
    public static final String SCOPED_NAME = "Scoped Test Contact";
    public static final String OTHER_NAME = "Unrelated Control Person";

    // Match fake phone numbers used by
    // cts/tests/tests/contactsprovider/src/android/provider/cts/contacts/
    // ContactsContract_DataUsageTest.java.
    public static final String SCOPED_NUMBER = "555-5555";
    public static final String OTHER_NUMBER = "555-5554";

    private TestContactData() {}
}
