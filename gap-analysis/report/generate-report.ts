import * as fs from "fs";
import * as path from "path";

interface PlaywrightTestResult {
  title: string;
  status: "passed" | "failed" | "timedOut" | "skipped";
  duration: number;
  errors: Array<{ message: string }>;
}

interface PlaywrightSuite {
  title: string;
  suites?: PlaywrightSuite[];
  specs?: Array<{
    title: string;
    tests: Array<{
      results: PlaywrightTestResult[];
    }>;
  }>;
}

interface PlaywrightReport {
  suites: PlaywrightSuite[];
}

interface GapEntry {
  category: string;
  test: string;
  status: "pass" | "fail" | "timeout" | "skip";
  error?: string;
  duration: number;
}

function collectTests(
  suite: PlaywrightSuite,
  parentTitle: string = ""
): GapEntry[] {
  const entries: GapEntry[] = [];
  const category = parentTitle
    ? `${parentTitle} > ${suite.title}`
    : suite.title;

  if (suite.specs) {
    for (const spec of suite.specs) {
      for (const test of spec.tests) {
        const result = test.results[test.results.length - 1];
        if (!result) continue;

        const statusMap: Record<string, GapEntry["status"]> = {
          passed: "pass",
          failed: "fail",
          timedOut: "timeout",
          skipped: "skip",
        };

        entries.push({
          category: suite.title || parentTitle,
          test: spec.title,
          status: statusMap[result.status] ?? "fail",
          error: result.errors?.[0]?.message,
          duration: result.duration,
        });
      }
    }
  }

  if (suite.suites) {
    for (const child of suite.suites) {
      entries.push(...collectTests(child, category));
    }
  }

  return entries;
}

function generateReport(entries: GapEntry[]): string {
  const lines: string[] = [];

  lines.push("# Kodemirror Gap Analysis Report");
  lines.push("");
  lines.push(
    `Generated: ${new Date().toISOString().split("T")[0]}`
  );
  lines.push("");

  // Summary table
  const categories = [...new Set(entries.map((e) => e.category))];
  lines.push("## Summary");
  lines.push("");
  lines.push("| Category | Pass | Fail | Timeout | Skip | Total |");
  lines.push("|----------|------|------|---------|------|-------|");

  let totalPass = 0,
    totalFail = 0,
    totalTimeout = 0,
    totalSkip = 0;

  for (const cat of categories) {
    const catEntries = entries.filter((e) => e.category === cat);
    const pass = catEntries.filter((e) => e.status === "pass").length;
    const fail = catEntries.filter((e) => e.status === "fail").length;
    const timeout = catEntries.filter((e) => e.status === "timeout").length;
    const skip = catEntries.filter((e) => e.status === "skip").length;
    totalPass += pass;
    totalFail += fail;
    totalTimeout += timeout;
    totalSkip += skip;
    lines.push(
      `| ${cat} | ${pass} | ${fail} | ${timeout} | ${skip} | ${catEntries.length} |`
    );
  }

  lines.push(
    `| **Total** | **${totalPass}** | **${totalFail}** | **${totalTimeout}** | **${totalSkip}** | **${entries.length}** |`
  );
  lines.push("");

  // Failures detail
  const failures = entries.filter(
    (e) => e.status === "fail" || e.status === "timeout"
  );
  if (failures.length > 0) {
    lines.push("## Failures");
    lines.push("");

    for (const f of failures) {
      lines.push(`### ${f.category} > ${f.test}`);
      lines.push("");
      lines.push(`- **Status:** ${f.status}`);
      lines.push(`- **Duration:** ${f.duration}ms`);
      if (f.error) {
        // Truncate very long error messages
        const errorMsg =
          f.error.length > 500 ? f.error.substring(0, 500) + "..." : f.error;
        lines.push("- **Error:**");
        lines.push("  ```");
        lines.push(`  ${errorMsg.replace(/\n/g, "\n  ")}`);
        lines.push("  ```");
      }
      lines.push("");
    }
  }

  // Suggested TODOs
  if (failures.length > 0) {
    lines.push("## Suggested TODO Items");
    lines.push("");
    lines.push(
      "Add these to `docs/TODO.md` for tracking and resolution:"
    );
    lines.push("");

    let todoNum = 1;
    for (const f of failures) {
      lines.push(
        `### ${todoNum}. Fix gap: ${f.category} - ${f.test}`
      );
      lines.push(
        `- Gap analysis test \`${f.test}\` in category \`${f.category}\` ${f.status === "timeout" ? "timed out" : "failed"}.`
      );
      if (f.error) {
        const shortError = f.error.split("\n")[0].substring(0, 120);
        lines.push(`- Error: ${shortError}`);
      }
      lines.push("");
      todoNum++;
    }
  }

  // Pass detail (compact)
  const passes = entries.filter((e) => e.status === "pass");
  if (passes.length > 0) {
    lines.push("## Passing Tests");
    lines.push("");
    for (const p of passes) {
      lines.push(`- [x] ${p.category} > ${p.test} (${p.duration}ms)`);
    }
    lines.push("");
  }

  return lines.join("\n");
}

// Main
const resultsPath = path.join(__dirname, "results.json");
const reportPath = path.join(__dirname, "gap-report.md");

if (!fs.existsSync(resultsPath)) {
  console.error(
    "No results.json found. Run `npx playwright test` first."
  );
  process.exit(1);
}

const raw = JSON.parse(fs.readFileSync(resultsPath, "utf-8")) as PlaywrightReport;
const entries: GapEntry[] = [];
for (const suite of raw.suites) {
  entries.push(...collectTests(suite));
}

const report = generateReport(entries);
fs.writeFileSync(reportPath, report);
console.log(`Gap report written to ${reportPath}`);
console.log(
  `  ${entries.filter((e) => e.status === "pass").length} pass, ` +
    `${entries.filter((e) => e.status === "fail").length} fail, ` +
    `${entries.filter((e) => e.status === "timeout").length} timeout, ` +
    `${entries.filter((e) => e.status === "skip").length} skip`
);
