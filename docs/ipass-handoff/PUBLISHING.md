# Publish the iPass handoff page

- `README.md`: readable handoff page.
- `confluence-page.storage.xhtml`: the same page in Atlassian storage format, with native Expand and Code macros and a Swagger iFrame template.

## Before publishing

1. Replace `{{PHAROS_BASE_URL}}` in both files with the agreed shared HTTPS base URL, without a trailing slash. Local preview: `http://localhost:8086/swagger-ui.html`.
2. Confirm authentication, retry limits and the termination-failure escalation contact with iPass. These are not configured by this document.
3. Check Swagger's server selection. The current OpenAPI file targets `http://localhost:8086`; configure the shared API server URL in the published contract before allowing shared “Try it out” usage.

## Confluence Cloud

Use the XHTML as a page body with representation `storage` through your approved Confluence publishing tool or a storage-source editor. Do not paste raw XHTML into the normal rich-text editor. Alternatively, copy the readable page and add Expand, Code and iFrame macros through the editor.

This page targets Confluence Cloud. In the page editor, insert the iFrame macro and set its URL to the shared Swagger URL, height to 900 pixels and scrolling to auto. The storage file includes this macro as a template. Retain the direct Swagger link if embedding is unavailable.

The Swagger host must be reachable by readers and permit framing by your Confluence site. A Markdown file alone cannot run interactive Swagger. Confluence Cloud is confirmed. The embed has not been tested in your instance; the shared Swagger URL is still required.

Authoritative references: [Atlassian iFrame macro](https://support.atlassian.com/confluence-cloud/docs/insert-the-iframe-macro/) and [iframe deployment requirements](https://support.atlassian.com/confluence/kb/how-to-put-an-iframe-into-confluence/).

## Contract and test collection

- [OpenAPI contract](../../src/main/resources/static/openapi/ipass.json)
- [Postman collection](../../postman/Pharos-UAM.postman_collection.json)

Keep these linked to the reviewed repository version; avoid maintaining a second schema inside the page.
