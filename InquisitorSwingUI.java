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
    private static final String PROFILES_FILE = "courses.properties";

    private final DefaultComboBoxModel<CourseProfile> courseModel = new DefaultComboBoxModel<>();
    private final JComboBox<CourseProfile> courseCombo = new JComboBox<>(courseModel);

    private final JTextField headingField = new JTextField(22);
    private final JTextField subheadingField = new JTextField(22);
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(defaultSeedForToday(), 0L, Long.MAX_VALUE, 1L));
    private final JSpinner examsSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 10000, 1));
    private final JSpinner studentsSpinner = new JSpinner(new SpinnerNumberModel(120, 1, 1000000, 1));

    private final JTextField basePathField = new JTextField(22);
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InquisitorSwingUI ui = new InquisitorSwingUI();
            ui.setVisible(true);
        });
    }

    private static long defaultSeedForToday() {
        return Long.parseLong(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
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
        global.setBorder(BorderFactory.createTitledBorder("Global Parameters"));
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
        JButton saveCourse = new JButton("Save Course");
        saveCourse.setActionCommand("saveCourse");

        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        course.add(addCourse, c);
        c.gridx = 3;
        course.add(removeCourse, c);
        c.gridx = 4;
        course.add(saveCourse, c);

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
        root.putClientProperty("saveCourse", saveCourse);
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
        JButton saveCourse = (JButton) topPanel.getClientProperty("saveCourse");
        JButton browsePath = (JButton) topPanel.getClientProperty("browsePath");
        JButton refreshQa = (JButton) topPanel.getClientProperty("refreshQa");

        addCourse.addActionListener(this::onAddCourse);
        removeCourse.addActionListener(this::onRemoveCourse);
        saveCourse.addActionListener(e -> {
            saveCurrentCourseFromUI();
            saveProfiles();
            appendLog("Saved course profiles to " + PROFILES_FILE);
        });

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
        ScriptDefaults defaults = ScriptDefaults.load();

        if (defaults.heading != null && !defaults.heading.isBlank()) {
            headingField.setText(defaults.heading);
        }
        if (defaults.subheading != null && !defaults.subheading.isBlank()) {
            subheadingField.setText(defaults.subheading);
        }
        if (defaults.seed != null) {
            seedSpinner.setValue(defaults.seed);
        }
        if (defaults.totalExams != null) {
            examsSpinner.setValue(defaults.totalExams);
        }
        if (defaults.totalStudents != null) {
            studentsSpinner.setValue(defaults.totalStudents);
        }

        List<CourseProfile> loaded = loadProfiles();
        if (loaded.isEmpty()) {
            if (defaults.toCourseProfile() != null) {
                loaded.add(defaults.toCourseProfile());
            }
            if (loaded.isEmpty()) {
                loaded.add(new CourseProfile("Default", "./questions"));
            }
        }

        suppressCourseEvents = true;
        for (CourseProfile c : loaded) {
            courseModel.addElement(c);
        }
        if (courseModel.getSize() > 0) {
            courseCombo.setSelectedIndex(0);
            currentCourse = courseModel.getElementAt(0);
            basePathField.setText(currentCourse.basePath);
            refreshQaPanel();
        }
        suppressCourseEvents = false;
        refreshPdfButtonState();
    }

    private void onCourseChanged() {
        saveCurrentCourseFromUI();
        CourseProfile selected = (CourseProfile) courseCombo.getSelectedItem();
        currentCourse = selected;
        if (selected != null) {
            basePathField.setText(selected.basePath);
            refreshQaPanel();
        }
    }

    private void onAddCourse(ActionEvent e) {
        String name = JOptionPane.showInputDialog(this, "Course name:", "Add Course", JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select questions folder for " + name);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File("."));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        CourseProfile profile = new CourseProfile(name.trim(), chooser.getSelectedFile().getPath());
        courseModel.addElement(profile);
        courseCombo.setSelectedItem(profile);
        saveProfiles();
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
            qaPanel.removeAll();
            qaPanel.revalidate();
            qaPanel.repaint();
        }
        saveProfiles();
    }

    private void onBrowsePath(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select questions folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!basePathField.getText().isBlank()) {
            chooser.setCurrentDirectory(new File(basePathField.getText()));
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            basePathField.setText(chooser.getSelectedFile().getPath());
            refreshQaPanel();
        }
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
            return;
        }

        List<Path> qaFiles;
        try {
            qaFiles = Files.list(basePath)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".qa"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            addQaMessage("Failed to read .qa files: " + ex.getMessage());
            qaPanel.revalidate();
            qaPanel.repaint();
            refreshTotalQuestionsLabel();
            return;
        }

        if (qaFiles.isEmpty()) {
            addQaMessage("No .qa files found in " + basePath);
            qaPanel.revalidate();
            qaPanel.repaint();
            refreshTotalQuestionsLabel();
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
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[Q]") && trimmed.endsWith("[/Q]")) {
                    count++;
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
        currentCourse.basePath = basePathField.getText().trim();
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

        saveProfiles();

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
                    int texCode = compileLatex(heading, seed, this::publish);
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

    private int compileLatex(String heading, long seed, java.util.function.Consumer<String> logger) throws IOException, InterruptedException {
        String outputDir = sanitizeHeading(heading) + "_" + seed;
        String texFile = seed + "_all_exams.tex";

        Path dir = Paths.get(outputDir);
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

    private Path getExpectedPdfPath(String heading, long seed) {
        String outputDir = sanitizeHeading(heading) + "_" + seed;
        String pdfFile = seed + "_all_exams.pdf";
        return Paths.get(outputDir).resolve(pdfFile);
    }

    private void refreshPdfButtonState() {
        String heading = headingField.getText().trim();
        long seed = ((Number) seedSpinner.getValue()).longValue();
        Path expectedPdf = getExpectedPdfPath(heading, seed);
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

        String heading = headingField.getText().trim();
        long seed = ((Number) seedSpinner.getValue()).longValue();
        Path expectedPdf = getExpectedPdfPath(heading, seed);
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

    private List<CourseProfile> loadProfiles() {
        Path path = Paths.get(PROFILES_FILE);
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException ex) {
            appendLog("Failed to load " + PROFILES_FILE + ": " + ex.getMessage());
            return new ArrayList<>();
        }

        String order = props.getProperty("courses", "").trim();
        List<CourseProfile> result = new ArrayList<>();
        if (order.isEmpty()) {
            return result;
        }

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

            CourseProfile profile = new CourseProfile(name, basePath);
            parseCounts(props.getProperty(prefix + "counts", ""), profile.questionCounts);
            result.add(profile);
        }

        return result;
    }

    private void saveProfiles() {
        saveCurrentCourseFromUI();

        Properties props = new Properties();
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < courseModel.getSize(); i++) {
            CourseProfile profile = courseModel.getElementAt(i);
            String id = Integer.toString(i + 1);
            ids.add(id);

            String prefix = "course." + id + ".";
            props.setProperty(prefix + "name", profile.name);
            props.setProperty(prefix + "path", profile.basePath);
            props.setProperty(prefix + "counts", formatCounts(profile.questionCounts));
        }

        props.setProperty("courses", String.join(",", ids));

        try (OutputStream out = Files.newOutputStream(Paths.get(PROFILES_FILE))) {
            props.store(out, "Inquisitor course profiles");
        } catch (IOException ex) {
            appendLog("Failed to save " + PROFILES_FILE + ": " + ex.getMessage());
        }
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
        private String basePath;
        private final Map<String, Integer> questionCounts = new LinkedHashMap<>();

        private CourseProfile(String name, String basePath) {
            this.name = name;
            this.basePath = basePath;
        }

        @Override
        public String toString() {
            return name;
        }
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
