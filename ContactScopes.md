For an introduction, read https://developer.android.com/guide/topics/providers/contacts-provider

The Contact Scopes feature enables the user to grant an app read access to a subset of data in the 
OS ContactsProvider. Granting write access (insert(), update(), delete()) is not supported.

Read access can be granted to the following scopes:
- Contact data (phone number or email). Access to each type of number and email in a contact is
granted separately. Access to contact name is granted automatically.
- Single contact. Access is granted to all contact data, except photo, originating account type and
name, contact sync data.
- Contact group ("label"). Equivalent to granting access to all contacts in the group. 
Any contact can be in any number of contact groups.

Type and name of account that contact is stored in is fully hidden from the app. Name of contact
account is usually the same as email address of that account.

Access to SIM phonebook data is fully stubbed out: an empty and immutable content provider is 
exposed instead of the actual SIM phonebook.
Implementations of legacy (ICC) and modern SIM stub phonebook providers are added to 
packages/services/Telephony.

The Contact Scopes feature is enabled per-app. When it's enabled, app's calls to contacts provider
and to SIM phonebook providers are redirected, respectively, to ScopedContactsProvider and to 
stub SIM phonebook providers. 
Implementation of redirection of ContentProvider calls is in frameworks/base:
com.android.internal.app.{ContentProviderRedirector,RedirectedContentProvider}.
