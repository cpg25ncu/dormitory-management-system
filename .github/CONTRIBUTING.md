# Contributing

1. Create a branch from `main` and run the [local demo](../README.md#try-the-local-demo).
2. Make the change in the relevant frontend or backend module. Record API changes in the [OpenAPI contract](../docs/openapi.yaml). Add schema changes as a new versioned SQL file under `src/main/resources/db/migration/` to preserve checksums in existing deployments.
3. Run the checks in [Development and testing](../docs/testing.md). `./mvnw verify` runs the tests, the coverage gate and the API contract test. For frontend changes, run the browser suite and verify the work in each affected workspace. For database or transaction changes, also run `./mvnw verify -Ddormitory.test.database=mysql`, which needs Docker.
4. Open a pull request with the affected workflows, the checks you ran and any migration or configuration steps. Use fictional data in screenshots and logs.

Open issues with the bug report or feature request form. Report security vulnerabilities privately as described in the [security policy](../SECURITY.md), not in an issue. The repository's [`.editorconfig`](../.editorconfig) sets UTF-8, LF line endings and indentation.

Keep local secrets in the ignored `.env` file. Document new deployment variables in [.env.example](../.env.example) and the [deployment guide](../docs/deployment.md).
