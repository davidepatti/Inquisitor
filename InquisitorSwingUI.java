import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InquisitorSwingUI extends JFrame {
    private static final String DEFAULT_PROFILES_FILE = "courses.properties";
    private static final String ABOUT_VERSION = "Build 2026-03-24 16:55 CET";

    private final DefaultComboBoxModel<CourseProfile> courseModel = new DefaultComboBoxModel<>();
    private final JComboBox<CourseProfile> courseCombo = new JComboBox<>(courseModel);

    private final JTextField headingField = new JTextField(22);
    private final JTextField subheadingField = new JTextField(22);
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(defaultSeedForToday(), 0L, Long.MAX_VALUE, 1L));
    private final JSpinner examsSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 10000, 1));
    private final JSpinner studentsSpinner = new JSpinner(new SpinnerNumberModel(120, 1, 1000000, 1));

    private final JTextField basePathField = new JTextField(22);
    private final JTextField profileNameField = new JTextField(22);
    private final JTextField profilesFileField = new JTextField(22);
    private final JPanel qaPanel = new JPanel(new GridBagLayout());
    private final JTextArea logArea = new JTextArea(12, 56);
    private final JCheckBox compilePdfBox = new JCheckBox("Compile PDF with pdflatex (2 passes)", true);
    private final JButton generateButton = new JButton("Generate Exams");
    private final JButton openPdfButton = new JButton("Open PDF");
    private final JLabel totalQuestionsLabel = new JLabel("Total selected questions: 0");

    private final Map<String, JSpinner> fileSpinners = new LinkedHashMap<>();
    private CourseProfile currentCourse;
    private boolean suppressCourseEvents = false;
    private Path lastGeneratedPdf;
    private Path profilesFilePath = resolveDefaultProfilesPath();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InquisitorSwingUI ui = new InquisitorSwingUI();
            ui.setVisible(true);
        });
    }

    private static long defaultSeedForToday() {
        return Long.parseLong(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private static Path resolveDefaultProfilesPath() {
        Path fromWorkingDir = Paths.get(DEFAULT_PROFILES_FILE).toAbsolutePath().normalize();
        if (Files.isRegularFile(fromWorkingDir)) {
            return fromWorkingDir;
        }

        Path appDir = resolveAppDirectory();
        if (appDir != null) {
            Path fromAppDir = appDir.resolve(DEFAULT_PROFILES_FILE).toAbsolutePath().normalize();
            if (Files.isRegularFile(fromAppDir)) {
                return fromAppDir;
            }
        }

        return fromWorkingDir;
    }

    private static Path resolveAppDirectory() {
        try {
            java.net.URL location = InquisitorSwingUI.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            Path codeLocation = Paths.get(location.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(codeLocation)) {
                return codeLocation.getParent();
            }
            if (Files.isDirectory(codeLocation)) {
                return codeLocation;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static Path findExistingDirectory(Path path) {
        if (path == null) {
            return null;
        }

        Path current = path.toAbsolutePath().normalize();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }
        while (current != null && !Files.isDirectory(current)) {
            current = current.getParent();
        }
        return current;
    }

    private InquisitorSwingUI() {
        super("Inquisitor Exam Generator");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel top = buildTopPanel();
        JScrollPane qaScroll = new JScrollPane(qaPanel);
        qaScroll.setBorder(BorderFactory.createTitledBorder("Question Files (.qa)"));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Execution Log"));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(new EmptyBorder(8, 8, 8, 8));
        center.add(qaScroll, BorderLayout.CENTER);
        center.add(logScroll, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        loadInitialData();
        wireActions();

        pack();
        setMinimumSize(new Dimension(760, 700));
        setLocationRelativeTo(null);
    }

    private JPanel buildTopPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(8, 8, 0, 8));

        JPanel global = new JPanel(new GridBagLayout());
        global.setBorder(BorderFactory.createTitledBorder("Course Parameters"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int r = 0;
        addLabeled(global, gbc, r++, "Heading (-h)", headingField);
        addLabeled(global, gbc, r++, "Subheading (-h2)", subheadingField);
        addLabeled(global, gbc, r++, "Seed (-s)", seedSpinner);
        addLabeled(global, gbc, r++, "Total exams (-t)", examsSpinner);
        addLabeled(global, gbc, r++, "Students (-st)", studentsSpinner);

        JPanel course = new JPanel(new GridBagLayout());
        course.setBorder(BorderFactory.createTitledBorder("Course / QA Set"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        course.add(new JLabel("Course"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        course.add(courseCombo, c);

        JButton addCourse = new JButton("Add Course");
        addCourse.setActionCommand("addCourse");
        JButton removeCourse = new JButton("Remove Course");
        removeCourse.setActionCommand("removeCourse");
        JButton saveProfile = new JButton("Save Profile");
        saveProfile.setActionCommand("saveProfile");
        JButton loadProfile = new JButton("Load Profile");
        loadProfile.setActionCommand("loadProfile");
        JButton aboutButton = new JButton("About");
        aboutButton.setActionCommand("about");

        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        course.add(addCourse, c);
        c.gridx = 3;
        course.add(removeCourse, c);
        c.gridx = 4;
        course.add(aboutButton, c);

        c.gridx = 0; c.gridy = 1;
        course.add(new JLabel("Questions Folder (--base_path)"), c);

        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        course.add(basePathField, c);

        JButton browse = new JButton("Browse...");
        browse.setActionCommand("browsePath");
        JButton refresh = new JButton("Refresh .qa Files");
        refresh.setActionCommand("refreshQa");

        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        course.add(browse, c);
        c.gridx = 3;
        c.gridwidth = 2;
        course.add(refresh, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = 2;
        course.add(new JLabel("Profile name"), c);

        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        course.add(profileNameField, c);

        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        course.add(saveProfile, c);
        c.gridx = 3;
        course.add(loadProfile, c);

        c.gridx = 0; c.gridy = 3;
        course.add(new JLabel("Profile file"), c);

        profilesFileField.setEditable(false);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        c.gridwidth = 3;
        course.add(profilesFileField, c);
        c.gridwidth = 1;

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(compilePdfBox);
        actions.add(generateButton);
        actions.add(openPdfButton);
        actions.add(Box.createHorizontalStrut(16));
        actions.add(totalQuestionsLabel);
        openPdfButton.setEnabled(false);

        JPanel upper = new JPanel(new GridLayout(2, 1, 8, 8));
        upper.add(global);
        upper.add(course);

        root.add(upper, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        root.putClientProperty("addCourse", addCourse);
        root.putClientProperty("removeCourse", removeCourse);
        root.putClientProperty("saveProfile", saveProfile);
        root.putClientProperty("loadProfile", loadProfile);
        root.putClientProperty("about", aboutButton);
        root.putClientProperty("browsePath", browse);
        root.putClientProperty("refreshQa", refresh);

        return root;
    }

    private void addLabeled(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(comp, gbc);
    }

    private void wireActions() {
        JPanel topPanel = (JPanel) getContentPane().getComponent(0);
        JButton addCourse = (JButton) topPanel.getClientProperty("addCourse");
        JButton removeCourse = (JButton) topPanel.getClientProperty("removeCourse");
        JButton saveProfile = (JButton) topPanel.getClientProperty("saveProfile");
        JButton loadProfile = (JButton) topPanel.getClientProperty("loadProfile");
        JButton aboutButton = (JButton) topPanel.getClientProperty("about");
        JButton browsePath = (JButton) topPanel.getClientProperty("browsePath");
        JButton refreshQa = (JButton) topPanel.getClientProperty("refreshQa");

        addCourse.addActionListener(this::onAddCourse);
        removeCourse.addActionListener(this::onRemoveCourse);
        saveProfile.addActionListener(this::onSaveProfile);
        loadProfile.addActionListener(this::onLoadProfile);
        aboutButton.addActionListener(this::onAbout);

        browsePath.addActionListener(this::onBrowsePath);
        refreshQa.addActionListener(e -> refreshQaPanel());

        courseCombo.addActionListener(e -> {
            if (!suppressCourseEvents) {
                onCourseChanged();
            }
        });

        generateButton.addActionListener(e -> runGeneration());
        openPdfButton.addActionListener(e -> openGeneratedPdf());

        headingField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshPdfButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshPdfButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshPdfButtonState();
            }
        });
        seedSpinner.addChangeListener(e -> refreshPdfButtonState());
    }

    private void loadInitialData() {
        setProfileFilePath(profilesFilePath);
        profileNameField.setText(deriveProfileNameFromPath(profilesFilePath));

        ScriptDefaults defaults = ScriptDefaults.load();

        SavedProfile loaded = loadProfile(profilesFilePath);
        if (loaded == null) {
            loaded = createFallbackProfile(defaults);
        } else if (loaded.courses.isEmpty()) {
            appendLog("No courses found in " + profilesFilePath + ". Loaded fallback Default course.");
            loaded = mergeWithFallbackCourse(loaded, defaults);
        } else {
            appendLog("Loaded profile '" + loaded.name + "' from " + profilesFilePath);
        }
        applyLoadedProfile(loaded);
    }

    private void onCourseChanged() {
        saveCurrentCourseFromUI();
        CourseProfile selected = (CourseProfile) courseCombo.getSelectedItem();
        currentCourse = selected;
        if (selected != null) {
            applyCourseToUI(selected);
        }
    }

    private void onAddCourse(ActionEvent e) {
        String name = JOptionPane.showInputDialog(this, "Course name:", "Add Course", JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }

        saveCurrentCourseFromUI();

        Path selectedDirectory = chooseDirectory("Select questions folder for " + name, Paths.get("."));
        if (selectedDirectory == null) {
            return;
        }

        CourseProfile profile = new CourseProfile(name.trim(), selectedDirectory.toString());
        profile.heading = headingField.getText().trim();
        profile.subheading = subheadingField.getText().trim();
        profile.totalExams = ((Number) examsSpinner.getValue()).intValue();
        profile.totalStudents = ((Number) studentsSpinner.getValue()).intValue();
        courseModel.addElement(profile);
        courseCombo.setSelectedItem(profile);
    }

    private void onRemoveCourse(ActionEvent e) {
        CourseProfile selected = (CourseProfile) courseCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Remove course '" + selected.name + "'?",
                "Confirm removal",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        courseModel.removeElement(selected);
        if (courseModel.getSize() > 0) {
            courseCombo.setSelectedIndex(0);
        } else {
            currentCourse = null;
            headingField.setText("");
            subheadingField.setText("");
            basePathField.setText("");
            examsSpinner.setValue(8);
            studentsSpinner.setValue(120);
            qaPanel.removeAll();
            qaPanel.revalidate();
            qaPanel.repaint();
            refreshTotalQuestionsLabel();
            refreshPdfButtonState();
        }
    }

    private void onBrowsePath(ActionEvent e) {
        Path initialDirectory = basePathField.getText().isBlank() ? null : Paths.get(basePathField.getText().trim());
        Path selectedDirectory = chooseDirectory("Select questions folder", initialDirectory);
        if (selectedDirectory == null) {
            return;
        }

        basePathField.setText(selectedDirectory.toString());
        refreshQaPanel();
    }

    private void onSaveProfile(ActionEvent e) {
        SavedProfile profile = captureCurrentProfile();
        if (profile.name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Profile name cannot be empty.", "Save Profile", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String suggestedFileName = suggestedProfileFileName(profile.name);

        Path currentParent = profilesFilePath.toAbsolutePath().normalize().getParent();
        if (currentParent == null) {
            currentParent = Paths.get(System.getProperty("user.dir"));
        }

        Path selected = chooseSaveFile(
                "Save profile",
                currentParent,
                suggestedFileName
        );
        if (selected == null) {
            return;
        }

        selected = ensureProfileFileExtension(selected);
        Path normalizedSelected = selected.toAbsolutePath().normalize();
        Path normalizedCurrent = (profilesFilePath == null) ? null : profilesFilePath.toAbsolutePath().normalize();
        if (Files.exists(normalizedSelected) && !normalizedSelected.equals(normalizedCurrent)) {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    "Overwrite existing file?\n" + normalizedSelected,
                    "Save Profile",
                    JOptionPane.YES_NO_OPTION
            );
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (!saveProfile(profile, normalizedSelected)) {
            return;
        }

        setProfileFilePath(normalizedSelected);
        profileNameField.setText(profile.name);
        appendLog("Saved profile '" + profile.name + "' to " + normalizedSelected);
    }

    private void onLoadProfile(ActionEvent e) {
        Path currentParent = profilesFilePath.toAbsolutePath().normalize().getParent();
        if (currentParent == null) {
            currentParent = Paths.get(System.getProperty("user.dir"));
        }

        String suggestedFileName = (profilesFilePath != null && profilesFilePath.getFileName() != null)
                ? profilesFilePath.getFileName().toString()
                : suggestedProfileFileName(profileNameField.getText().trim());

        Path selected = chooseFile(
                "Load profile",
                currentParent,
                suggestedFileName
        );
        if (selected == null) {
            return;
        }

        if (!Files.isRegularFile(selected)) {
            JOptionPane.showMessageDialog(this, "Profile file not found: " + selected, "Load Profile", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SavedProfile loaded = loadProfile(selected);
        if (loaded == null) {
            JOptionPane.showMessageDialog(this, "Failed to load profile: " + selected, "Load Profile", JOptionPane.ERROR_MESSAGE);
            return;
        }
        setProfileFilePath(selected);
        if (loaded.courses.isEmpty()) {
            loaded = mergeWithFallbackCourse(loaded, ScriptDefaults.load());
            appendLog("No courses found in " + selected + ". Loaded fallback Default course.");
        } else {
            appendLog("Loaded profile '" + loaded.name + "' from " + selected);
        }

        applyLoadedProfile(loaded);
    }

    private void onAbout(ActionEvent e) {
        String message = "Author: Davide Patti\n"
                + "Version: " + ABOUT_VERSION + "\n"
                + "Email: xedivad@gmail.com";
        JOptionPane.showMessageDialog(this, message, "About Inquisitor", JOptionPane.INFORMATION_MESSAGE);
    }

    private void applyLoadedProfile(SavedProfile loaded) {
        suppressCourseEvents = true;
        profileNameField.setText((loaded.name == null || loaded.name.isBlank())
                ? deriveProfileNameFromPath(profilesFilePath)
                : loaded.name);
        if (loaded.compilePdf != null) {
            compilePdfBox.setSelected(loaded.compilePdf);
        }

        courseModel.removeAllElements();
        for (CourseProfile c : loaded.courses) {
            courseModel.addElement(c);
        }
        if (courseModel.getSize() > 0) {
            int selectedIndex = (loaded.selectedCourseIndex == null) ? 0 : loaded.selectedCourseIndex;
            selectedIndex = Math.max(0, Math.min(selectedIndex, courseModel.getSize() - 1));
            courseCombo.setSelectedIndex(selectedIndex);
            currentCourse = courseModel.getElementAt(selectedIndex);
            applyCourseToUI(currentCourse);
        } else {
            currentCourse = null;
            headingField.setText("");
            subheadingField.setText("");
            basePathField.setText("");
            examsSpinner.setValue(8);
            studentsSpinner.setValue(120);
            qaPanel.removeAll();
            qaPanel.revalidate();
            qaPanel.repaint();
            refreshTotalQuestionsLabel();
            refreshPdfButtonState();
        }
        suppressCourseEvents = false;
        refreshPdfButtonState();
    }

    private void refreshQaPanel() {
        qaPanel.removeAll();
        fileSpinners.clear();

        Path basePath = Paths.get(basePathField.getText().trim());
        if (!Files.isDirectory(basePath)) {
            addQaMessage("Directory not found: " + basePath);
            qaPanel.revalidate();
            qaPanel.repaint();
            refreshTotalQuestionsLabel();
            refreshPdfButtonState();
            return;
        }

        List<Path> qaFiles;
        try {
            qaFiles = listQaFiles(basePath);
        } catch (IOException ex) {
            addQaMessage("Failed to read .qa files: " + ex.getMessage());
            qaPanel.revalidate();
            qaPanel.repaint();
            refreshTotalQuestionsLabel();
            refreshPdfButtonState();
            return;
        }

        if (qaFiles.isEmpty()) {
            addQaMessage("No .qa files found in " + basePath);
            qaPanel.revalidate();
            qaPanel.repaint();
            refreshTotalQuestionsLabel();
            refreshPdfButtonState();
            return;
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        for (Path file : qaFiles) {
            String fileName = file.getFileName().toString();
            int available = countQuestions(file);
            int defaultValue = (currentCourse != null) ? currentCourse.questionCounts.getOrDefault(fileName, 0) : 0;
            defaultValue = Math.max(0, Math.min(defaultValue, available));

            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            qaPanel.add(new JLabel(fileName), gbc);

            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            qaPanel.add(new JLabel("available: " + available), gbc);

            JSpinner spinner = new JSpinner(new SpinnerNumberModel(defaultValue, 0, Math.max(available, 0), 1));
            spinner.setPreferredSize(new Dimension(70, spinner.getPreferredSize().height));
            spinner.addChangeListener(e -> refreshTotalQuestionsLabel());
            gbc.gridx = 2;
            qaPanel.add(spinner, gbc);

            fileSpinners.put(fileName, spinner);
            row++;
        }

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.weightx = 1;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.BOTH;
        qaPanel.add(Box.createGlue(), filler);

        qaPanel.revalidate();
        qaPanel.repaint();
        refreshTotalQuestionsLabel();
        refreshPdfButtonState();
    }

    private List<Path> listQaFiles(Path basePath) throws IOException {
        List<Path> qaFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(basePath,
                entry -> Files.isRegularFile(entry)
                        && entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".qa"))) {
            for (Path entry : stream) {
                qaFiles.add(entry);
            }
        }
        qaFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return qaFiles;
    }

    private Path chooseDirectory(String title, Path initialPath) {
        Path initialDirectory = findExistingDirectory(initialPath);
        if (isMacOs()) {
            return chooseDirectoryWithNativeDialog(title, initialDirectory);
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (initialDirectory != null) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return null;
        }
        return chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
    }

    private Path chooseDirectoryWithNativeDialog(String title, Path initialDirectory) {
        String previous = System.getProperty("apple.awt.fileDialogForDirectories");
        FileDialog dialog = null;
        try {
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            dialog = new FileDialog(this, title, FileDialog.LOAD);
            if (initialDirectory != null) {
                dialog.setDirectory(initialDirectory.toString());
            }
            dialog.setVisible(true);

            File[] selectedFiles = dialog.getFiles();
            if (selectedFiles != null && selectedFiles.length > 0) {
                return selectedFiles[0].toPath().toAbsolutePath().normalize();
            }

            if (dialog.getDirectory() == null) {
                return null;
            }
            if (dialog.getFile() == null) {
                return Paths.get(dialog.getDirectory()).toAbsolutePath().normalize();
            }
            return Paths.get(dialog.getDirectory(), dialog.getFile()).toAbsolutePath().normalize();
        } finally {
            if (dialog != null) {
                dialog.dispose();
            }
            if (previous == null) {
                System.clearProperty("apple.awt.fileDialogForDirectories");
            } else {
                System.setProperty("apple.awt.fileDialogForDirectories", previous);
            }
        }
    }

    private Path chooseFile(String title, Path initialPath, String suggestedFileName) {
        Path initialDirectory = findExistingDirectory(initialPath);
        if (isMacOs()) {
            return chooseFileWithNativeDialog(title, initialDirectory, suggestedFileName);
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (initialDirectory != null) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }
        if (suggestedFileName != null && !suggestedFileName.isBlank()) {
            chooser.setSelectedFile(new File(
                    initialDirectory != null ? initialDirectory.toFile() : new File("."),
                    suggestedFileName
            ));
        }

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return null;
        }
        return chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
    }

    private Path chooseFileWithNativeDialog(String title, Path initialDirectory, String suggestedFileName) {
        FileDialog dialog = new FileDialog(this, title, FileDialog.LOAD);
        try {
            if (initialDirectory != null) {
                dialog.setDirectory(initialDirectory.toString());
            }
            if (suggestedFileName != null && !suggestedFileName.isBlank()) {
                dialog.setFile(suggestedFileName);
            }
            dialog.setVisible(true);

            File[] selectedFiles = dialog.getFiles();
            if (selectedFiles == null || selectedFiles.length == 0) {
                return null;
            }
            return selectedFiles[0].toPath().toAbsolutePath().normalize();
        } finally {
            dialog.dispose();
        }
    }

    private Path chooseSaveFile(String title, Path initialPath, String suggestedFileName) {
        Path initialDirectory = findExistingDirectory(initialPath);
        if (isMacOs()) {
            return chooseSaveFileWithNativeDialog(title, initialDirectory, suggestedFileName);
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (initialDirectory != null) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }
        if (suggestedFileName != null && !suggestedFileName.isBlank()) {
            chooser.setSelectedFile(new File(
                    initialDirectory != null ? initialDirectory.toFile() : new File("."),
                    suggestedFileName
            ));
        }

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return null;
        }
        return chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
    }

    private Path chooseSaveFileWithNativeDialog(String title, Path initialDirectory, String suggestedFileName) {
        FileDialog dialog = new FileDialog(this, title, FileDialog.SAVE);
        try {
            if (initialDirectory != null) {
                dialog.setDirectory(initialDirectory.toString());
            }
            if (suggestedFileName != null && !suggestedFileName.isBlank()) {
                dialog.setFile(suggestedFileName);
            }
            dialog.setVisible(true);

            if (dialog.getDirectory() == null || dialog.getFile() == null) {
                return null;
            }
            return Paths.get(dialog.getDirectory(), dialog.getFile()).toAbsolutePath().normalize();
        } finally {
            dialog.dispose();
        }
    }

    private void setProfileFilePath(Path path) {
        profilesFilePath = path.toAbsolutePath().normalize();
        profilesFileField.setText(profilesFilePath.toString());
        profilesFileField.setToolTipText(profilesFilePath.toString());
    }

    private String deriveProfileNameFromPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return "Default";
        }

        String fileName = path.getFileName().toString().trim();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            fileName = fileName.substring(0, dot);
        }
        return fileName.isBlank() ? "Default" : fileName;
    }

    private String suggestedProfileFileName(String profileName) {
        String sanitized = profileName == null ? "" : profileName.trim();
        sanitized = sanitized.replaceAll("[\\\\/:*?\"<>|]+", "_");
        sanitized = sanitized.replaceAll("\\s+", "_");
        if (sanitized.isBlank()) {
            sanitized = "profile";
        }
        if (!sanitized.toLowerCase(Locale.ROOT).endsWith(".properties")) {
            sanitized += ".properties";
        }
        return sanitized;
    }

    private Path ensureProfileFileExtension(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".properties")) {
            return path;
        }
        String updatedFileName = fileName.isBlank() ? "profile.properties" : fileName + ".properties";
        Path parent = path.getParent();
        return (parent == null) ? Paths.get(updatedFileName) : parent.resolve(updatedFileName);
    }

    private SavedProfile captureCurrentProfile() {
        saveCurrentCourseFromUI();

        SavedProfile profile = new SavedProfile();
        profile.name = profileNameField.getText().trim();
        profile.compilePdf = compilePdfBox.isSelected();
        profile.selectedCourseIndex = Math.max(courseCombo.getSelectedIndex(), 0);

        for (int i = 0; i < courseModel.getSize(); i++) {
            profile.courses.add(copyCourseProfile(courseModel.getElementAt(i)));
        }
        return profile;
    }

    private SavedProfile createFallbackProfile(ScriptDefaults defaults) {
        SavedProfile profile = new SavedProfile();
        profile.name = deriveProfileNameFromPath(profilesFilePath);
        profile.compilePdf = compilePdfBox.isSelected();
        profile.selectedCourseIndex = 0;

        CourseProfile imported = defaults.toCourseProfile();
        if (imported != null) {
            profile.courses.add(imported);
        }
        if (profile.courses.isEmpty()) {
            profile.courses.add(new CourseProfile("Default", "./questions"));
        }
        return profile;
    }

    private SavedProfile mergeWithFallbackCourse(SavedProfile profile, ScriptDefaults defaults) {
        if (profile.name == null || profile.name.isBlank()) {
            profile.name = deriveProfileNameFromPath(profilesFilePath);
        }
        if (profile.courses.isEmpty()) {
            CourseProfile imported = defaults.toCourseProfile();
            if (imported != null) {
                profile.courses.add(imported);
            }
        }
        if (profile.courses.isEmpty()) {
            profile.courses.add(new CourseProfile("Default", "./questions"));
        }
        if (profile.selectedCourseIndex == null) {
            profile.selectedCourseIndex = 0;
        }
        return profile;
    }

    private CourseProfile copyCourseProfile(CourseProfile source) {
        CourseProfile copy = new CourseProfile(source.name, source.basePath);
        copy.heading = source.heading;
        copy.subheading = source.subheading;
        copy.totalExams = source.totalExams;
        copy.totalStudents = source.totalStudents;
        copy.questionCounts.putAll(source.questionCounts);
        return copy;
    }

    private void applyCourseToUI(CourseProfile course) {
        headingField.setText(course.heading);
        subheadingField.setText(course.subheading);
        examsSpinner.setValue(course.totalExams);
        studentsSpinner.setValue(course.totalStudents);
        basePathField.setText(course.basePath);
        refreshQaPanel();
    }

    private void addQaMessage(String text) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        qaPanel.add(new JLabel(text), gbc);
    }

    private int countQuestions(Path qaFile) {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(qaFile)) {
            String line;
            boolean inQuestionBlock = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                int openIdx = trimmed.indexOf("[Q]");
                int closeIdx = trimmed.indexOf("[/Q]");

                if (!inQuestionBlock) {
                    if (openIdx >= 0) {
                        if (closeIdx >= 0 && closeIdx > openIdx) {
                            count++;
                        } else {
                            inQuestionBlock = true;
                        }
                    }
                } else if (closeIdx >= 0) {
                    count++;
                    inQuestionBlock = false;
                }
            }
        } catch (IOException ignored) {
            return 0;
        }
        return count;
    }

    private void saveCurrentCourseFromUI() {
        if (currentCourse == null) {
            return;
        }
        currentCourse.heading = headingField.getText().trim();
        currentCourse.subheading = subheadingField.getText().trim();
        currentCourse.basePath = basePathField.getText().trim();
        currentCourse.totalExams = ((Number) examsSpinner.getValue()).intValue();
        currentCourse.totalStudents = ((Number) studentsSpinner.getValue()).intValue();
        currentCourse.questionCounts.clear();
        for (Map.Entry<String, JSpinner> entry : fileSpinners.entrySet()) {
            int value = (Integer) entry.getValue().getValue();
            if (value > 0) {
                currentCourse.questionCounts.put(entry.getKey(), value);
            }
        }
    }

    private void runGeneration() {
        saveCurrentCourseFromUI();
        if (currentCourse == null) {
            JOptionPane.showMessageDialog(this, "No course selected.", "Validation error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String basePath = basePathField.getText().trim();
        if (!Files.isDirectory(Paths.get(basePath))) {
            JOptionPane.showMessageDialog(this, "Base path is not a valid directory: " + basePath, "Validation error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String heading = headingField.getText().trim();
        if (heading.isBlank()) {
            JOptionPane.showMessageDialog(this, "Heading cannot be empty.", "Validation error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> args = new ArrayList<>();
        args.add("--base_path");
        args.add(basePath);

        int selectedFiles = 0;
        for (Map.Entry<String, JSpinner> entry : fileSpinners.entrySet()) {
            int n = (Integer) entry.getValue().getValue();
            if (n > 0) {
                args.add(Integer.toString(n));
                args.add(entry.getKey());
                selectedFiles++;
            }
        }

        if (selectedFiles == 0) {
            JOptionPane.showMessageDialog(this, "Select at least one question from at least one .qa file.", "Validation error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        long seed = ((Number) seedSpinner.getValue()).longValue();
        int totalExams = ((Number) examsSpinner.getValue()).intValue();
        int totalStudents = ((Number) studentsSpinner.getValue()).intValue();
        String subheading = subheadingField.getText().trim();

        args.add("-t");
        args.add(Integer.toString(totalExams));
        args.add("-st");
        args.add(Integer.toString(totalStudents));
        args.add("-s");
        args.add(Long.toString(seed));
        args.add("-h");
        args.add(heading);
        if (!subheading.isBlank()) {
            args.add("-h2");
            args.add(subheading);
        }

        generateButton.setEnabled(false);
        appendLog("Running Inquisitor with course: " + currentCourse.name);
        appendLog("Arguments: " + String.join(" ", quoteForLog(args)));

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int code = runJavaInquisitor(args, this::publish);
                if (code != 0) {
                    return code;
                }
                if (compilePdfBox.isSelected()) {
                    int texCode = compileLatex(basePath, heading, seed, this::publish);
                    if (texCode != 0) {
                        return texCode;
                    }
                }
                return 0;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    appendLog(line);
                }
            }

            @Override
            protected void done() {
                generateButton.setEnabled(true);
                try {
                    int exit = get();
                    if (exit == 0) {
                        appendLog("Generation completed successfully.");
                        refreshPdfButtonState();
                    } else {
                        appendLog("Generation failed with exit code: " + exit);
                    }
                } catch (Exception ex) {
                    appendLog("Execution failed: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    private int runJavaInquisitor(List<String> args, java.util.function.Consumer<String> logger) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(getJavaCommand());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("Inquisitor");
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.accept(line);
            }
        }

        return process.waitFor();
    }

    private int compileLatex(String basePath, String heading, long seed, java.util.function.Consumer<String> logger) throws IOException, InterruptedException {
        Path outputDirPath = getOutputDirPath(basePath, heading, seed);
        String outputDir = outputDirPath.toString();
        String texFile = seed + "_all_exams.tex";

        Path dir = outputDirPath;
        if (!Files.isDirectory(dir)) {
            logger.accept("Skipping pdflatex: output directory not found: " + outputDir);
            return 0;
        }

        logger.accept("Compiling PDF in " + outputDir + " ...");
        int first = runPdflatex(dir, texFile, logger);
        if (first != 0) {
            return first;
        }
        return runPdflatex(dir, texFile, logger);
    }

    private int runPdflatex(Path dir, String texFile, java.util.function.Consumer<String> logger) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("pdflatex", texFile);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.accept(line);
            }
        }

        return process.waitFor();
    }

    private String getJavaCommand() {
        String javaHome = System.getProperty("java.home");
        Path javaBin = Paths.get(javaHome, "bin", "java");
        if (Files.isExecutable(javaBin)) {
            return javaBin.toString();
        }
        return "java";
    }

    private String sanitizeHeading(String heading) {
        return heading.replaceAll("\\s+", "_");
    }

    private Path getOutputDirPath(String basePath, String heading, long seed) {
        Path base = (basePath == null || basePath.isBlank()) ? Paths.get(".") : Paths.get(basePath);
        String outputDir = sanitizeHeading(heading) + "_" + seed;
        return base.resolve(outputDir);
    }

    private Path getExpectedPdfPath(String basePath, String heading, long seed) {
        Path outputDir = getOutputDirPath(basePath, heading, seed);
        String pdfFile = seed + "_all_exams.pdf";
        return outputDir.resolve(pdfFile);
    }

    private void refreshPdfButtonState() {
        String basePath = basePathField.getText().trim();
        String heading = headingField.getText().trim();
        long seed = ((Number) seedSpinner.getValue()).longValue();
        Path expectedPdf = getExpectedPdfPath(basePath, heading, seed);
        if (Files.exists(expectedPdf)) {
            lastGeneratedPdf = expectedPdf;
            openPdfButton.setEnabled(true);
        } else if (lastGeneratedPdf == null || !Files.exists(lastGeneratedPdf)) {
            openPdfButton.setEnabled(false);
        }
    }

    private void openGeneratedPdf() {
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(this, "Desktop open is not supported on this system.", "Open PDF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String basePath = basePathField.getText().trim();
        String heading = headingField.getText().trim();
        long seed = ((Number) seedSpinner.getValue()).longValue();
        Path expectedPdf = getExpectedPdfPath(basePath, heading, seed);
        Path candidate = Files.exists(expectedPdf) ? expectedPdf : lastGeneratedPdf;

        if (candidate == null || !Files.exists(candidate)) {
            JOptionPane.showMessageDialog(this,
                    "PDF not found.\nGenerate and compile first, or check heading/seed.",
                    "Open PDF",
                    JOptionPane.ERROR_MESSAGE);
            openPdfButton.setEnabled(false);
            return;
        }

        try {
            Desktop.getDesktop().open(candidate.toFile());
            appendLog("Opening PDF: " + candidate);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open PDF: " + ex.getMessage(), "Open PDF", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<String> quoteForLog(List<String> args) {
        List<String> quoted = new ArrayList<>(args.size());
        for (String arg : args) {
            if (arg.contains(" ")) {
                quoted.add('"' + arg + '"');
            } else {
                quoted.add(arg);
            }
        }
        return quoted;
    }

    private void appendLog(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void refreshTotalQuestionsLabel() {
        int totalSelected = 0;
        for (JSpinner spinner : fileSpinners.values()) {
            totalSelected += ((Number) spinner.getValue()).intValue();
        }
        totalQuestionsLabel.setText("Total selected questions: " + totalSelected);
    }

    private SavedProfile loadProfile(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        Path profilesDir = normalizedPath.getParent();

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(normalizedPath)) {
            props.load(in);
        } catch (IOException ex) {
            appendLog("Failed to load " + normalizedPath + ": " + ex.getMessage());
            return null;
        }

        SavedProfile profile = new SavedProfile();
        profile.name = readOptionalProperty(props, "profile.name");
        profile.compilePdf = parseBoolean(readOptionalProperty(props, "profile.compilePdf"));
        profile.selectedCourseIndex = parseInteger(readOptionalProperty(props, "profile.selectedCourse"));

        String legacyHeading = readOptionalProperty(props, "profile.heading");
        String legacySubheading = readOptionalProperty(props, "profile.subheading");
        Integer legacyTotalExams = parseInteger(readOptionalProperty(props, "profile.totalExams"));
        Integer legacyTotalStudents = parseInteger(readOptionalProperty(props, "profile.totalStudents"));

        String order = props.getProperty("courses", "").trim();
        if (!order.isEmpty()) {
            for (String id : order.split(",")) {
                String courseId = id.trim();
                if (courseId.isEmpty()) {
                    continue;
                }

                String prefix = "course." + courseId + ".";
                String name = props.getProperty(prefix + "name");
                String basePath = props.getProperty(prefix + "path");
                if (name == null || basePath == null) {
                    continue;
                }

                Path resolvedBasePath;
                try {
                    resolvedBasePath = Paths.get(basePath);
                } catch (Exception ex) {
                    continue;
                }
                if (!resolvedBasePath.isAbsolute() && profilesDir != null) {
                    resolvedBasePath = profilesDir.resolve(resolvedBasePath).normalize();
                }

                CourseProfile course = new CourseProfile(name, resolvedBasePath.toString());
                course.heading = props.containsKey(prefix + "heading")
                        ? props.getProperty(prefix + "heading", "")
                        : (legacyHeading == null ? "" : legacyHeading);
                course.subheading = props.containsKey(prefix + "subheading")
                        ? props.getProperty(prefix + "subheading", "")
                        : (legacySubheading == null ? "" : legacySubheading);
                course.totalExams = coalesce(
                        parseInteger(readOptionalProperty(props, prefix + "totalExams")),
                        legacyTotalExams,
                        8
                );
                course.totalStudents = coalesce(
                        parseInteger(readOptionalProperty(props, prefix + "totalStudents")),
                        legacyTotalStudents,
                        120
                );
                parseCounts(props.getProperty(prefix + "counts", ""), course.questionCounts);
                profile.courses.add(course);
            }
        }

        if (profile.name == null || profile.name.isBlank()) {
            profile.name = deriveProfileNameFromPath(normalizedPath);
        }
        return profile;
    }

    private boolean saveProfile(SavedProfile profile, Path path) {
        Properties props = new Properties();
        List<String> ids = new ArrayList<>();

        props.setProperty("profile.name", profile.name);
        props.setProperty("profile.compilePdf", Boolean.toString(profile.compilePdf == null || profile.compilePdf));
        props.setProperty("profile.selectedCourse", Integer.toString(profile.selectedCourseIndex == null ? 0 : profile.selectedCourseIndex));

        for (int i = 0; i < profile.courses.size(); i++) {
            CourseProfile course = profile.courses.get(i);
            String id = Integer.toString(i + 1);
            ids.add(id);

            String prefix = "course." + id + ".";
            props.setProperty(prefix + "name", course.name);
            props.setProperty(prefix + "path", toStoredProfilePath(course.basePath, path));
            props.setProperty(prefix + "heading", course.heading == null ? "" : course.heading);
            props.setProperty(prefix + "subheading", course.subheading == null ? "" : course.subheading);
            props.setProperty(prefix + "totalExams", Integer.toString(course.totalExams));
            props.setProperty(prefix + "totalStudents", Integer.toString(course.totalStudents));
            props.setProperty(prefix + "counts", formatCounts(course.questionCounts));
        }

        props.setProperty("courses", String.join(",", ids));

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (IOException ex) {
            appendLog("Failed to prepare directory for " + path + ": " + ex.getMessage());
            return false;
        }

        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "Inquisitor saved profile");
            return true;
        } catch (IOException ex) {
            appendLog("Failed to save " + path + ": " + ex.getMessage());
            return false;
        }
    }

    private String readOptionalProperty(Properties props, String key) {
        return props.containsKey(key) ? props.getProperty(key) : null;
    }

    private Boolean parseBoolean(String text) {
        if (text == null) {
            return null;
        }
        return Boolean.parseBoolean(text.trim());
    }

    private Integer parseInteger(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toStoredProfilePath(String basePath, Path profileFilePath) {
        String raw = (basePath == null) ? "" : basePath.trim();
        if (raw.isEmpty()) {
            return raw;
        }

        Path profilesDir = profileFilePath.toAbsolutePath().normalize().getParent();
        if (profilesDir == null) {
            return raw;
        }

        Path path;
        try {
            path = Paths.get(raw);
        } catch (Exception ex) {
            return raw;
        }

        Path absolute = path.isAbsolute() ? path.normalize() : profilesDir.resolve(path).normalize();
        try {
            Path rel = profilesDir.relativize(absolute);
            String relText = rel.toString().replace(File.separatorChar, '/');
            if (!relText.isEmpty() && !relText.startsWith("..") && !rel.isAbsolute()) {
                return relText.startsWith(".") ? relText : "./" + relText;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return raw;
    }

    private int coalesce(Integer primary, Integer fallback, int defaultValue) {
        if (primary != null) {
            return primary;
        }
        if (fallback != null) {
            return fallback;
        }
        return defaultValue;
    }

    private void parseCounts(String raw, Map<String, Integer> target) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String[] pairs = raw.split(";");
        for (String pair : pairs) {
            int idx = pair.lastIndexOf('=');
            if (idx <= 0 || idx >= pair.length() - 1) {
                continue;
            }
            String file = pair.substring(0, idx).trim();
            String value = pair.substring(idx + 1).trim();
            try {
                int n = Integer.parseInt(value);
                if (n > 0) {
                    target.put(file, n);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private String formatCounts(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static class CourseProfile {
        private String name;
        private String heading;
        private String subheading;
        private String basePath;
        private int totalExams;
        private int totalStudents;
        private final Map<String, Integer> questionCounts = new LinkedHashMap<>();

        private CourseProfile(String name, String basePath) {
            this.name = name;
            this.heading = "";
            this.subheading = "";
            this.basePath = basePath;
            this.totalExams = 8;
            this.totalStudents = 120;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class SavedProfile {
        private String name;
        private Boolean compilePdf;
        private Integer selectedCourseIndex;
        private final List<CourseProfile> courses = new ArrayList<>();
    }

    private static class ScriptDefaults {
        private Long seed;
        private Integer totalExams;
        private Integer totalStudents;
        private String heading;
        private String subheading;
        private String basePath;
        private final Map<String, Integer> counts = new LinkedHashMap<>();

        static ScriptDefaults load() {
            ScriptDefaults defaults = new ScriptDefaults();

            Path script = findScript();
            if (script == null) {
                return defaults;
            }

            Pattern keyValuePattern = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
            Pattern qaPattern = Pattern.compile("^\\s*(\\d+)\\s+([^\\s]+\\.qa)(?:\\s+\\\\)?\\s*$");

            try {
                List<String> lines = Files.readAllLines(script);
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    Matcher kv = keyValuePattern.matcher(line);
                    if (kv.matches()) {
                        String key = kv.group(1);
                        String value = unquote(kv.group(2).trim());
                        switch (key) {
                            case "SEED" -> defaults.seed = parseLong(value);
                            case "TOT_EXAMS" -> defaults.totalExams = parseInt(value);
                            case "TOT_STUDENTS" -> defaults.totalStudents = parseInt(value);
                            case "HEADING" -> defaults.heading = value;
                            case "HEADING2" -> defaults.subheading = value;
                            case "BASE_PATH" -> defaults.basePath = value;
                            default -> {
                            }
                        }
                    }

                    Matcher qa = qaPattern.matcher(line);
                    if (qa.matches()) {
                        Integer n = parseInt(qa.group(1));
                        if (n != null && n > 0) {
                            defaults.counts.put(qa.group(2), n);
                        }
                    }
                }
            } catch (IOException ignored) {
            }

            return defaults;
        }

        CourseProfile toCourseProfile() {
            if (basePath == null || basePath.isBlank()) {
                return null;
            }
            String courseName = (heading != null && !heading.isBlank()) ? heading : "Imported course";
            CourseProfile profile = new CourseProfile(courseName, basePath);
            profile.heading = (heading == null) ? "" : heading;
            profile.subheading = (subheading == null) ? "" : subheading;
            if (totalExams != null) {
                profile.totalExams = totalExams;
            }
            if (totalStudents != null) {
                profile.totalStudents = totalStudents;
            }
            profile.questionCounts.putAll(counts);
            return profile;
        }

        private static Path findScript() {
            Path getScript = Paths.get("get_iotst.sh");
            if (Files.exists(getScript)) {
                return getScript;
            }
            Path genScript = Paths.get("gen_iotst.sh");
            if (Files.exists(genScript)) {
                return genScript;
            }
            return null;
        }

        private static String unquote(String value) {
            if (value.length() >= 2) {
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                    return value.substring(1, value.length() - 1);
                }
            }
            return value;
        }

        private static Integer parseInt(String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static Long parseLong(String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
