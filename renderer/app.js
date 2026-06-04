const api = window.inquisitor;

const icons = {
  scan: '<path d="M7 3H5a2 2 0 0 0-2 2v2"/><path d="M17 3h2a2 2 0 0 1 2 2v2"/><path d="M21 17v2a2 2 0 0 1-2 2h-2"/><path d="M7 21H5a2 2 0 0 1-2-2v-2"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><path d="M9 9h.01"/><path d="M15 9h.01"/>',
  upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5"/><path d="M12 3v12"/>',
  save: '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8"/><path d="M7 3v5h8"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  trash: '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/>',
  refresh: '<path d="M21 12a9 9 0 0 1-15.3 6.4"/><path d="M3 12A9 9 0 0 1 18.3 5.6"/><path d="M18 2v4h4"/><path d="M6 22v-4H2"/>',
  sliders: '<path d="M4 21v-7"/><path d="M4 10V3"/><path d="M12 21v-9"/><path d="M12 8V3"/><path d="M20 21v-5"/><path d="M20 12V3"/><path d="M2 14h4"/><path d="M10 8h4"/><path d="M18 16h4"/>',
  folder: '<path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/>',
  "folder-open": '<path d="M6 14l1.5-4h13L18 20H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v2"/>',
  "list-check": '<path d="M11 6h10"/><path d="M11 12h10"/><path d="M11 18h10"/><path d="m3 6 1 1 3-3"/><path d="m3 12 1 1 3-3"/><path d="m3 18 1 1 3-3"/>',
  play: '<path d="M6 4v16l14-8Z"/>',
  files: '<path d="M15 2H6a2 2 0 0 0-2 2v12"/><path d="M8 6h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z"/>',
  "file-check": '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/><path d="m9 15 2 2 4-4"/>',
  "file-text": '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/><path d="M8 13h8"/><path d="M8 17h6"/>',
  table: '<path d="M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2Z"/><path d="M4 10h16"/><path d="M10 3v18"/>',
  external: '<path d="M15 3h6v6"/><path d="M10 14 21 3"/><path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5"/>',
  terminal: '<path d="m4 17 6-6-6-6"/><path d="M12 19h8"/>',
  minus: '<path d="M5 12h14"/>'
};

const state = {
  app: null,
  profile: null,
  currentCourseIndex: 0,
  qaFiles: [],
  lastResult: null,
  generating: false
};

const el = {};

function byId(id) {
  return document.getElementById(id);
}

function numberValue(input, fallback) {
  const value = Number.parseInt(input.value, 10);
  return Number.isFinite(value) ? value : fallback;
}

