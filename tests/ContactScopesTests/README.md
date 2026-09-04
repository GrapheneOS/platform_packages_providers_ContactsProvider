# ContactScopesTests

These tests exercise Contacts Provider queries through Contact Scopes while the helper apps do not
hold `READ_CONTACTS`.

The same device test source is packaged into helper APKs targeting different SDK levels. Each host
test enables Contact Scopes for the applicable helper package and delegates to one device test so
Tradefed reports the query and target SDK combination separately.

Each host test uses a separate writer helper to create two local contacts, scopes one of them to the
query helper, and removes both contacts during teardown. This lets device tests verify the returned
data belongs to the selected contact without giving the query helper write access.

To add a query regression, add a device-side `@Test` and a small host-side delegate. Parameterize
the host test with `targetPackages` when the behavior applies to every helper package; otherwise run
the device test only from the applicable package. To add a target SDK variant, add another helper
app stanza in `app/Android.bp`, then list its APK in `AndroidTest.xml`, the host package list, and
`device_common_data`.

Run the suite through its TEST_MAPPING entry from the checkout root:

```
atest --test-mapping packages/providers/ContactsProvider:gos-postsubmit
```
