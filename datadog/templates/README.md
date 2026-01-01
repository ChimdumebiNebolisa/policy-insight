# Datadog Asset Templates

This directory contains **templates** for Datadog dashboards, monitors, and SLOs. These are NOT real exports from Datadog—they are templates that can be used to create or update assets in Datadog.

## Usage

### To create/update assets from templates:

Use the `apply-assets.py` script:

```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
python scripts/datadog/apply-assets.py
```

### To export real assets from Datadog:

Use the `export-assets.py` script:

```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
python scripts/datadog/export-assets.py
```

Real exports will be written to:
- `../dashboards/*.json`
- `../monitors/*.json`
- `../slos/*.json`

## Important Notes

- **Templates do NOT contain real Datadog IDs** - they are configuration templates
- **Real exports contain IDs** - they are pulled directly from Datadog via API
- Always use `export-assets.py` to get the current state from Datadog
- Use `apply-assets.py` to create/update assets from templates

