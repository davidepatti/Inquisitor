const { app, BrowserWindow, dialog, ipcMain, shell } = require("electron");
const fs = require("fs");
const fsp = require("fs/promises");
const path = require("path");
const crypto = require("crypto");
const { spawn } = require("child_process");

const projectRoot = path.resolve(__dirname, "..");
const rendererEntry = path.join(projectRoot, "renderer", "index.html");
const javaClassesDir = path.join(projectRoot, "build", "electron-java", "classes");
const defaultProfilePath = path.join(projectRoot, "courses.properties");

let mainWindow = null;
let allowWindowClose = false;
let closePromptOpen = false;

function createWindow() {
  allowWindowClose = false;
  closePromptOpen = false;
  mainWindow = new BrowserWindow({
    width: 1320,
    height: 860,
    minWidth: 1080,
    minHeight: 700,
    title: "Inquisitor",
    backgroundColor: "#f6f4ef",
    show: false,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.once("ready-to-show", () => mainWindow.show());
  mainWindow.on("close", handleWindowClose);
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
  mainWindow.loadFile(rendererEntry);
}

async function handleWindowClose(event) {
  if (allowWindowClose) {
    return;
  }

  event.preventDefault();
  if (closePromptOpen) {
    return;
  }

  closePromptOpen = true;
  const windowToClose = mainWindow;

  try {
    const closeState = await getProfileCloseState(windowToClose);
    if (!closeState?.dirty) {
      closeWindowWithoutPrompt(windowToClose);
      return;
    }

    const resolution = await resolveUnsavedProfileChanges(closeState.profile, "closing");
    if (resolution.action === "cancel") {
      return;
    }

    closeWindowWithoutPrompt(windowToClose);
  } catch (error) {
    await dialog.showMessageBox(windowToClose, {
      type: "error",
      buttons: ["OK"],
      message: "Unable to close Inquisitor",
      detail: error.message || String(error)
    });
  } finally {
    closePromptOpen = false;
  }
}

async function getProfileCloseState(targetWindow) {
  if (!targetWindow || targetWindow.isDestroyed() || targetWindow.webContents.isDestroyed()) {
    return { dirty: false, profile: null };
  }

  return targetWindow.webContents.executeJavaScript(
    "window.inquisitorGetProfileCloseState ? window.inquisitorGetProfileCloseState() : { dirty: false, profile: null }",
    true
  );
}

async function resolveUnsavedProfileChanges(profile, actionDescription) {
  const choice = await dialog.showMessageBox(mainWindow, {
    type: "question",
    buttons: ["Save", "Don't Save", "Cancel"],
    defaultId: 0,
    cancelId: 2,
    noLink: true,
    message: `Save changes to "${profile?.name || "Inquisitor"}" before ${actionDescription}?`,
    detail: "Unsaved changes to the current profile will be lost if you don't save them."
  });

  if (choice.response === 2) {
    return { action: "cancel", profile: null };
  }

  if (choice.response === 1) {
    return { action: "discard", profile: null };
  }

  try {
    const saved = await saveProfileToCurrentPath(profile);
    return saved ? { action: "saved", profile: saved } : { action: "cancel", profile: null };
  } catch (error) {
    await dialog.showMessageBox(mainWindow, {
      type: "error",
      buttons: ["OK"],
      message: "Unable to save profile",
      detail: error.message || String(error)
    });
    return { action: "cancel", profile: null };
  }
}

async function saveProfileToCurrentPath(profile) {
  const defaultPath = profile?.path || path.join(projectRoot, "courses.properties");
  if (!profile?.path) {
    const result = await dialog.showSaveDialog(mainWindow, {
      title: "Save profile",
      defaultPath,
      filters: [{ name: "Inquisitor profiles", extensions: ["properties"] }]
    });
    if (result.canceled || !result.filePath) {
      return null;
    }
    return saveProfileFile(profile, result.filePath);
  }

  return saveProfileFile(profile, profile.path);
}

async function chooseProfilePath() {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: "Load profile",
    defaultPath: projectRoot,
    properties: ["openFile"],
    filters: [{ name: "Inquisitor profiles", extensions: ["properties"] }]
  });
  if (result.canceled || result.filePaths.length === 0) {
    return null;
  }
  return result.filePaths[0];
}

