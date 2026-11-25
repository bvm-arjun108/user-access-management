# iPass → Pharos scenarios

## Payload rules

One event identifies one employee. Provisioning and updates also identify one source account. USER_PROVISIONED and USER_UPDATED include the full employee profile, employeeClass, flat oktaAccountId, manager, jobDetails and the complete iPassRoles list. email and supplier are optional and nullable. The role source key is entitlementValue, not roleId. Each role includes entitlementType, roleName, displayName, roleDescription, the three boolean flags, assignedAt and PROVISIONED status.


Examples: [iPass POST contract](ipass-handoff/pharos-ipass.openapi.json). Extended executable scenarios: [Postman collection](../postman/Pharos-UAM.postman_collection.json).

## Lifecycle

| Scenario | Call | Database result |
| --- | --- | --- |
| Hire; non-Pharos or pending role requests | None | No Pharos changes |
| First approved Pharos access | USER_PROVISIONED | Create/update employee, account, roles and account assignments |
| Remove GSI | USER_UPDATED | Close omitted GSI assignment for this account only; retain shared role |
| Add GDD | USER_UPDATED | Retain existing assignments and original times; add GDD |
| Employment termination | USER_TERMINATED | Mark employee TERMINATED and close all account assignments in this database |
| Correct wrongful termination | Approved USER_PROVISIONED | Clear termination date; keep original hire date; create new assignment periods for the specified account only |
| Genuine rehire | Approved USER_PROVISIONED | Update employment details and provision only newly approved roles |
| Remove last role | USER_UPDATED with iPassRoles [] | Keep employee ACTIVE; close assignments for supplied account only |
| Profile changes | USER_UPDATED | Update profile and retain the complete current account role list |
| Access after all roles were removed | USER_UPDATED | Create new assignment periods; keep closed history |
| VNDLY contractor | USER_PROVISIONED | Same account/assignment model |
| Identical retry | Same UUID and unchanged payload | Return SUCCESS; no repeated changes |
| Second account | USER_UPDATED with another oktaAccountId | Add/update that account; leave first account's roles intact |
| Role metadata changes | USER_UPDATED with complete roles | Update shared role flags and descriptions; retain assignment times |

## Processing

Validate → record/find UUID → serialize employee changes → save profile/account/catalog → reconcile supplied account assignments → commit with APPLIED status. Failure rolls back business changes and records FAILED separately. Identical retries of APPLIED events return SUCCESS without repeated changes. Different content under the same UUID returns 400.

The same account cannot be assigned to another employee. Missing roles, malformed booleans and duplicate entitlement IDs are rejected. Role names are stored as supplied; no environment is inferred. A missing manager blocks provisioning/updates, never minimal termination.

## Termination and delivery

Send termination immediately when effective; do not wait for iPass's 15-day membership cleanup. Retained memberships do not reactivate a terminated employee. USER_UPDATED cannot reactivate; USER_PROVISIONED must explicitly confirm approved access. Only the supplied account's approved roles are restored.

iPass reviews failed deliveries and may require manual retriggering; automatic retry is not a confirmed capability. Identical retrigger: same event UUID/payload. New or corrected snapshot: new UUID. Ordered employee delivery, including manual retriggers, must be agreed; no eventVersion or timestamp-based stale-event rejection is implemented.

This module stores ingestion state. Granular permission editing and external session revocation are separate integrations.

Assignment removed_at is the time Pharos closed access, including termination; it is not confirmation of iPass membership removal.

Source Owner ID/First/Last describe the employee, not role owners. Manager uses employeeId, firstName, middleName and lastName; middleName may be null. Employee email may be null or omitted.

Two distinct Okta IDs under the same Owner ID remain two account records. Their role lists are independent, even when environment cannot be determined.
