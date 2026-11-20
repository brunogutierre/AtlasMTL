# 0005. Ingest as a weekly CI job

**Status:** Accepted, 2026-11-20

## Context

The sources update between weekly and twice a year. Downloading and normalizing ~350k addresses takes memory and time that the small web container shouldn't spend.

## Decision

- A separate Gradle module, `ingest`, runs as a command-line app on a GitHub Actions cron, once a week, and on demand.
- For each dataset:
  1. resolve the download URL through CKAN `package_show`, sending a `User-Agent: atlasmtl-ingest/<version>` header;
  2. download the file and compare its checksum with the last run, skipping the dataset if nothing changed;
  3. load it into a staging table and normalize it;
  4. swap staging and live in one transaction.

## Consequences

- The web app only reads, and it never sees a half-loaded dataset.
- A failed run leaves the previous data in place and shows up as a failed workflow.
- The database credentials for ingest live only in the GitHub secrets.

## Alternatives rejected

- **`@Scheduled` in the web app:** couples ingestion to web uptime and memory.
- **Spring Batch:** restartable chunked jobs are more than five files a week need.
- **ogr2ogr scripts:** an extra tool, with normalization spread over SQL scripts that are hard to test.
