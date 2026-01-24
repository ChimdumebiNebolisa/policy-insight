# IDE Notes

## Stale JDT Errors

CLI builds successfully (`./mvnw package` passes), but the IDE may show "cannot be resolved" errors for types like:
- `JobPublisher`
- `StorageService`
- `PolicyJob`
- `PolicyJobRepository`

**Resolution:** These are IDE/classpath issues, not actual compilation errors. To fix:
1. Reimport Maven project
2. Reload workspace
3. Clean IDE cache if needed

The project compiles successfully from the command line.

## GitHub Actions "Context access might be invalid" Warnings

The VS Code GitHub Actions extension/language service may show warnings like "Context access might be invalid" for workflow files. These are known false positives. The workflows use `secrets.*` and `vars.*` contexts correctly. Verify workflow functionality via actual GitHub Actions runs rather than relying on editor warnings alone.

