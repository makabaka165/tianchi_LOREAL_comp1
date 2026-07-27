# Security verification baseline - 2026-07-27

## 1. Scope and status

- Code baseline: `main@133d0aa`.
- Gate status: `BLOCKED`; unit, API contract and full integration workflows pass, but the dependency vulnerability gate correctly remains red.
- This is a pre-existing platform dependency baseline, not a DATA-002 parser regression. DATA-002 stays `DONE`, while RELEASE-002 cannot be marked `DONE` until this gate is resolved or an explicit, evidence-backed risk decision is accepted.
- No CVE suppression was added. A finding may only be suppressed after its CPE/package match and exploitability have been reviewed and the rationale is versioned.

## 2. Scanner failure and repair

The original workflow used OWASP Dependency-Check `10.0.4`. On 2026 NVD data it failed before producing a report:

- CVE references longer than the old database's 1,000-character URL column could not be stored;
- the RetireJS repository download timed out and, with no local RetireJS data, aborted the Maven dependency analysis;
- the roughly 650 MB vulnerability database shared the generic Maven cache, so updates were not reliably persisted between runs.

The workflow now uses Dependency-Check `12.2.2` (Java 11 compatible), disables only the unrelated RetireJS and hosted-suppression remote sources, retains NVD and CISA analysis, restores/saves a dedicated database cache, uploads HTML/JSON reports even when the CVSS gate fails, and runs permission/SSRF/path-traversal regressions before the long dependency scan.

## 3. Reproduction

```powershell
mvn -B -ntp org.owasp:dependency-check-maven:12.2.2:check `
  '-DfailBuildOnCVSS=9' '-DskipTestScope=true' '-Dformats=HTML,JSON' `
  '-DdataDirectory=target/dependency-check-data-v12' `
  '-DretireJsAnalyzerEnabled=false' '-DhostedSuppressionsEnabled=false'
```

Result: the scanner completed and generated `target/dependency-check-report.json`, then failed the build because the following runtime artifacts have one or more findings at CVSS 9.0 or above.

The clean v12 database build and scan took `20:31`. Repeating the same command against that database took `21.4s`, skipped the four-hour NVD and 24-hour CISA updates, and produced both HTML and JSON reports. This validates the dedicated CI cache; the cached database changes performance only and does not change the vulnerability result or threshold.

| Artifact | Dependency path / ownership | Highest observed CVSS | Required disposition |
| --- | --- | ---: | --- |
| `hutool-all:5.7.17` | direct dependency | 9.8 | test a current 5.8.x patch line and remove unused modules where possible |
| `mybatis-plus-core:3.4.3` | direct MyBatis-Plus starter | 9.8 | upgrade starter/core together and rerun persistence regressions |
| `netty-transport:4.1.101.Final` | direct Lettuce plus managed Netty graph | 10.0 | align Netty/Lettuce versions; do not override a single Netty jar in isolation |
| `opennlp-tools:1.9.4` | LangChain4j transitive dependency | 9.8 | upgrade or exclude through a tested LangChain4j-compatible path |
| `kotlin-stdlib:1.6.21` | OkHttp/OpenAI client transitive dependency | 9.8 | align Kotlin/OkHttp graph to a fixed compatible version |
| `snakeyaml:1.30` | Spring/Redisson managed dependency | 9.8 | upgrade with Spring configuration parsing tests |
| `tika-core:2.9.1` | direct dependency | 9.8 | upgrade the full Tika parser module set together |
| `spring-boot:2.7.18` | platform BOM | 9.8 | requires an explicit supported-platform upgrade decision |
| `spring-core:5.3.31` | Spring Boot 2.7 managed graph | 9.8 | resolve with the Spring platform, not an isolated jar override |
| `spring-web:5.3.31` | Spring Boot 2.7 managed graph | 9.8 | resolve with the Spring platform; separately validate legacy CPE matches |
| `tomcat-embed-core:9.0.83` | Spring Boot starter Tomcat | 9.8 | take a compatible patched Tomcat line or platform upgrade |
| `jaxb-runtime:2.3.9` | Tika parser transitive dependency | 9.6 | validate Eclipse GlassFish CPE match, then upgrade or document a precise false positive |
| `txw2:2.3.9` | JAXB transitive dependency | 9.6 | validate Eclipse GlassFish CPE match with the JAXB upgrade |

## 4. Execution boundary

The implementation plan explicitly excludes a Spring Boot/Java major-version upgrade from the current customer-service feature scope. Therefore the next feature task remains DATA-003, but the dependency baseline must be handled as a separate platform-security workstream before release. The safe order is:

1. apply patch-compatible upgrades for direct libraries and coherent transitive graphs;
2. verify suspected CPE false positives with package-specific evidence before any suppression;
3. make an explicit architecture/scope decision for the unsupported Spring Boot 2.7/Spring 5.3 platform;
4. rerun unit, contract, full integration and Security workflows; only a real green result closes the release gate.
