# Contributing

1. Create a branch from `main` and run the application from source as described in the [quick start](../README.md#quick-start).
2. Make the change in the relevant frontend or backend module. Record API changes in the [OpenAPI contract](../docs/openapi.yaml). Add schema changes as a new versioned SQL file under `src/main/resources/db/migration/` to preserve checksums in existing deployments.
3. Run the checks in [Development and testing](../docs/testing.md). `./mvnw verify` runs the tests, the coverage gate and the API contract test. For frontend changes, run the browser suite and verify the work in each affected workspace. For database or transaction changes, also run `./mvnw verify -Ddormitory.test.database=mysql`, which needs Docker.
4. Add user-facing changes to an `Unreleased` section at the top of the [changelog](../CHANGELOG.md).
5. Open a pull request with the affected workflows, the checks you ran and any migration or configuration steps. Use fictional data in screenshots and logs. When the user interface changes, regenerate the README screenshots with `npm run screenshots` in `frontend/`.

Open issues with the bug report or feature request form. Report security vulnerabilities privately as described in the [security policy](../SECURITY.md), not in an issue. The repository's [`.editorconfig`](../.editorconfig) sets UTF-8, LF line endings and indentation.

Keep local secrets in the ignored `.env` file. Document new deployment variables in [.env.example](../.env.example) and the [deployment guide](../docs/deployment.md).

## Releasing

1. Set the version in `pom.xml` and `docs/openapi.yaml`, and the Docker example in both READMEs.
2. Turn the `Unreleased` changelog section into `## [x.y.z] - date`, and add a comparison link at the bottom.
3. Merge to `main`, then tag the merge commit and push the tag:

   ```bash
   git tag -a vX.Y.Z -m "Dormitory Management System X.Y.Z"
   git push origin vX.Y.Z
   ```

The [release workflow](workflows/release.yml) checks that the tag matches the project version and runs the full build. It publishes `ghcr.io/cpg25ncu/dormitory-management-system` with `X.Y.Z` and `X.Y` tags, and creates the GitHub release with the changelog section, the JAR and `SHA256SUMS`.
