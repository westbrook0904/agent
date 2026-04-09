# TempAgent Deployment Notes

## Supported Template Subset (v1)

- Scalar placeholders: `{{customer.name}}`
- Collection blocks: `{{#each orders}} ... {{/each}}`
- Text and HTML table content rendered as canonical text-based model
- Output formats: `HTML`, `EML`

## Operational Constraints

- API endpoints referenced by placeholder bindings must be reachable from deployment environment
- Placeholder bindings must be explicitly registered in `BindingRegistry`
- Missing value behavior is controlled by `tempagent.template.missing-value-policy`
- Native Outlook `MSG/OFT` export is out of v1 scope

## Observability

Exposed metrics:

- `tempagent.ingestion.requests`
- `tempagent.orchestration.calls`
- `tempagent.render.requests`
- `tempagent.export.requests`
- `tempagent.pipeline.duration`

Actuator exposure is configured in `application.yaml` for `health`, `info`, and `metrics`.

## Rollback Strategy

1. Roll back to previous application artifact.
2. Restore previous template binding registry entries if changed.
3. Re-run smoke tests on representative templates.
4. Confirm metrics and logs return to baseline.