function closeWindowWithoutPrompt(targetWindow) {
  if (!targetWindow || targetWindow.isDestroyed()) {
    return;
  }
  allowWindowClose = true;
  targetWindow.close();
}

function defaultSeedForToday() {
  const now = new Date();
  const yyyy = String(now.getFullYear());
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  return Number(`${yyyy}${mm}${dd}`);
}

function isEscaped(text, index) {
  let count = 0;
  for (let i = index - 1; i >= 0 && text[i] === "\\"; i -= 1) {
    count += 1;
  }
  return count % 2 === 1;
}

function joinPropertiesContinuations(text) {
  const physicalLines = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
  const logicalLines = [];
  let current = "";

  for (const line of physicalLines) {
    const trimmedEnd = line.replace(/[ \t\f]+$/g, "");
    const continues = trimmedEnd.endsWith("\\") && !isEscaped(trimmedEnd, trimmedEnd.length - 1);
    current += continues ? trimmedEnd.slice(0, -1) : trimmedEnd;
    if (!continues) {
      logicalLines.push(current);
      current = "";
    }
  }

  if (current) {
    logicalLines.push(current);
  }
  return logicalLines;
}

function unescapeProperty(text) {
  let output = "";
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (ch !== "\\" || i === text.length - 1) {
      output += ch;
      continue;
    }

    const next = text[++i];
    if (next === "t") output += "\t";
    else if (next === "n") output += "\n";
    else if (next === "r") output += "\r";
    else if (next === "f") output += "\f";
    else if (next === "u" && i + 4 < text.length) {
      const hex = text.slice(i + 1, i + 5);
      if (/^[0-9a-fA-F]{4}$/.test(hex)) {
        output += String.fromCharCode(Number.parseInt(hex, 16));
        i += 4;
      } else {
        output += "u";
      }
    } else {
      output += next;
    }
  }
  return output;
}

function escapeProperty(text) {
  return String(text ?? "")
    .replace(/\\/g, "\\\\")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "\\r");
}

function parseProperties(text) {
  const props = {};

  for (const raw of joinPropertiesContinuations(text)) {
    const line = raw.trimStart();
    if (!line || line.startsWith("#") || line.startsWith("!")) {
      continue;
    }

    let separator = -1;
    let separatorIsWhitespace = false;
    for (let i = 0; i < line.length; i += 1) {
      const ch = line[i];
      if (isEscaped(line, i)) {
        continue;
      }
      if (ch === "=" || ch === ":") {
        separator = i;
        break;
      }
      if (/\s/.test(ch)) {
        separator = i;
        separatorIsWhitespace = true;
        break;
      }
    }

    let key;
    let value;
    if (separator === -1) {
      key = line;
      value = "";
    } else {
      key = line.slice(0, separator);
      let valueStart = separator + 1;
      if (separatorIsWhitespace) {
        while (valueStart < line.length && /\s/.test(line[valueStart])) {
          valueStart += 1;
        }
        if (line[valueStart] === "=" || line[valueStart] === ":") {
          valueStart += 1;
        }
      }
      while (valueStart < line.length && /\s/.test(line[valueStart])) {
        valueStart += 1;
      }
      value = line.slice(valueStart);
    }

    props[unescapeProperty(key.trim())] = unescapeProperty(value.trim());
  }

  return props;
}

