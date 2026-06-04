# Inquisitor Java to Web/Electron Conversion Notes

Created for the `devel` branch on 2026-06-03.

## Current Version

The project is now an Electron desktop application backed by the existing Java command-line generator.

Main files:

- `Inquisitor.java`: command-line generator. It parses arguments, reads question banks, selects and shuffles questions/answers, writes LaTeX, writes a Google Sheets-oriented CSV, and writes an answer key.
- `electron/main.cjs`: Electron main process for filesystem access, native dialogs, Java subprocess execution, optional `pdflatex`, and opening generated artifacts.
- `electron/preload.cjs`: secure bridge between the renderer and Electron main process.
- `renderer/`: modern web interface for course/profile selection, question counts, generation options, logs, and output actions.
- `courses.properties`: persisted course/profile data used by the Electron UI.
- `questions/`, `Wallace_questions/`, `Lynch_questions/`: sample or working `.qa` question-bank folders.
- `scripts/build_electron_java.sh`: compiles the Java CLI into `build/electron-java/classes`.
- `scripts/inquisitor`: convenience launcher for `npm start`.

## Current User Workflow

The Electron UI lets the user:

- Select or add a course profile.
- Choose a folder containing `.qa` files.
- Pick how many questions to draw from each `.qa` file.
- Set heading, subheading, seed, number of exam variants, and number of students.
- Generate exam artifacts.
- Optionally run `pdflatex` twice to create a PDF.
- Open the generated PDF.

The CLI accepts arguments in this shape:

```bash
java Inquisitor n1 file1.qa n2 file2.qa \
  --base_path ./questions \
  --seed 20260603 \
  --total_exams 8 \
  --students 120 \
  -h "Exam Heading" \
  -h2 "Exam Subheading"
```

Important defaults:

- Seed defaults to today's date as `yyyyMMdd`.
- Heading defaults to `Exam` in the CLI, or to the selected course name in the Electron UI.
- Output folder is `<sanitized-heading>_<seed>`.
- Output files are `<seed>_all_exams.tex`, `<seed>_results.csv`, and `<seed>_answers_key.txt`.

## Data Formats to Preserve

Question bank format:

```text
[Q] Question text [/Q]
*Correct answer
Wrong answer 1
Wrong answer 2
Wrong answer 3
```

Notes:

- Multi-line question blocks are supported between `[Q]` and `[/Q]`.
- Each question currently expects exactly 4 answers.
- The first answer must be marked with `*` and is treated as the correct answer before answer shuffling.
- Questions and answers may include LaTeX snippets delimited by `$`.

Course profile format:

- Stored as Java `.properties`.
- Course order is listed in `courses=1,2,3`.
- Course records use `course.<id>.name`, `course.<id>.path`, `course.<id>.counts`, and newer optional per-course settings like heading, subheading, total exams, and total students.
- Counts are stored as `file.qa=3;other.qa=2`.

Generated artifact behavior:

- LaTeX output uses an A4 article layout, two columns, smaller margins, and numbered answers.
- CSV output includes student fields, exam ID, seed, answer-entry formulas, per-question correctness formulas, and summary counters.
- Answer key output lists each exam and the 1-based correct answer index for every question.
- Exam IDs are SHA-256 based: the generator hashes the command-line argument string, takes the first 4 bytes as hex, and appends the exam number.

## Conversion Direction

Chosen first-step architecture:

- Keep `Inquisitor.java` as the authoritative command-line generator.
- Add an Electron main process for filesystem access, native file dialogs, profile file read/write, Java subprocess execution, optional `pdflatex`, and opening generated artifacts.
- Add a modern web renderer for course/profile UI, question-file list, count controls, generation options, logs, and output actions.
- Defer a TypeScript port of the generator core until the Electron workflow is stable.

Implemented in this first Electron pass:

- `package.json` and `package-lock.json` with Electron 42.3.2.
- `scripts/build_electron_java.sh` to compile the Java CLI into `build/electron-java/classes`.
- `electron/main.cjs` to load/save profiles, scan `.qa` folders, invoke Java, stream logs, optionally call `pdflatex`, and open outputs.
- `electron/preload.cjs` to expose a small secure API to the renderer.
- `renderer/index.html`, `renderer/styles.css`, and `renderer/app.js` for the first modern interface.

Removed from the active code path:

- `InquisitorSwingUI.java`
- old Java `jpackage` scripts: `scripts/build_macos_app.sh`, `scripts/build_linux_app.sh`, and `scripts/launch_inquisitor.sh`
- generated old Swing artifacts under `build/macos`, `dist/Inquisitor.app`, and `out/production/Inquisitor`

Future core modules to extract/port only if/when Java is retired:

- `parseQaFile(text): Question[]`
- `generateExamSet(config, questionBanks): ExamData[]`
- `renderLatex(config, exams): string`
- `renderResultsCsv(config, exams): string`
- `renderAnswersKey(exams): string`
- `buildOutputPaths(config): OutputPaths`
- `loadProfile/saveProfile`, either preserving `.properties` or migrating to JSON with an importer.

Compatibility concerns:

- Java uses `java.util.Random`; use a compatible implementation in TypeScript if old seeds must reproduce old exam variants exactly.
- Preserve current `.qa` parsing and LaTeX escaping in tests before improving it. The current code escapes question text during parsing and again during rendering, so fixtures should decide what behavior the first web version must match.
- CSV formulas currently use semicolon separators, which is appropriate for some Google Sheets locales but may need an option in the web version.
- `pdflatex` is an external dependency. Electron should detect it, show availability, and allow generation of `.tex`/CSV/key even if PDF compilation is unavailable.

## First Web/Electron Milestones

1. Scaffold Electron plus a web renderer, preferably with TypeScript.
2. Port the generator core without UI dependencies.
3. Add fixture tests from existing `.qa` files and generated outputs.
4. Build the renderer UI around the current exam-generation workflow.
5. Add Electron main-process services for selecting folders, saving profiles, writing outputs, running `pdflatex`, and opening PDFs.
6. Add Electron packaging scripts for distributable native apps.

## Useful Existing Fixtures

Use these to validate the port:

- `Wallace_questions/`
- `Lynch_questions/`
- `questions/`
- Existing generated folders such as `IoT_Systems_and_Technologies_20250630/`, `IoT_Systems_and_Technologies_20260321/`, and course-specific generated folders.

## Open Decisions

- Keep `.properties` profiles for backward compatibility, or migrate to JSON and provide import/export.
- Preserve exact Java randomization output, or accept a new deterministic sequence for the web version.
- Continue producing LaTeX as the primary printable format, or add direct HTML/PDF generation later.
- Keep all data local-only, or add an optional project/workspace concept for managing multiple courses.
