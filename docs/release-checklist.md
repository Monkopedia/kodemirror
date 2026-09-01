# Release Checklist

Steps for publishing a new Kodemirror release to Maven Central.

> **`CLAUDE.md` → "Release process" is authoritative.** This file is its long-form operational
> companion: the same flow with the exact commands and gates. It may add detail; where the two
> disagree, `CLAUDE.md` wins and this file is the one that gets corrected.

## Pre-Release

### 1. All CI jobs are green

Check that **every** workflow on the `main` branch is passing:

```bash
gh run list --repo Monkopedia/kodemirror --branch main --limit 10
```

Required green:
- **CI** (JVM + wasmJs + Android) — runs on push
- **CI (Apple)** (macOS + iOS) — trigger manually if not recently run:
  ```bash
  gh workflow run ci-apple.yml --repo Monkopedia/kodemirror --ref main
  ```
- **Docs** (showcase build + API docs + site) — runs on push

Do not proceed until all three are green on the latest commit.

### 2. Version bump

Two files contain the version:

```bash
grep -r "SNAPSHOT" --include="*.gradle.kts" .
```

- `convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts` — `version = "X.Y.Z-SNAPSHOT"`
- `kodemirror-bom/build.gradle.kts` — `version = "X.Y.Z-SNAPSHOT"`

Remove `-SNAPSHOT` from both.

The docs hardcode Maven coordinates (`com.monkopedia.kodemirror:<module>:X.Y.Z`)
in install snippets, so grepping for `SNAPSHOT` never finds them. Grep for the
*previous* released version instead and bump every hit to the new one:

```bash
PREV=$(git describe --tags --abbrev=0 --match 'v*' | sed 's/^v//')
echo "superseding: $PREV"                    # positive control: must name the last release
grep -rn "$PREV" docs-site/docs/ README.md
```

`$PREV` is derived from the tag list rather than written down, because a literal version is true
only until the next release and then matches nothing — and **zero hits reads as "the docs are
already clean" when it actually means the pattern is wrong.** Check the control line before
believing an empty result.

Read every hit before changing it rather than running a blanket `sed`: a dependency snippet or a
current-version claim (`README.md`'s "This is vX.Y.Z") gets bumped, but a genuine historical
reference — "changed in $PREV", "if you are upgrading from $PREV" — must stay as written, or the
document starts making false statements. As of the 0.3.6 bump every hit in these four files
(`README.md`, `docs-site/docs/examples/bundle.md`, `docs-site/docs/guide/getting-started.md`,
`docs-site/docs/guide/migration.md`) was a coordinate or a version claim, but that is a fact
about today's docs, not a licence to skip the read next time.

Then confirm nothing stale is left — the only versions these files should
mention are the one being released and historical CHANGELOG entries:

```bash
grep -rEn "com\.monkopedia\.kodemirror:[a-z-]+:[0-9]" docs-site/docs/ README.md
grep -rn "SNAPSHOT" docs-site/docs/ README.md
```

### 3. Changelog

Entries live as one file per change under `changelog.d/` — `CHANGELOG.md` carries no
`[Unreleased]` section to edit, because a shared block made every merge conflict every other open
PR (#281). Run this **after** step 2, since the assembler derives the version from the build files
you just bumped:

```bash
python3 .github/scripts/changelog.py preview          # read the entries in aggregate
python3 .github/scripts/changelog.py assemble         # writes CHANGELOG.md, deletes the fragments
```

Read the `preview` output before assembling rather than after. It is the one moment in the cycle
when all of a release's entries are in front of one reader, and changelog defects are real: a
wrong `MapMode` default and a fabricated test count both reached review and were caught only by
someone reading the entry.

`assemble` refuses an empty `changelog.d/`, refuses a `-SNAPSHOT` or underivable version instead
of writing an empty one, and checks its own result is purely additive — the new section stripped
back out must reproduce the previous `CHANGELOG.md` byte for byte, so published sections cannot be
reworded by accident. Any of those failing aborts with a non-zero exit and writes nothing.

The result must be a dated `[X.Y.Z] - YYYY-MM-DD` section; `deploy.yml` extracts the release notes
from it by that exact heading.

### 4. Commit the version bump

Steps 2 and 3 are **one PR**, opened through the normal coderbot -> `monkopedia-reviewer` flow
(`CLAUDE.md` -> "Task Workflow"); never pushed straight to `main`. Stage the docs bump from step
2 along with the two version files, or the release ships with stale coordinates again:

```bash
git add convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts \
        kodemirror-bom/build.gradle.kts \
        CHANGELOG.md README.md docs-site/docs/
git add -A changelog.d/          # -A, so the fragment deletions are staged too
git commit -m "Bump version to X.Y.Z for release"
```

Then push the branch and open the PR via coderbot. Wait for CI + Docs to pass and for the PR to
merge before proceeding.

## Release

### 5. Tag the release commit

Tags and GitHub Releases exist **only** for fully-published versions, so push the tag now and do
**not** create the GitHub Release by hand — `deploy.yml` creates it itself once the publish has
succeeded (step 6).

```bash
git tag vX.Y.Z <merged-release-commit>
git push origin vX.Y.Z
```

### 6. Deploy to Maven Central

Trigger the deploy workflow (manual dispatch):

```bash
gh workflow run deploy.yml --repo Monkopedia/kodemirror --ref vX.Y.Z
```

A single macOS runner publishes every target, the BOM included, in one Gradle invocation — one
atomic Central Portal deployment, validated and auto-released. On success the workflow's
`release` job creates the matching GitHub Release (`vX.Y.Z`) from the CHANGELOG section: it
skips `-SNAPSHOT`/`-RC` versions, is re-run safe (no-op if the release already exists), and
`--verify-tag` fails the job if the tag was never pushed.

Monitor progress:
```bash
gh run list --repo Monkopedia/kodemirror --workflow deploy.yml --limit 3
```

If the publish fails partway, that version is **burned**: do not retry it. Delete the tag, skip
the version, and cut the next patch.

### 7. Verify on Maven Central

Confirm the BOM resolves from Maven Central proper (`repo1.maven.org`) — propagation takes
roughly 10-35 minutes:

```bash
curl -sI https://repo1.maven.org/maven2/com/monkopedia/kodemirror/kodemirror-bom/X.Y.Z/kodemirror-bom-X.Y.Z.pom
```

`HTTP/2 200` means published; `404` means it has not synced (or the deploy did not succeed).
Then confirm the workflow created the GitHub Release:

```bash
gh release view vX.Y.Z --repo Monkopedia/kodemirror
```

## Post-Release

### 8. Bump to next SNAPSHOT

```bash
# In both files, change version to next development version:
# convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts
# kodemirror-bom/build.gradle.kts
# X.Y.Z → X.Y.(Z+1)-SNAPSHOT

git add convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts kodemirror-bom/build.gradle.kts
git commit -m "Bump version to X.Y.(Z+1)-SNAPSHOT for development"
```

Like every other change, this goes through the coderbot -> reviewer PR flow, not a direct push
to `main`.

### 9. Notify downstream consumers

Not optional — this is `CLAUDE.md`'s release step 5, and the release is not finished until
consumers have been told. *Which* channels to use is the discretionary part:

- Post to relevant Reddit communities
- Submit PR to [kmp-awesome](https://github.com/terrakok/kmp-awesome)
