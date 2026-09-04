package app.grapheneos.goscompat.contactscopes.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceParameterizedRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;

import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RunWith(DeviceParameterizedRunner.class)
public final class ContactScopesHostTest extends BaseHostJUnit4Test {
    private static final String SDK_36_TEST_PACKAGE =
            "app.grapheneos.goscompat.contactscopes.sdk_36";
    private static final String[] TEST_PACKAGES = {
        SDK_36_TEST_PACKAGE,
        "app.grapheneos.goscompat.contactscopes.sdk_37",
    };
    private static final String TEST_CLASS =
            "app.grapheneos.goscompat.contactscopes.ContactScopesDeviceTest";
    private static final String WRITER_PACKAGE =
            "app.grapheneos.goscompat.contactscopes.writer";
    private static final String WRITER_TEST_CLASS =
            "app.grapheneos.goscompat.contactscopes.writer.ContactScopesWriterDeviceTest";
    private static final String SCOPED_CONTACT_ID_METRIC = "scoped_contact_id";

    @After
    public void tearDown() throws Exception {
        int userId = getDevice().getCurrentUser();
        try {
            for (String packageName : TEST_PACKAGES) {
                resetContactScopes(packageName, userId);
            }
        } finally {
            deleteTestContacts(userId);
        }
    }

    @Test
    @Parameters(method = "targetPackages")
    @TestCaseName("{method}[{index}]")
    public void countProjectionAllowsSelectionColumns(String packageName) throws Exception {
        runDeviceTest(packageName, "countProjectionAllowsSelectionColumns");
    }

    @Test
    @Parameters(method = "targetPackages")
    @TestCaseName("{method}[{index}]")
    public void trailingLimitInSortOrderIsAccepted(String packageName) throws Exception {
        runDeviceTest(packageName, "trailingLimitInSortOrderIsAccepted");
    }

    @Test
    public void accountTypeProjectionReturnsOnlyScopedContactForSdk36() throws Exception {
        runDeviceTest(SDK_36_TEST_PACKAGE, "accountTypeProjectionReturnsOnlyScopedContact");
    }

    public List<String> targetPackages() {
        return Arrays.asList(TEST_PACKAGES);
    }

    private void runDeviceTest(String packageName, String methodName) throws Exception {
        int userId = getDevice().getCurrentUser();
        long contactId = createTestContacts(userId);
        enableContactScopes(packageName, userId, serializeContactScope(contactId));

        DeviceTestRunOptions options =
                new DeviceTestRunOptions(packageName)
                        .setTestClassName(TEST_CLASS)
                        .setTestMethodName(methodName)
                        .addInstrumentationArg("scopedContactId", Long.toString(contactId))
                        .setUserId(userId);
        runDeviceTests(options);
    }

    private void enableContactScopes(String packageName, int userId, String contactScope)
            throws Exception {
        resetContactScopes(packageName, userId);
        shell(
                "pm revoke --user "
                        + userId
                        + " "
                        + packageName
                        + " android.permission.READ_CONTACTS");
        shell(
                "pm edit-gos-package-state "
                        + packageName
                        + " "
                        + userId
                        + " add-flag CONTACT_SCOPES_ENABLED"
                        + " set-contact-scopes "
                        + contactScope
                        + " set-kill-uid-after-apply true update-permission-state");
    }

    private long createTestContacts(int userId) throws Exception {
        grantWriterPermissions(userId);
        try {
            runWriterDeviceTest("createContacts", userId);

            Metric metric =
                    getLastDeviceRunResults().getRunProtoMetrics().get(SCOPED_CONTACT_ID_METRIC);
            assertNotNull(SCOPED_CONTACT_ID_METRIC, metric);
            return Long.parseLong(metric.getMeasurements().getSingleString());
        } finally {
            revokeWriterPermissions(userId);
        }
    }

    private void deleteTestContacts(int userId) throws Exception {
        try {
            grantWriterPermissions(userId);
            runWriterDeviceTest("deleteContacts", userId);
        } finally {
            revokeWriterPermissions(userId);
        }
    }

    private void grantWriterPermissions(int userId) throws Exception {
        shell(
                "pm grant --user "
                        + userId
                        + " "
                        + WRITER_PACKAGE
                        + " android.permission.READ_CONTACTS");
        shell(
                "pm grant --user "
                        + userId
                        + " "
                        + WRITER_PACKAGE
                        + " android.permission.WRITE_CONTACTS");
    }

    private void revokeWriterPermissions(int userId) throws Exception {
        shell(
                "pm revoke --user "
                        + userId
                        + " "
                        + WRITER_PACKAGE
                        + " android.permission.READ_CONTACTS");
        shell(
                "pm revoke --user "
                        + userId
                        + " "
                        + WRITER_PACKAGE
                        + " android.permission.WRITE_CONTACTS");
    }

    private void runWriterDeviceTest(String methodName, int userId) throws Exception {
        DeviceTestRunOptions options =
                new DeviceTestRunOptions(WRITER_PACKAGE)
                        .setTestClassName(WRITER_TEST_CLASS)
                        .setTestMethodName(methodName)
                        .setUserId(userId);
        runDeviceTests(options);
    }

    private static String serializeContactScope(long contactId) {
        // ContactScopesStorage v0, TYPE_CONTACT, one ID, followed by the contact ID.
        return String.format(Locale.ROOT, "000100000001%016x", contactId);
    }

    private void resetContactScopes(String packageName, int userId) throws Exception {
        shell(
                "pm edit-gos-package-state "
                        + packageName
                        + " "
                        + userId
                        + " clear-flag CONTACT_SCOPES_ENABLED set-contact-scopes null"
                        + " set-kill-uid-after-apply true update-permission-state");
    }

    private void shell(String command) throws Exception {
        CommandResult result = getDevice().executeShellV2Command(command);
        assertEquals(result.toString(), 0L, (long) result.getExitCode());
    }
}
