# Publish in Confluence Cloud

The interactive API reference is rendered inside Confluence from a page attachment. No hosted Swagger page or running Pharos application is needed for browsing.

## Files

- `README.md`: concise handoff content.
- `confluence-page.storage.xhtml`: Atlassian storage body with Expand and Code macros; an insertion point for the installed OpenAPI macro.
- `pharos-ipass.openapi.json`: self-contained POST-only contract to attach to the page. Schemas and examples match the application contract; its server is a deliberately non-resolving `.invalid` address for documentation review.

## Publish

1. Create the page from the README, or use the XHTML with an approved publisher supporting Confluence `storage` format. Raw XHTML cannot be pasted into the normal editor.
2. Attach `pharos-ipass.openapi.json` to the page.
3. Insert your installed OpenAPI/Swagger app macro under **Interactive API reference** and select this page attachment as its source. Use its attachment or pasted-spec mode, not a URL to the running application.
4. Disable request execution (“Try it out”) in the macro settings. Verify that readers can expand the POST endpoint, inspect schemas and select examples. Remove the publisher setup paragraph.
5. Keep the endpoint placeholder until the shared API address is agreed. Authentication, retry limits and termination-failure escalation also remain integration agreements.

## Required Confluence capability

Interactive OpenAPI rendering requires an installed compatible app; a standard Code macro only displays JSON. For example, [OpenAPI Viewer for Confluence](https://marketplace.atlassian.com/apps/2956960728/openapi-viewer-for-confluence) lists support for page attachments and pasted specifications. Use an organization-approved app already available on your site where possible.

Macro names, storage syntax and request-execution settings depend on the installed app. The page deliberately leaves the macro insertion point unconfigured until that app is known. If no compatible app is installed, a Confluence administrator must enable one to meet the interactive requirement.

The contract can be reviewed without API access. Actual API calls require a shared endpoint and authentication. The macro has not yet been configured or tested in your Confluence instance.

## Updating the contract

Extract only `POST /api/v1/ipass/events` and its referenced components from `../../src/main/resources/static/openapi/ipass.json` when the contract changes, retaining the documentation-only server until API execution is enabled deliberately. Replace the same Confluence attachment to update the documentation.

## Check the rendered schema

Replace the existing `pharos-ipass.openapi.json` attachment with the updated file and refresh the macro. Confirm the macro references this POST-only attachment.

The request schema has three alternatives selected by `eventType`: USER_PROVISIONED, USER_UPDATED and USER_TERMINATED. Some renderers keep the example dropdown and schema selector separate; selecting an example may not switch the schema tab automatically. Select the matching event schema when comparing them.

Provisioning requires at least one role. Updates allow an empty role list. Termination shows only employeeId, employmentStatus and optional terminationDate under user. Responses are 200, 400 and 500.

The current profile uses flat `user.oktaAccountId`, `employeeClass`, `companyName`, `businessUnit`, `city`, separate manager names, `jobDetails` and optional `supplier`. Replace the existing attachment and page body together after contract changes. Internal GET endpoints and database diagrams are maintained in the Pharos documentation, outside the iPass handoff.
