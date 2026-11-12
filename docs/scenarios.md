# iPass → Pharos scenarios

## Payload rules

One event contains one employee and one account in an environment. USER_PROVISIONED and USER_UPDATED include all profile fields, manager.displayName, employeeClass, account and the complete iPassRoles list. The role source key is entitlementValue, not roleId. Each role includes entitlementType, roleName, displayName, description, the three boolean flags, owners, assignedAt and PROVISIONED status.

Account ID may differ across environments. Owner identity is separate from the receiving employee. Every owner has ownerId, firstName, lastName and email. [] explicitly clears the shared owner list; missing/null is invalid. Original employee and manager fields remain included.

## Lifecycle

| Scenario | Call | Database result | Full payload |
| --- | --- | --- | --- |
| Hire; non-Pharos or pending role requests | None | No Pharos changes | — |
| First approved Pharos access | USER_PROVISIONED | Create/update employee, account, roles, owners and account assignments | [01](examples/01-first-provisioning.json) |
| Remove GSI | USER_UPDATED | Close omitted GSI assignment for this account only; retain shared role/owners | [02](examples/02-remove-gsi.json) |
| Add GDD | USER_UPDATED | Retain existing assignments and original times; add GDD and owners | [03](examples/03-add-gdd.json) |
| Employment termination | USER_TERMINATED | Mark employee TERMINATED and close all account assignments in the deployment | [04](examples/04-termination.json) |
| Correct wrongful termination | Approved USER_PROVISIONED | Clear termination date; keep original hire date; create new assignment periods for the specified account only | [05](examples/05-termination-correction.json) |
| Genuine rehire | Approved USER_PROVISIONED | Update employment details and provision only newly approved roles | [06](examples/06-genuine-rehire.json) |
| Remove last role | USER_UPDATED with iPassRoles [] | Keep employee ACTIVE; close assignments for supplied account only | [07](examples/07-remove-all-roles.json) |
| Profile changes | USER_UPDATED | Update profile and retain the complete current account role list | [08](examples/08-profile-update.json) |
| Access after all roles were removed | USER_UPDATED | Create new assignment periods; keep closed history | [09](examples/09-access-after-last-role-removal.json) |
| VNDLY contractor | USER_PROVISIONED | Same account/assignment model | [10](examples/10-contractor-provisioning.json) |
| Identical retry | Same UUID and unchanged payload | Return SUCCESS; no repeated changes | [11](examples/11-identical-retry.json) |
| Second account | USER_UPDATED with another accountId | Add/update that account; leave first account's roles intact | [12](examples/12-second-account.json) |
| Owner list changes | USER_UPDATED with complete roles and current owners | Update shared owners; do not change continuing assignment times | [13](examples/13-role-owners-update.json) |

## Processing

Validate → record/find UUID → serialize employee changes → save profile/account/catalog/owners → reconcile supplied account assignments → commit with APPLIED status. Failure rolls back business changes and records FAILED separately. Identical retries of APPLIED events return SUCCESS without repeated changes. Different content under the same UUID returns 409.

The same account cannot be assigned to another employee. Missing roles, malformed booleans and duplicate entitlement/owner IDs are rejected. Role names must match the environment. Owners need not have employee records. A missing manager blocks provisioning/updates, never minimal termination.

## Termination and delivery

Send termination immediately when effective; do not wait for iPass's 15-day membership cleanup. Retained memberships do not reactivate a terminated employee. USER_UPDATED cannot reactivate; USER_PROVISIONED must explicitly confirm approved access. Only the supplied account's approved roles are restored.

iPass reviews failed deliveries and may require manual retriggering; automatic retry is not a confirmed capability. Identical retrigger: same event UUID/payload. New or corrected snapshot: new UUID. Ordered employee delivery, including manual retriggers, must be agreed; no eventVersion or timestamp-based stale-event rejection is implemented.

This module stores ingestion state. Granular permission editing and external session revocation are separate integrations.

Assignment removed_at is the time Pharos closed access, including termination; it is not confirmation of iPass membership removal.
