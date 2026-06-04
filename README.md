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

The Electron UI loads `courses.properties`, scans `.qa` folders, runs the Java CLI, streams the generation log, and optionally calls `pdflatex` when it is available.

## Java CLI

The Java CLI remains available for compatibility and automation.

Compile it:

```bash
npm run java:build
```

Run it directly:

```bash
java -cp build/electron-java/classes Inquisitor n1 file1.qa n2 file2.qa \
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
- `Wallace_questions/`
- `Lynch_questions/`