function parseInteger(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function nonNegativeInteger(value, fallback) {
  return Math.max(0, parseInteger(value, fallback));
}

function profileSeedValue(profile) {
  const selectedIndex = Number(profile?.selectedCourseIndex || 0);
  const selectedCourse = profile?.courses?.[selectedIndex];
  return nonNegativeInteger(profile?.seed, nonNegativeInteger(selectedCourse?.seed, defaultSeedForToday()));
}

function courseSeedValue(course, fallbackSeed) {
  return nonNegativeInteger(course?.seed, fallbackSeed);
}

function parseBoolean(value, fallback = true) {
  if (value == null || value === "") {
    return fallback;
  }
  return String(value).trim().toLowerCase() === "true";
}

function defaultHeadingForCourse(name) {
  return name && name.trim() ? name.trim() : "Exam";
}

function resolveProfilePath(rawPath, profileDir) {
  if (!rawPath) {
    return "";
  }
  const candidate = path.normalize(rawPath);
  if (path.isAbsolute(candidate) || !profileDir) {
    return candidate;
  }
  return path.resolve(profileDir, candidate);
}

function formatProfilePath(rawPath, profileDir) {
  if (!rawPath) {
    return "";
  }

  const normalizedPath = path.resolve(rawPath);
  if (!profileDir) {
    return normalizedPath;
  }

  const relativePath = path.relative(profileDir, normalizedPath);
  if (relativePath && !relativePath.startsWith("..") && !path.isAbsolute(relativePath)) {
    return relativePath;
  }
  return normalizedPath;
}

function parseCounts(raw) {
  const counts = {};
  if (!raw || !raw.trim()) {
    return counts;
  }

  for (const pair of raw.split(";")) {
    const idx = pair.lastIndexOf("=");
    if (idx <= 0 || idx >= pair.length - 1) {
      continue;
    }
    const fileName = pair.slice(0, idx).trim();
    const count = Number.parseInt(pair.slice(idx + 1).trim(), 10);
    if (fileName && Number.isFinite(count) && count > 0) {
      counts[fileName] = count;
    }
  }
  return counts;
}

function formatCounts(counts) {
  return Object.entries(counts || {})
    .filter(([, value]) => Number(value) > 0)
    .map(([fileName, value]) => `${fileName}=${Number(value)}`)
    .join(";");
}

function profileNameFromPath(filePath) {
  const base = path.basename(filePath || "courses.properties");
  return base.replace(/\.(properties|inqprofile)$/i, "") || "Inquisitor";
}

async function loadProfileFile(filePath) {
  const normalizedPath = path.resolve(filePath);
  const text = await fsp.readFile(normalizedPath, "utf8");
  const props = parseProperties(text);
  const profileDir = path.dirname(normalizedPath);
  const profileSeed = nonNegativeInteger(props["profile.seed"], defaultSeedForToday());
  const profile = {
    name: props["profile.name"] || profileNameFromPath(normalizedPath),
    path: normalizedPath,
    seed: profileSeed,
    compilePdf: parseBoolean(props["profile.compilePdf"], true),
    selectedCourseIndex: parseInteger(props["profile.selectedCourse"], 0),
    courses: []
  };

  const legacyHeading = props["profile.heading"] || "";
  const legacySubheading = props["profile.subheading"] || "";
  const legacyTotalExams = parseInteger(props["profile.totalExams"], 8);
  const legacyTotalStudents = parseInteger(props["profile.totalStudents"], 120);
  let order = (props.courses || "").split(",").map((id) => id.trim()).filter(Boolean);

  if (order.length === 0) {
    const ids = new Set();
    Object.keys(props).forEach((key) => {
      const match = key.match(/^course\.(\d+)\.name$/);
      if (match) ids.add(match[1]);
    });
    order = Array.from(ids).sort((a, b) => Number(a) - Number(b));
  }

  for (const id of order) {
    const prefix = `course.${id}.`;
    const name = props[`${prefix}name`];
    const storedPath = props[`${prefix}path`];
    if (!name || !storedPath) {
      continue;
    }

    const heading = props[`${prefix}heading`] ?? legacyHeading;
    const subheading = props[`${prefix}subheading`] ?? legacySubheading;
    profile.courses.push({
      id,
      name,
      heading: heading && heading.trim() ? heading : defaultHeadingForCourse(name),
      subheading: subheading || "",
      seed: nonNegativeInteger(props[`${prefix}seed`], profileSeed),
      basePath: resolveProfilePath(storedPath, profileDir),
      totalExams: parseInteger(props[`${prefix}totalExams`], legacyTotalExams),
      totalStudents: parseInteger(props[`${prefix}totalStudents`], legacyTotalStudents),
      questionCounts: parseCounts(props[`${prefix}counts`] || "")
    });
  }

  if (profile.courses.length === 0) {
    profile.courses.push({
      id: "1",
      name: "Default",
      heading: "Exam",
      subheading: "",
      seed: profileSeed,
      basePath: path.join(projectRoot, "questions"),
      totalExams: 8,
      totalStudents: 120,
      questionCounts: {}
    });
  }

  profile.selectedCourseIndex = Math.max(
    0,
    Math.min(profile.selectedCourseIndex, profile.courses.length - 1)
  );

  return profile;
}

async function saveProfileFile(profile, filePath) {
  const normalizedPath = path.resolve(filePath);
  const profileDir = path.dirname(normalizedPath);
  const profileSeed = profileSeedValue(profile);
  const lines = [
    "# Inquisitor Electron profile",
    `profile.name=${escapeProperty(profile.name || profileNameFromPath(normalizedPath))}`,
    `profile.seed=${profileSeed}`,
    `profile.compilePdf=${profile.compilePdf === false ? "false" : "true"}`,
    `profile.selectedCourse=${Number(profile.selectedCourseIndex || 0)}`,
    `courses=${profile.courses.map((_, index) => index + 1).join(",")}`
  ];

  profile.courses.forEach((course, index) => {
    const id = index + 1;
    const prefix = `course.${id}.`;
    lines.push(`${prefix}name=${escapeProperty(course.name)}`);
    lines.push(`${prefix}path=${escapeProperty(formatProfilePath(course.basePath, profileDir))}`);
    lines.push(`${prefix}heading=${escapeProperty(course.heading || defaultHeadingForCourse(course.name))}`);
    lines.push(`${prefix}subheading=${escapeProperty(course.subheading || "")}`);
    lines.push(`${prefix}seed=${courseSeedValue(course, profileSeed)}`);
    lines.push(`${prefix}totalExams=${Number(course.totalExams || 8)}`);
    lines.push(`${prefix}totalStudents=${Number(course.totalStudents || 120)}`);
    lines.push(`${prefix}counts=${escapeProperty(formatCounts(course.questionCounts))}`);
  });

  await fsp.mkdir(path.dirname(normalizedPath), { recursive: true });
  await fsp.writeFile(normalizedPath, `${lines.join("\n")}\n`, "utf8");
  return loadProfileFile(normalizedPath);
}

async function listQaFiles(basePath) {
  const normalizedBasePath = path.resolve(basePath || ".");
  const entries = await fsp.readdir(normalizedBasePath, { withFileTypes: true });
  const files = entries
    .filter((entry) => entry.isFile() && isQuestionBankFileName(entry.name))
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }))
    .map((entry) => entry.name);

  const results = [];
  for (const fileName of files) {
    const filePath = path.join(normalizedBasePath, fileName);
    const text = await fsp.readFile(filePath, "utf8").catch(() => "");
    const questionCount = countQuestionBankEntries(text);
    results.push({ fileName, path: filePath, questionCount });
  }

  return { basePath: normalizedBasePath, files: results };
}

