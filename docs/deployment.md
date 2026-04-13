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

## AI Parameter Resolution

When `tempagent.ai.enabled=true`, bindings can declare parameter schemas so the AI resolves
concrete values from user intent. To enable this:

1. Register bindings with `parameterSchemas`:

```java
registry.register(new ApiBinding(
    "salesData",
    "http://api.internal/sales",
    "GET",
    "items",
    Map.of("apiKey", "fixed-key"),     // static params always included
    List.of(
        new ParamSchema("month", ParamType.DATE,
            "Report month in yyyy-MM format, e.g. 2026-03", true, null, List.of()),
        new ParamSchema("format", ParamType.ENUM,
            "Output format", false, "summary", List.of("summary", "detail"))
    )
));
```

2. Include `userIntent` in the render request:

```json
{
  "templateContent": "<h1>{{reportTitle}}</h1>...",
  "format": "HTML",
  "userIntent": "generate the March 2026 sales report in detail format"
}
```

3. The AI resolves parameter values from the user intent and declared schemas.
   Static `requestParams` are merged as defaults; AI-resolved values take precedence.

When `userIntent` is absent or `tempagent.ai.enabled=false`, the pipeline uses only static
`requestParams` — behavior is identical to pre-enhancement.

## Observability

Exposed metrics:

- `tempagent.ingestion.requests`
- `tempagent.orchestration.calls`
- `tempagent.render.requests`
- `tempagent.export.requests`
- `tempagent.pipeline.duration`
- `tempagent.paramresolution.calls`
- `tempagent.paramresolution.failures`
- `tempagent.paramresolution.duration`

Actuator exposure is configured in `application.yaml` for `health`, `info`, and `metrics`.

## Rollback Strategy

1. Roll back to previous application artifact.
2. Restore previous template binding registry entries if changed.
3. Re-run smoke tests on representative templates.
4. Confirm metrics and logs return to baseline.
