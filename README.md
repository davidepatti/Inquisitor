# Inquisitor

## Requirements

- JDK 17+ (`javac`, `java`)
- Node.js 22.12+

## Electron App

Inquisitor now uses Electron as its desktop interface and keeps `Inquisitor.java` as the command-line generation engine.

Install dependencies:

```bash
npm install
```

Run the app:

```bash
npm start
```

Or use the convenience launcher:

```bash
./scripts/inquisitor
```

## IntelliJ IDEA

Open the project folder in IntelliJ IDEA. The shared run configurations in `.run/` should appear in the run configuration menu:

- `Inquisitor Electron`: starts the Electron app with `npm start`
- `Build Java CLI`: compiles `Inquisitor.java`
- `Check Electron App`: syntax-checks the Electron files

If dependencies are not installed yet, run `npm install` once from IntelliJ's Terminal or from the package.json npm scripts view.

If your IntelliJ edition does not load npm run configurations, use the Terminal inside IntelliJ:

```bash
./scripts/inquisitor
```

Useful scripts:

- `npm run java:build`: compiles `Inquisitor.java` into `build/electron-java/classes`
- `npm run check`: syntax-checks the Electron main, preload, and renderer scripts
- `npm run qa:convert -- path/to/questions`: converts old `.qa` files to `.qa.md`

The Electron UI loads `courses.properties`, scans `.qa.md` question-bank folders, runs the Java CLI, streams the generation log, and optionally calls `pdflatex` when it is available.

## Question Bank Format

The source format is `.qa.md`, a small Markdown subset:

```md
# Course Topic

## Who wrote "Infinite Jest"?

- David Foster Wallace
- Jonathan Franzen
- Thomas Pynchon
- Don DeLillo

## For $f(x)=x^2 + 3x$, what is $f'(x)$?

- $2x + 3$
- $x + 3$
- $2x$
- $x^2 + 3$
```

Rules:

- Each `##` heading starts a question.
- Paragraphs after the heading and before the answers are included in the question prompt.
- The four answer bullets immediately after the prompt are the answer choices.
- The first answer bullet is the correct answer in source; answers are shuffled when exams are generated.
- LaTeX math is preserved inside `$...$` and `$$...$$`.

Old `.qa` files are not supported at runtime. Convert them first:

```bash
node scripts/convert_qa_to_md.cjs path/to/questions
```

The converter accepts old `.qa` files or directories containing `.qa` files, and writes sibling `.qa.md` files. Add `--overwrite` to replace existing converted files.

## Java CLI

The Java CLI remains available for automation.

Compile it:

```bash
npm run java:build
```

Run it directly:

```bash
java -cp build/electron-java/classes Inquisitor n1 file1.qa.md n2 file2.qa.md \
  --base_path ./questions \
  --seed 20260604 \
  --total_exams 8 \
  --students 120 \
  -h "Exam Heading" \
  -h2 "Exam Subheading"
```

## Default Course Data

The default local course/profile data includes:

- `courses.properties`
- `example_questions/Wallace_questions/`
- `example_questions/Lynch_questions/`