function isQuestionBankFileName(fileName) {
  return fileName.toLowerCase().endsWith(".qa.md");
}

function countQuestionBankEntries(text) {
  return text
    .split(/\r?\n/)
    .filter((line) => {
      const trimmed = line.trimStart();
      return /^##[ \t]+/.test(trimmed);
    }).length;
}

function sanitizeHeading(heading) {
  return String(heading || "Exam").trim().replace(/\s+/g, "_");
}

function generateExamIdPrefix(commandLineArgs) {
  return crypto.createHash("sha256").update(commandLineArgs).digest("hex").slice(0, 8).toUpperCase();
}

function buildOutputPaths(config, generatorArgs = buildJavaArgs(config)) {
  const seed = nonNegativeInteger(config.seed, defaultSeedForToday());
  const heading = config.heading && config.heading.trim() ? config.heading.trim() : "Exam";
  const outputDir = path.resolve(config.basePath, `${sanitizeHeading(heading)}_${seed}`);
  const filePrefix = generateExamIdPrefix(generatorArgs.join(" "));
  return {
    outputDir,
    texPath: path.join(outputDir, `${filePrefix}_all_exams.tex`),
    csvPath: path.join(outputDir, `${filePrefix}_results.csv`),
    answersPath: path.join(outputDir, `${filePrefix}_answers_key.txt`),
    pdfPath: path.join(outputDir, `${filePrefix}_all_exams.pdf`)
  };
}