function iconSvg(name) {
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || ""}</svg>`;
}

function installIcons(root = document) {
  root.querySelectorAll("[data-icon]").forEach((node) => {
    node.innerHTML = iconSvg(node.dataset.icon);
  });
}

function setStatus(node, icon, text, mode) {
  node.textContent = text;
  icon.className = `status-dot ${mode}`;
}

function setBusy(isBusy) {
  state.generating = isBusy;
  el.generateBtn.disabled = isBusy || selectedTotal() === 0;
  el.generateBtn.querySelector("span:last-child").textContent = isBusy ? "Generating" : "Generate";
  el.refreshQaBtn.disabled = isBusy;
  el.browseFolderBtn.disabled = isBusy;
}

function updateSystemStatus() {
  if (!state.app) {
    return;
  }
  setStatus(
    el.javaStatus,
    el.javaStatusIcon,
    state.app.javaReady ? "Java CLI ready" : "Java CLI missing",
    state.app.javaReady ? "" : "error"
  );
  setStatus(
    el.pdfStatus,
    el.pdfStatusIcon,
    state.app.pdflatexAvailable ? "pdflatex ready" : "pdflatex optional",
    state.app.pdflatexAvailable ? "" : "warning"
  );
}

function captureCurrentCourse() {
  if (!state.profile || !state.profile.courses[state.currentCourseIndex]) {
    return;
  }

  const course = state.profile.courses[state.currentCourseIndex];
  course.heading = el.headingInput.value.trim() || course.name || "Exam";
  course.subheading = el.subheadingInput.value.trim();
  course.basePath = el.basePathInput.value.trim();
  course.totalExams = Math.max(1, numberValue(el.totalExamsInput, 1));
  course.totalStudents = Math.max(1, numberValue(el.totalStudentsInput, 1));
  course.questionCounts = {};

  el.qaList.querySelectorAll("[data-file-name]").forEach((row) => {
    const input = row.querySelector("input");
    const value = Math.max(0, numberValue(input, 0));
    if (value > 0) {
      course.questionCounts[row.dataset.fileName] = value;
    }
  });
}

function renderProfile() {
  if (!state.profile) {
    return;
  }

  el.profileName.value = state.profile.name || "Inquisitor";
  el.profilePath.textContent = state.profile.path || "";
  el.compilePdfInput.checked = state.profile.compilePdf !== false;
  renderCourseOptions();
}

function renderCourseOptions() {
  el.courseSelect.innerHTML = "";
  state.profile.courses.forEach((course, index) => {
    const option = document.createElement("option");
    option.value = String(index);
    option.textContent = course.name;
    el.courseSelect.append(option);
  });
  el.courseSelect.value = String(state.currentCourseIndex);
}

async function selectCourse(index) {
  if (!state.profile || state.profile.courses.length === 0) {
    return;
  }

  state.currentCourseIndex = Math.max(0, Math.min(index, state.profile.courses.length - 1));
  state.profile.selectedCourseIndex = state.currentCourseIndex;
  const course = state.profile.courses[state.currentCourseIndex];

  el.courseSelect.value = String(state.currentCourseIndex);
  el.workspaceTitle.textContent = course.name || "Course";
  el.headingInput.value = course.heading || course.name || "Exam";
  el.subheadingInput.value = course.subheading || "";
  el.seedInput.value = String(state.app?.defaultSeed || "");
  el.totalExamsInput.value = String(course.totalExams || 8);
  el.totalStudentsInput.value = String(course.totalStudents || 120);
  el.basePathInput.value = course.basePath || "";
  await refreshQaFiles();
}

async function refreshQaFiles() {
  const basePath = el.basePathInput.value.trim();
  el.qaList.innerHTML = '<div class="empty-state">Reading question bank...</div>';
  updateTotal();

  if (!basePath) {
    state.qaFiles = [];
    el.qaList.innerHTML = '<div class="empty-state">No folder selected.</div>';
    return;
  }

  try {
    const result = await api.listQaFiles(basePath);
    state.qaFiles = result.files;
    el.basePathInput.value = result.basePath;
    renderQaFiles();
  } catch (error) {
    state.qaFiles = [];
    el.qaList.innerHTML = `<div class="empty-state">${escapeHtml(error.message || "Unable to read folder.")}</div>`;
  }
}

function renderQaFiles() {
  const course = state.profile?.courses[state.currentCourseIndex];
  const counts = course?.questionCounts || {};
  el.qaList.innerHTML = "";

  if (state.qaFiles.length === 0) {
    el.qaList.innerHTML = '<div class="empty-state">No .qa.md files found.</div>';
    updateTotal();
    return;
  }

  for (const file of state.qaFiles) {
    const row = document.createElement("div");
    row.className = "qa-row";
    row.dataset.fileName = file.fileName;
    const initial = Math.max(0, Math.min(Number(counts[file.fileName] || 0), file.questionCount));
    row.innerHTML = `
      <div class="qa-name">
        <strong title="${escapeAttribute(file.fileName)}">${escapeHtml(file.fileName)}</strong>
        <span>${escapeHtml(file.path)}</span>
      </div>
      <div class="qa-available">${file.questionCount} available</div>
      <div class="qa-counter">
        <button class="stepper" type="button" data-step="-1" title="Decrease">${iconSvg("minus")}</button>
        <input type="number" min="0" max="${file.questionCount}" step="1" value="${initial}" aria-label="${escapeAttribute(file.fileName)} count">
        <button class="stepper" type="button" data-step="1" title="Increase">${iconSvg("plus")}</button>
      </div>
    `;
    el.qaList.append(row);
  }

  updateTotal();
}

function selectedTotal() {
  let total = 0;
  el.qaList.querySelectorAll(".qa-row input").forEach((input) => {
    total += Math.max(0, numberValue(input, 0));
  });
  return total;
}

function selectedFiles() {
  return Array.from(el.qaList.querySelectorAll(".qa-row"))
    .map((row) => ({
      fileName: row.dataset.fileName,
      count: Math.max(0, numberValue(row.querySelector("input"), 0))
    }))
    .filter((selection) => selection.count > 0);
}

function updateTotal() {
  const total = selectedTotal();
  el.totalQuestions.textContent = String(total);
  el.generateBtn.disabled = state.generating || total === 0;
}

function appendLog(line, level = "info") {
  if (!line) {
    return;
  }
  const entry = document.createElement("div");
  entry.className = `log-line ${level}`;
  entry.textContent = line;
  el.logOutput.append(entry);
  while (el.logOutput.children.length > 500) {
    el.logOutput.firstElementChild.remove();
  }
  el.logOutput.scrollTop = el.logOutput.scrollHeight;
}

function clearLog() {
  el.logOutput.innerHTML = "";
}

function setOutput(result) {
  state.lastResult = result;
  const paths = result?.outputPaths;
  const files = result?.files || {};
  const ok = Boolean(result?.ok);
  el.outputSummary.textContent = paths
    ? `${ok ? "Generated" : "Finished with warnings"}: ${paths.outputDir}`
    : "No generation yet.";

  el.openPdfBtn.disabled = !paths?.pdfPath || !files.pdf;
  el.openTexBtn.disabled = !paths?.texPath || !files.tex;
  el.openCsvBtn.disabled = !paths?.csvPath || !files.csv;
  el.openFolderBtn.disabled = !paths?.outputDir || !files.outputDir;
}

async function runGeneration() {
  captureCurrentCourse();
  const course = state.profile.courses[state.currentCourseIndex];
  const selections = selectedFiles();

  if (selections.length === 0) {
    appendLog("Select at least one question.", "warning");
    return;
  }

  const config = {
    runId: String(Date.now()),
    basePath: el.basePathInput.value.trim(),
    heading: el.headingInput.value.trim() || course.name || "Exam",
    subheading: el.subheadingInput.value.trim(),
    seed: Math.max(0, numberValue(el.seedInput, state.app.defaultSeed)),
    totalExams: Math.max(1, numberValue(el.totalExamsInput, 1)),
    totalStudents: Math.max(1, numberValue(el.totalStudentsInput, 1)),
    compilePdf: el.compilePdfInput.checked,
    selections
  };

  clearLog();
  setBusy(true);
  appendLog(`Course: ${course.name}`);
  appendLog(`Folder: ${config.basePath}`);

  try {
    const result = await api.runGeneration(config);
    setOutput(result);
    appendLog(result.ok ? "Generation completed." : "Generation finished with errors.", result.ok ? "info" : "error");
  } catch (error) {
    appendLog(error.message || "Generation failed.", "error");
  } finally {
    setBusy(false);
  }
}

async function loadDefaultProfile() {
  state.profile = await api.loadDefaultProfile();
  state.currentCourseIndex = state.profile.selectedCourseIndex || 0;
  renderProfile();
  await selectCourse(state.currentCourseIndex);
}

async function chooseAndLoadProfile() {
  captureCurrentCourse();
  const loaded = await api.chooseAndLoadProfile();
  if (!loaded) {
    return;
  }
  state.profile = loaded;
  state.currentCourseIndex = loaded.selectedCourseIndex || 0;
  renderProfile();
  await selectCourse(state.currentCourseIndex);
}

async function saveProfile() {
  captureCurrentCourse();
  state.profile.name = el.profileName.value.trim() || "Inquisitor";
  state.profile.compilePdf = el.compilePdfInput.checked;
  state.profile.selectedCourseIndex = state.currentCourseIndex;
  const saved = await api.saveProfile(state.profile);
  if (!saved) {
    return;
  }
  state.profile = saved;
  state.currentCourseIndex = saved.selectedCourseIndex || 0;
  renderProfile();
  await selectCourse(state.currentCourseIndex);
  appendLog(`Saved profile: ${saved.path}`);
}

function showCourseDialog(defaultName = "") {
  return new Promise((resolve) => {
    el.courseDialogName.value = defaultName;
    const onClose = () => {
      el.courseDialog.removeEventListener("close", onClose);
      resolve(el.courseDialog.returnValue === "save" ? el.courseDialogName.value.trim() : null);
    };
    el.courseDialog.addEventListener("close", onClose);
    el.courseDialog.showModal();
    el.courseDialogName.focus();
  });
}

async function addCourse() {
  captureCurrentCourse();
  const name = await showCourseDialog("");
  if (!name) {
    return;
  }

  const selectedPath = await api.chooseDirectory(el.basePathInput.value.trim());
  if (!selectedPath) {
    return;
  }

  const course = {
    id: String(Date.now()),
    name,
    heading: name,
    subheading: "",
    basePath: selectedPath,
    totalExams: Math.max(1, numberValue(el.totalExamsInput, 8)),
    totalStudents: Math.max(1, numberValue(el.totalStudentsInput, 120)),
    questionCounts: {}
  };
  state.profile.courses.push(course);
  state.currentCourseIndex = state.profile.courses.length - 1;
  renderCourseOptions();
  await selectCourse(state.currentCourseIndex);
}

async function removeCourse() {
  if (!state.profile || state.profile.courses.length === 0) {
    return;
  }
  const course = state.profile.courses[state.currentCourseIndex];
  if (!window.confirm(`Remove ${course.name}?`)) {
    return;
  }
  state.profile.courses.splice(state.currentCourseIndex, 1);
  if (state.profile.courses.length === 0) {
    state.profile.courses.push({
      id: "1",
      name: "Default",
      heading: "Exam",
      subheading: "",
      basePath: "",
      totalExams: 8,
      totalStudents: 120,
      questionCounts: {}
    });
  }
  state.currentCourseIndex = Math.max(0, state.currentCourseIndex - 1);
  renderCourseOptions();
  await selectCourse(state.currentCourseIndex);
}

async function browseFolder() {
  const selectedPath = await api.chooseDirectory(el.basePathInput.value.trim());
  if (!selectedPath) {
    return;
  }
  el.basePathInput.value = selectedPath;
  await refreshQaFiles();
}

async function openOutput(kind) {
  const paths = state.lastResult?.outputPaths;
  if (!paths) {
    return;
  }

  if (kind === "folder") {
    await api.openPath(paths.outputDir);
  } else if (kind === "pdf") {
    await api.openPath(paths.pdfPath);
  } else if (kind === "tex") {
    await api.openPath(paths.texPath);
  } else if (kind === "csv") {
    await api.openPath(paths.csvPath);
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function escapeAttribute(value) {
  return escapeHtml(value).replace(/'/g, "&#039;");
}

function bindElements() {
  [
    "profile-name",
    "profile-path",
    "load-profile-btn",
    "save-profile-btn",
    "java-status",
    "java-status-icon",
    "pdf-status",
    "pdf-status-icon",
    "workspace-title",
    "add-course-btn",
    "remove-course-btn",
    "course-select",
    "refresh-qa-btn",
    "heading-input",
    "subheading-input",
    "seed-input",
    "total-exams-input",
    "total-students-input",
    "browse-folder-btn",
    "base-path-input",
    "qa-list",
    "total-questions",
    "compile-pdf-input",
    "generate-btn",
    "output-summary",
    "open-pdf-btn",
    "open-tex-btn",
    "open-csv-btn",
    "open-folder-btn",
    "log-output",
    "course-dialog",
    "course-dialog-name"
  ].forEach((id) => {
    const key = id.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
    el[key] = byId(id);
  });
}

function bindEvents() {
  api.onGenerationLog((payload) => appendLog(payload.line, payload.level));

  el.courseSelect.addEventListener("change", async () => {
    captureCurrentCourse();
    await selectCourse(Number.parseInt(el.courseSelect.value, 10));
  });
  el.loadProfileBtn.addEventListener("click", chooseAndLoadProfile);
  el.saveProfileBtn.addEventListener("click", saveProfile);
  el.addCourseBtn.addEventListener("click", addCourse);
  el.removeCourseBtn.addEventListener("click", removeCourse);
  el.browseFolderBtn.addEventListener("click", browseFolder);
  el.refreshQaBtn.addEventListener("click", refreshQaFiles);
  el.generateBtn.addEventListener("click", runGeneration);
  el.compilePdfInput.addEventListener("change", () => {
    if (state.profile) {
      state.profile.compilePdf = el.compilePdfInput.checked;
    }
  });
  el.basePathInput.addEventListener("change", refreshQaFiles);
  el.qaList.addEventListener("input", updateTotal);
  el.qaList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-step]");
    if (!button) {
      return;
    }
    const input = button.parentElement.querySelector("input");
    const step = Number.parseInt(button.dataset.step, 10);
    const min = Number.parseInt(input.min, 10);
    const max = Number.parseInt(input.max, 10);
    input.value = String(Math.max(min, Math.min(max, numberValue(input, 0) + step)));
    updateTotal();
  });

  el.openPdfBtn.addEventListener("click", () => openOutput("pdf"));
  el.openTexBtn.addEventListener("click", () => openOutput("tex"));
  el.openCsvBtn.addEventListener("click", () => openOutput("csv"));
  el.openFolderBtn.addEventListener("click", () => openOutput("folder"));
}

async function init() {
  bindElements();
  installIcons();
  bindEvents();

  state.app = await api.getAppState();
  updateSystemStatus();
  await loadDefaultProfile();
  appendLog("Ready.");
}

init().catch((error) => {
  bindElements();
  installIcons();
  el.logOutput.textContent = error.message || String(error);
});
