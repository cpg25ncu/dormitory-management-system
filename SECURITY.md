# Security policy

## Supported versions

Security fixes are made on `main` and included in the next release. Use the latest release for deployments.

## Reporting a vulnerability

Report vulnerabilities privately through [GitHub private vulnerability reporting](https://github.com/cpg25ncu/dormitory-management-system/security/advisories/new). Do not open a public issue, pull request or discussion for a vulnerability.

Include:

- the affected version or commit
- steps to reproduce, with fictional data
- the impact you expect, for example which role can read or change what
- any proof of concept

Reports are acknowledged as soon as possible. A fix and an advisory follow once the issue is confirmed. Credit is given in the advisory unless you ask otherwise.

## Scope

The `demo` profile and the browser demo use fictional data and publish fixed demo sign-ins on purpose. These are not vulnerabilities on their own. The demo profile listens only on the loopback interface. Do not use it for real records.

For deployments, follow the [deployment guide](docs/deployment.md). In particular, serve the app over HTTPS with `DORM_SECURE_COOKIE=true`, set `DORM_FORWARD_HEADERS=native` behind a trusted reverse proxy, and use distinct passwords for the database and the first administrator.