function outputFileState(outputPaths) {
  return {
    outputDir: fs.existsSync(outputPaths.outputDir),
    tex: fs.existsSync(outputPaths.texPath),
    csv: fs.existsSync(outputPaths.csvPath),
    answers: fs.existsSync(outputPaths.answersPath),
    pdf: fs.existsSync(outputPaths.pdfPath)
  };
}

function buildJavaArgs(config) {
  const args = ["--base_path", config.basePath];
  for (const selection of config.selections || []) {
    const count = Number(selection.count);
    if (count > 0) {
      args.push(String(count), selection.fileName);
    }
  }
  args.push("-t", String(Number(config.totalExams || 1)));
  args.push("-st", String(Number(config.totalStudents || 1)));
  args.push("-s", String(nonNegativeInteger(config.seed, defaultSeedForToday())));
  args.push("-h", config.heading && config.heading.trim() ? config.heading.trim() : "Exam");
  if (config.subheading && config.subheading.trim()) {
    args.push("-h2", config.subheading.trim());
  }
  return args;
}

function runProcess(command, args, options, onLine) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options?.cwd || projectRoot,
      env: { ...process.env, ...(options?.env || {}) },
      shell: false
    });
    let buffer = "";

    const consume = (chunk) => {
      buffer += chunk.toString();
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() || "";
      for (const line of lines) {
        if (line.trim() || options?.includeBlankLines) {
          onLine?.(line);
        }
      }
    };

    child.stdout.on("data", consume);
    child.stderr.on("data", consume);
    child.on("error", reject);
    child.on("close", (code) => {
      if (buffer.trim()) {
        onLine?.(buffer);
      }
      resolve(code ?? 0);
    });
  });
}

function checkCommand(command, args = ["--version"]) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { shell: false });
    const timer = setTimeout(() => {
      child.kill();
      resolve(false);
    }, 5000);

    child.on("error", () => {
      clearTimeout(timer);
      resolve(false);
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      resolve(code === 0);
    });
  });
}

