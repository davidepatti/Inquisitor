const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("inquisitor", {
  getAppState: () => ipcRenderer.invoke("app:getState"),
  loadDefaultProfile: () => ipcRenderer.invoke("profile:loadDefault"),
  chooseAndLoadProfile: () => ipcRenderer.invoke("profile:chooseAndLoad"),
  saveProfile: (profile) => ipcRenderer.invoke("profile:save", profile),
  chooseDirectory: (startPath) => ipcRenderer.invoke("dialog:chooseDirectory", startPath),
  listQaFiles: (basePath) => ipcRenderer.invoke("qa:list", basePath),
  runGeneration: (config) => ipcRenderer.invoke("generation:run", config),
  openPath: (filePath) => ipcRenderer.invoke("path:open", filePath),
  showPath: (filePath) => ipcRenderer.invoke("path:show", filePath),
  onGenerationLog: (callback) => {
    const listener = (_event, payload) => callback(payload);
    ipcRenderer.on("generation:log", listener);
    return () => ipcRenderer.removeListener("generation:log", listener);
  }
});
