#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const ANSWERS_PER_QUESTION = 4;

function usage() {
  console.log(`Usage: node scripts/convert_qa_to_md.cjs [--overwrite] <file.qa|directory> [...]

Converts legacy Inquisitor .qa files to .qa.md files.

Examples:
  node scripts/convert_qa_to_md.cjs example_questions/Wallace_questions
  node scripts/convert_qa_to_md.cjs --overwrite example_questions/Lynch_questions/1_lynch_films.qa
`);
}

function normalizeBlock(lines) {
  return lines
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
}

function readNextAnswer(lines, startIndex, sourcePath, questionText) {
  let index = startIndex;
  while (index < lines.length && lines[index].trim().length === 0) {
    index++;
  }
  if (index >= lines.length) {
    throw new Error(`Unexpected end of file while reading answers for question "${questionText}" in ${sourcePath}`);
  }
  return { answer: lines[index].trim(), nextIndex: index + 1 };
}

function parseLegacyQa(sourcePath) {
  const text = fs.readFileSync(sourcePath, "utf8");
  const lines = text.split(/\r?\n/);
  const questions = [];

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();
    if (!trimmed.startsWith("[Q]")) {
      continue;
    }

    const questionLines = [];
    const firstPart = trimmed.slice(3).trim();
    const inlineClose = firstPart.indexOf("[/Q]");
    if (inlineClose >= 0) {
      const beforeClose = firstPart.slice(0, inlineClose).trim();
      if (beforeClose.length > 0) {
        questionLines.push(beforeClose);
      }
    } else {
      if (firstPart.length > 0) {
        questionLines.push(firstPart);
      }

      let foundClose = false;
      while (++i < lines.length) {
        const qLine = lines[i].trim();
        const closeIndex = qLine.indexOf("[/Q]");
        if (closeIndex >= 0) {
          const beforeClose = qLine.slice(0, closeIndex).trim();
          if (beforeClose.length > 0) {
            questionLines.push(beforeClose);
          }
          foundClose = true;
          break;
        }
        if (qLine.length > 0) {
          questionLines.push(qLine);
        }
      }

      if (!foundClose) {
        throw new Error(`Missing [/Q] for question in ${sourcePath}`);
      }
    }

    const questionText = normalizeBlock(questionLines);
    if (questionText.length === 0) {
      throw new Error(`Empty question in ${sourcePath}`);
    }

    let correctAnswer = null;
    const wrongAnswers = [];
    let nextIndex = i + 1;

    for (let answerIndex = 0; answerIndex < ANSWERS_PER_QUESTION; answerIndex++) {
      const answerResult = readNextAnswer(lines, nextIndex, sourcePath, questionText);
      nextIndex = answerResult.nextIndex;
      let answer = answerResult.answer;

      if (answer.startsWith("*")) {
        if (correctAnswer !== null) {
          throw new Error(`Multiple correct answers marked for question "${questionText}" in ${sourcePath}`);
        }
        correctAnswer = answer.slice(1).trim();
      } else {
        wrongAnswers.push(answer);
      }
    }

    if (correctAnswer === null || correctAnswer.length === 0) {
      throw new Error(`No correct answer marked for question "${questionText}" in ${sourcePath}`);
    }

    questions.push({
      question: questionText,
      answers: [correctAnswer, ...wrongAnswers]
    });
    i = nextIndex - 1;
  }

  return questions;
}

function renderMarkdown(questions, sourcePath) {
  const bankTitle = path.basename(sourcePath, ".qa")
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  const lines = [`# ${bankTitle || "Question Bank"}`, ""];

  for (const question of questions) {
    lines.push(`## ${question.question}`, "");
    for (const answer of question.answers) {
      lines.push(`- ${answer}`);
    }
    lines.push("");
  }

  return `${lines.join("\n").trimEnd()}\n`;
}

function convertFile(sourcePath, options) {
  if (!sourcePath.toLowerCase().endsWith(".qa")) {
    throw new Error(`Not a .qa file: ${sourcePath}`);
  }

  const outputPath = `${sourcePath}.md`;
  if (!options.overwrite && fs.existsSync(outputPath)) {
    throw new Error(`Refusing to overwrite existing file: ${outputPath}`);
  }

  const questions = parseLegacyQa(sourcePath);
  if (questions.length === 0) {
    throw new Error(`No questions found in ${sourcePath}`);
  }

  fs.writeFileSync(outputPath, renderMarkdown(questions, sourcePath), "utf8");
  console.log(`${sourcePath} -> ${outputPath} (${questions.length} questions)`);
}

function collectFiles(targetPath) {
  const stat = fs.statSync(targetPath);
  if (stat.isDirectory()) {
    return fs.readdirSync(targetPath)
      .filter((entry) => entry.toLowerCase().endsWith(".qa"))
      .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
      .map((entry) => path.join(targetPath, entry));
  }
  return [targetPath];
}

function main() {
  const args = process.argv.slice(2);
  const options = { overwrite: false };
  const targets = [];

  for (const arg of args) {
    if (arg === "--help" || arg === "-h") {
      usage();
      return;
    }
    if (arg === "--overwrite") {
      options.overwrite = true;
      continue;
    }
    targets.push(arg);
  }

  if (targets.length === 0) {
    usage();
    process.exitCode = 1;
    return;
  }

  try {
    const files = targets.flatMap((target) => collectFiles(path.resolve(target)));
    if (files.length === 0) {
      throw new Error("No .qa files found.");
    }
    for (const file of files) {
      convertFile(file, options);
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

main();