async function runGeneration(event, config) {
  const runId = config.runId || String(Date.now());
  const sendLog = (line, level = "info") => {
    event.sender.send("generation:log", { runId, line, level });
  };

  const classFile = path.join(javaClassesDir, "Inquisitor.class");
  if (!fs.existsSync(classFile)) {
    throw new Error("Java CLI classes are missing. Run `npm run java:build` first.");
  }

  const generatorArgs = buildJavaArgs(config);
  const javaArgs = ["-cp", javaClassesDir, "Inquisitor", ...generatorArgs];
  const outputPaths = buildOutputPaths(config, generatorArgs);

  sendLog("Starting Java generator.");
  const javaExit = await runProcess("java", javaArgs, { cwd: projectRoot }, sendLog);
  if (javaExit !== 0) {
    sendLog(`Java generator exited with code ${javaExit}.`, "error");
    return { ok: false, javaExit, pdfExit: null, outputPaths, files: outputFileState(outputPaths), runId };
  }

  let pdfExit = null;
  if (config.compilePdf) {
    const pdflatexAvailable = await checkCommand("pdflatex", ["--version"]);
    if (!pdflatexAvailable) {
      sendLog("pdflatex was not found. LaTeX, CSV, and answer key were still generated.", "warning");
    } else if (!fs.existsSync(outputPaths.texPath)) {
      sendLog(`Skipping PDF: missing ${outputPaths.texPath}`, "warning");
    } else {
      sendLog("Compiling PDF with pdflatex, pass 1.");
      pdfExit = await runProcess(
        "pdflatex",
        [path.basename(outputPaths.texPath)],
        { cwd: outputPaths.outputDir },
        sendLog
      );
      if (pdfExit === 0) {
        sendLog("Compiling PDF with pdflatex, pass 2.");
        pdfExit = await runProcess(
          "pdflatex",
          [path.basename(outputPaths.texPath)],
          { cwd: outputPaths.outputDir },
          sendLog
        );
      }
      if (pdfExit !== 0) {
        sendLog(`pdflatex exited with code ${pdfExit}.`, "error");
      }
    }
  }

  return {
    ok: javaExit === 0 && (pdfExit == null || pdfExit === 0),
    javaExit,
    pdfExit,
    outputPaths,
    files: outputFileState(outputPaths),
    runId
  };
}

function registerIpc() {
  ipcMain.handle("app:getState", async () => ({
    projectRoot,
    defaultProfilePath,
    defaultSeed: defaultSeedForToday(),
    javaReady: fs.existsSync(path.join(javaClassesDir, "Inquisitor.class")),
    pdflatexAvailable: await checkCommand("pdflatex", ["--version"])
  }));

  ipcMain.handle("profile:loadDefault", async () => loadProfileFile(defaultProfilePath));

  ipcMain.handle("profile:choosePath", async () => chooseProfilePath());
  ipcMain.handle("profile:loadPath", async (_event, filePath) => loadProfileFile(filePath));

  ipcMain.handle("profile:chooseAndLoad", async () => {
    const filePath = await chooseProfilePath();
    return filePath ? loadProfileFile(filePath) : null;
  });

  ipcMain.handle("profile:resolveUnsavedChanges", async (_event, profile, actionDescription) => (
    resolveUnsavedProfileChanges(profile, actionDescription || "continuing")
  ));

  ipcMain.handle("profile:save", async (_event, profile) => {
    const result = await dialog.showSaveDialog(mainWindow, {
      title: "Save profile",
      defaultPath: profile.path || path.join(projectRoot, "courses.properties"),
      filters: [{ name: "Inquisitor profiles", extensions: ["properties"] }]
    });
    if (result.canceled || !result.filePath) {
      return null;
    }
    return saveProfileFile(profile, result.filePath);
  });

  ipcMain.handle("dialog:chooseDirectory", async (_event, startPath) => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: "Choose question folder",
      defaultPath: startPath && fs.existsSync(startPath) ? startPath : projectRoot,
      properties: ["openDirectory"]
    });
    if (result.canceled || result.filePaths.length === 0) {
      return null;
    }
    return result.filePaths[0];
  });

  ipcMain.handle("qa:list", async (_event, basePath) => listQaFiles(basePath));
  ipcMain.handle("generation:run", runGeneration);

  ipcMain.handle("path:open", async (_event, filePath) => {
    if (!filePath) {
      return "";
    }
    return shell.openPath(filePath);
  });

  ipcMain.handle("path:show", async (_event, filePath) => {
    if (filePath && fs.existsSync(filePath)) {
      shell.showItemInFolder(filePath);
      return true;
    }
    return false;
  });
}

app.whenReady().then(() => {
  registerIpc();
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
