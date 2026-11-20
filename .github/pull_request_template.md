## Summary

<!-- What changes and why. Link the issue it resolves, if any. -->

## Testing

<!-- The commands you ran and anything you checked by hand. -->

- [ ] `./mvnw verify` (tests, coverage gate and API contract test)
- [ ] `npm test` in `frontend/` for changes to the user interface
- [ ] `./mvnw verify -Ddormitory.test.database=mysql` for database or transaction changes

## Checklist

- [ ] Schema changes are a new migration under `src/main/resources/db/migration/`
- [ ] API changes are recorded in `docs/openapi.yaml`
- [ ] New settings are documented in `.env.example` and `docs/deployment.md`
- [ ] Screenshots and logs use fictional data
