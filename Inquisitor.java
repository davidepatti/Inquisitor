import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Inquisitor {
    private static final int ANSWERS_PER_QUESTION = 4;

    private static long defaultSeedForToday() {
        return Long.parseLong(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private static Path resolveOutputBasePath(String basePath, List<String> filteredArgs) {
        if (basePath != null && !basePath.isBlank()) {
            return Paths.get(basePath);
        }
        if (filteredArgs.size() >= 2) {
            Path firstQaPath = Paths.get(filteredArgs.get(1));
            if (firstQaPath.getParent() != null) {
                return firstQaPath.getParent();
            }
        }
        return Paths.get(".");
    }

    private static String generateExamIdPrefix(String commandLineArgs) {
        try {
            String input = commandLineArgs;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) { // First 4 bytes -> 8 hex chars
                String hex = Integer.toHexString(0xff & digest[i]);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    private static String generateObfuscatedExamId(String commandLineArgs, int examNumber) {
        return generateExamIdPrefix(commandLineArgs) + examNumber;
    }

    // Class to represent a question
    static class Question {
        String text;
        List<String> answers;
        int correctAnswerIndex; // Index of the correct answer in the shuffled answers list

        Question(String text, List<String> answers, int correctAnswerIndex) {
            this.text = text;
            this.answers = answers;
            this.correctAnswerIndex = correctAnswerIndex;
        }
    }

    // Class to store exam data
    static class ExamData {
        int examNumber;
        long seed;
        List<Integer> correctAnswerIndices;
        List<Question> questions; // List of questions for the exam

        ExamData(int examNumber, long seed, List<Integer> correctAnswerIndices, List<Question> questions) {
            this.examNumber = examNumber;
            this.seed = seed;
            this.correctAnswerIndices = correctAnswerIndices;
            this.questions = questions;
        }
    }

    public static void main(String[] args) {
        // Parse command-line arguments
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        Long seed = null;
        Integer totalExams = null;
        Integer totalStudents = null; // New argument for total students
        String heading = null;  // For the -h option
        String subheading = null; // For the -h2 option
        String basePath = null; // New argument for base path

        // Parse for --seed or -s, --total_exams or -t, --students or -st, -h, and -h2
        Iterator<String> iterator = argsList.iterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if (arg.equalsIgnoreCase("--seed") || arg.equalsIgnoreCase("-s")) {
                if (iterator.hasNext()) {
                    String seedStr = iterator.next();
                    try {
                        seed = Long.parseLong(seedStr);
                        if (seed < 0) {
                            System.out.println("Seed value must be a non-negative integer. Found: " + seedStr);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid seed value: " + seedStr);
                        return;
                    }
                } else {
                    System.out.println("Seed value missing after " + arg);
                    return;
                }
            } else if (arg.equalsIgnoreCase("--total_exams") || arg.equalsIgnoreCase("-t")) {
                if (iterator.hasNext()) {
                    String totalExamsStr = iterator.next();
                    try {
                        totalExams = Integer.parseInt(totalExamsStr);
                        if (totalExams <= 0) {
                            System.out.println("Total exams must be a positive integer. Found: " + totalExamsStr);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid total exams value: " + totalExamsStr);
                        return;
                    }
                } else {
                    System.out.println("Total exams value missing after " + arg);
                    return;
                }
            } else if (arg.equalsIgnoreCase("--students") || arg.equalsIgnoreCase("-st")) {
                if (iterator.hasNext()) {
                    String totalStudentsStr = iterator.next();
                    try {
                        totalStudents = Integer.parseInt(totalStudentsStr);
                        if (totalStudents <= 0) {
                            System.out.println("Total students must be a positive integer. Found: " + totalStudentsStr);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid total students value: " + totalStudentsStr);
                        return;
                    }
                } else {
                    System.out.println("Total students value missing after " + arg);
                    return;
                }
            } else if (arg.equalsIgnoreCase("-h")) {
                if (iterator.hasNext()) {
                    heading = iterator.next();
                } else {
                    System.out.println("Heading string missing after " + arg);
                    return;
                }
            } else if (arg.equalsIgnoreCase("-h2")) {
                if (iterator.hasNext()) {
                    subheading = iterator.next();
                } else {
                    System.out.println("Subheading string missing after " + arg);
                    return;
                }
            }
            else if (arg.equalsIgnoreCase("--base_path") || arg.equalsIgnoreCase("-b")) {
                if (iterator.hasNext()) {
                    basePath = iterator.next();
                } else {
                    System.out.println("Base path string missing after " + arg);
                    return;
                }
            }
        }

        // After parsing, remove the flags and their values from argsList
        // Reconstruct argsList without flags
        List<String> filteredArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--seed") || arg.equalsIgnoreCase("-s") ||
                arg.equalsIgnoreCase("--total_exams") || arg.equalsIgnoreCase("-t") ||
                arg.equalsIgnoreCase("--students") || arg.equalsIgnoreCase("-st") ||
                arg.equalsIgnoreCase("-h") || arg.equalsIgnoreCase("-h2") ||
                arg.equalsIgnoreCase("--base_path") || arg.equalsIgnoreCase("-b")) {
                i++; // Skip the next argument as it's the value for the flag
            } else {
                filteredArgs.add(arg);
            }
        }

        // Now, filteredArgs contains only the question selection arguments
        if (filteredArgs.size() == 0 || filteredArgs.size() % 2 != 0) {
            System.out.println("Usage: java Inquisitor n1 questions_file1 n2 questions_file2 ... [-h <heading>] [-h2 <subheading>] [--base_path <path>] [--seed <integer>] [--total_exams <integer>] [--students <integer>]");
            System.out.println("Example: java Inquisitor 5 questions1.txt 10 questions2.txt -h \"Midterm Exam\" -h2 \"Calculus Section\" --base_path ./questions --seed 1000 --total_exams 3 --students 30");
            return;
        }

        // Gather command line string for exam ID obfuscation
        String commandLineString = String.join(" ", args);

        if (seed == null) {
            seed = defaultSeedForToday();
            System.out.println("No seed provided. Using date-based seed: " + seed);
        }

        // Set default heading if not provided
        if (heading == null) {
            heading = "Exam";
        }

        // Create output folder name by concatenating HEADING and SEED with an underscore
        String sanitizedHeading = heading.replaceAll("\\s+", "_"); // Replace spaces with underscores
        String outputFolderName = sanitizedHeading + "_" + seed;
        Path outputBasePath = resolveOutputBasePath(basePath, filteredArgs);
        Path outputFolderPath = outputBasePath.resolve(outputFolderName);

        // Create the output folder if it doesn't exist
        if (!Files.exists(outputFolderPath)) {
            try {
                Files.createDirectories(outputFolderPath);
                System.out.println("Created output directory: " + outputFolderPath.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("Error creating output directory: " + outputFolderPath.toAbsolutePath());
                e.printStackTrace();
                return;
            }
        }

        // Initialize output filenames with the shared 8-hex exam ID prefix.
        String filePrefix = generateExamIdPrefix(commandLineString);
        String outputFileName = filePrefix + "_all_exams.tex";
        String highlightedOutputFileName = filePrefix + "_all_exams_correct_answers.tex";
        String csvFileName = filePrefix + "_results.csv";
        String answersKeyFileName = filePrefix + "_answers_key.txt";

        Path outputFilePath = outputFolderPath.resolve(outputFileName);
        Path highlightedOutputFilePath = outputFolderPath.resolve(highlightedOutputFileName);
        Path csvFilePath = outputFolderPath.resolve(csvFileName);
        Path answersKeyFilePath = outputFolderPath.resolve(answersKeyFileName);

        // Initialize a list to collect all ExamData
        List<ExamData> examDataList = new ArrayList<>();

        // Initialize a single Random instance with the provided seed
        Random randomInstance = new Random(seed);

        // Determine the total number of exams
        int examsToGenerate = (totalExams != null) ? totalExams : 1;

        // Generate all exams
        for (int exam = 1; exam <= examsToGenerate; exam++) {
            long currentSeed = seed;
            List<Question> selectedQuestions = generateExam(filteredArgs, basePath, currentSeed, exam, randomInstance);
            if (selectedQuestions == null) {
                System.out.println("Failed to generate Exam " + exam);
                continue;
            }
            // Extract correct answer indices from selectedQuestions
            List<Integer> correctAnswerIndices = new ArrayList<>();
            for (Question q : selectedQuestions) {
                correctAnswerIndices.add(q.correctAnswerIndex);
            }
            ExamData ed = new ExamData(exam, currentSeed, correctAnswerIndices, selectedQuestions); // Pass the questions list
            examDataList.add(ed);
        }

        // Check if any exams were generated
        if (examDataList.isEmpty()) {
            System.out.println("No exams were generated. Exiting.");
            return;
        }

        // Determine T (number of questions) based on the first exam
        int T = examDataList.get(0).correctAnswerIndices.size();

        try {
            writeExamsLatex(outputFilePath, examDataList, commandLineString, heading, subheading, false);
            System.out.println("Generated " + outputFilePath.getFileName() + " successfully.");

            writeExamsLatex(highlightedOutputFilePath, examDataList, commandLineString, heading, subheading, true);
            System.out.println("Generated " + highlightedOutputFilePath.getFileName() + " successfully.");

            // Write results.csv with new columns
            writeResultsCSV(examDataList, csvFilePath.toString(), T, totalStudents);
            System.out.println("Generated " + csvFilePath.getFileName() + " successfully.");

            // Write answers_key.txt
            writeAnswersKey(examDataList, answersKeyFilePath.toString());
            System.out.println("Generated " + answersKeyFileName + " successfully.");

        } catch (IOException e) {
            System.out.println("Error writing generator output.");
            e.printStackTrace();
        }
    }

    private static void writeExamsLatex(
            Path outputFilePath,
            List<ExamData> examDataList,
            String commandLineString,
            String heading,
            String subheading,
            boolean highlightCorrectAnswers
    ) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath.toString()))) {
            writeLatexPreamble(writer);

            // Write each exam into the LaTeX file
            for (ExamData ed : examDataList) {
                // Insert a page break before each exam except the first one
                if (ed.examNumber > 1) {
                    writer.write("\\newpage");
                    writer.newLine();
                    writer.newLine();
                }

                // Write heading for each exam, with obfuscated exam ID
                String obfuscatedExamId = generateObfuscatedExamId(commandLineString, ed.examNumber);
                writer.write("\\section*{" + escapeLatex(heading) + " \\footnotesize [" + obfuscatedExamId + "]}");
                writer.newLine();

                // If subheading is provided, add it below the main heading
                if (subheading != null) {
                    writer.write("{\\footnotesize " + escapeLatex(subheading) + "}");
                    writer.newLine();
                }

                writer.write("\\begin{multicols}{2}"); // Start two-column layout
                writer.newLine();
                writer.newLine();

                // Generate LaTeX content for the current exam
                List<String> latexQuestions = generateLatex(ed.questions, highlightCorrectAnswers);

                // Write each question's LaTeX content
                for (String questionTex : latexQuestions) {
                    writer.write(questionTex);
                }

                writer.write("\\end{multicols}");
                writer.newLine();
                writer.newLine();
            }

            writer.write("\\end{document}");
            writer.newLine();
        }
    }

    private static void writeLatexPreamble(BufferedWriter writer) throws IOException {
        // Write LaTeX preamble with optimized settings
        writer.write("\\documentclass[10pt]{article}"); // Smaller base font size
        writer.newLine();
        writer.write("\\usepackage[utf8]{inputenc}");      // UTF-8 encoding
        writer.newLine();
        writer.write("\\usepackage[T1]{fontenc}");         // Enhanced font encoding
        writer.newLine();
        writer.write("\\usepackage{lmodern}");             // Latin Modern fonts
        writer.newLine();
        writer.write("\\usepackage{enumitem}");            // Customizable lists
        writer.newLine();
        writer.write("\\usepackage{multicol}");            // Two-column layout
        writer.newLine();
        writer.write("\\usepackage{geometry}");            // Page layout
        writer.newLine();
        writer.write("\\usepackage{setspace}");            // Line spacing control
        writer.newLine();
        writer.write("\\usepackage{xcolor}");              // Correct-answer highlighting
        writer.newLine();
        writer.write("\\geometry{a4paper, margin=0.5in}"); // One-third less margin space than 0.75in
        writer.newLine();
        writer.write("\\setstretch{0.8}");                 // Reduced line spacing
        writer.newLine();
        writer.write("\\hyphenpenalty=10000");             // Prevent words from splitting across lines
        writer.newLine();
        writer.write("\\exhyphenpenalty=10000");           // Prevent breaks at explicit hyphens
        writer.newLine();
        writer.write("\\emergencystretch=2em");            // Allow cleaner spacing without hyphenation
        writer.newLine();
        writer.write("\\begin{document}");
        writer.newLine();
        writer.newLine();
    }

    /**
     * Generates a list of selected Question objects for an exam.
     *
     * @param filteredArgs List of question selection arguments.
     * @param basePath     Base directory to search for question files (optional, uses current directory if null).
     * @param seed         Seed value for randomness.
     * @param examNumber   The current exam number.
     * @param random       Random instance for shuffling and selection.
     * @return List of selected Question objects.
     */
    private static List<Question> generateExam(List<String> filteredArgs, String basePath, long seed, int examNumber, Random random) {
        String seedInfo = String.valueOf(seed);
        System.out.println("Inquisitor: Selecting questions for Exam " + examNumber + " with seed: " + seed);

        // List to hold all selected questions
        List<Question> selectedQuestions = new ArrayList<>();

        // Process each pair of arguments
        for (int i = 0; i < filteredArgs.size(); i += 2) {
            int numQuestions = 0;
            try {
                numQuestions = Integer.parseInt(filteredArgs.get(i));
                if (numQuestions <= 0) {
                    System.out.println("Number of questions must be positive. Found: " + filteredArgs.get(i));
                    return null;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid number format: " + filteredArgs.get(i));
                return null;
            }

            String relativePath = filteredArgs.get(i + 1);
            String fullPath = (basePath != null) ? Paths.get(basePath, relativePath).toString() : relativePath;
            List<Question> allQuestions = null;

            // Check if the question file exists
            File qaFile = new File(fullPath);
            if (!qaFile.exists()) {
                System.out.println("Question file not found: " + fullPath);
                return null;
            }

            try {
                allQuestions = readQuestionsFromFile(fullPath);
            } catch (IOException e) {
                System.out.println("Error reading file: " + fullPath);
                e.printStackTrace();
                return null;
            }

            if (numQuestions > allQuestions.size()) {
                System.out.println("Requested " + numQuestions + " questions, but only " + allQuestions.size() + " available in " + fullPath);
                return null;
            }

            // Randomly select numQuestions from allQuestions using the provided Random instance
            Collections.shuffle(allQuestions, random);
            List<Question> selected = allQuestions.subList(0, numQuestions);

            // Shuffle answers within each question and set correctAnswerIndex
            for (Question q : selected) {
                List<String> shuffledAnswers = new ArrayList<>(q.answers);
                Collections.shuffle(shuffledAnswers, random);
                String correctAnswerStr = q.answers.get(q.correctAnswerIndex);
                int correctIndex = shuffledAnswers.indexOf(correctAnswerStr);
                if (correctIndex == -1) {
                    System.out.println("Error: Correct answer '" + correctAnswerStr + "' not found after shuffling for question: " + q.text);
                    return null;
                }
                q.answers = shuffledAnswers;
                q.correctAnswerIndex = correctIndex;
            }

            selectedQuestions.addAll(selected);
        }

        return selectedQuestions;
    }

    /**
     * Generates LaTeX-formatted content from a list of Question objects.
     *
     * @param questions List of Question objects.
     * @return List of LaTeX-formatted question strings.
     */
    private static List<String> generateLatex(List<Question> questions, boolean highlightCorrectAnswers) {
        List<String> latexQuestions = new ArrayList<>();
        int questionNumber = 1;
        for (Question q : questions) {
            StringBuilder questionTex = new StringBuilder();

            // Scope bold styling to the question label and prompt; answers start after the group.
            questionTex.append("{\\bfseries Q")
                    .append(questionNumber)
                    .append(": ")
                    .append(escapeLatex(q.text))
                    .append("\\par}\n\n");

            // Begin enumerate environment with numerical labels
            questionTex.append("\\begin{enumerate}[label=\\arabic*.]\n");

            // Use the already shuffled answers
            for (int answerIndex = 0; answerIndex < q.answers.size(); answerIndex++) {
                String answer = escapeLatex(q.answers.get(answerIndex));
                if (highlightCorrectAnswers && answerIndex == q.correctAnswerIndex) {
                    questionTex.append("\\item {\\color{green!50!black}\\bfseries ")
                            .append(answer)
                            .append("}\n");
                } else {
                    questionTex.append("\\item ")
                            .append(answer)
                            .append("\n");
                }
            }

            questionTex.append("\\end{enumerate}\n");

            // Add to LaTeX questions list
            latexQuestions.add(questionTex.toString());

            questionNumber++;
        }
        return latexQuestions;
    }

    /**
     * Writes the results.csv file compatible with Google Sheets, including correction formulas for each student row.
     *
     * @param examDataList  List of ExamData containing exam details.
     * @param csvFileName   Name of the CSV file to create.
     * @param T             Number of questions per exam.
     * @param totalStudents Total number of students.
     * @throws IOException If an I/O error occurs.
     */
    private static void writeResultsCSV(List<ExamData> examDataList, String csvFileName, int T, Integer totalStudents) throws IOException {
        if (examDataList.isEmpty()) {
            System.out.println("No exams to write to CSV.");
            return;
        }

        // Open CSV file for writing
        try (BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFileName))) {
            // Write header with student data, exam number, expected answers, submitted answers, and correction fields.
            StringBuilder header = new StringBuilder();
            header.append("Surname,Name,Student ID,Exam Number,Exam Answers,answers");
            for (int i = 1; i <= T; i++) {
                header.append(",A").append(i);
            }
            for (int i = 1; i <= T; i++) {
                header.append(",Q").append(i);
            }
            header.append(",Correct,Wrong,Not Given");
            csvWriter.write(header.toString());
            csvWriter.newLine();

            int studentRows = (totalStudents != null) ? totalStudents : examDataList.size();
            if (studentRows < 1) {
                studentRows = 1;
            }
            int rowIndex = 2; // First data row after the CSV header.
            for (int i = 0; i < studentRows; i++) {
                csvWriter.write(buildCorrectionCsvRow(examDataList, T, rowIndex));
                csvWriter.newLine();
                rowIndex++;
            }
        }
    }

    private static String buildCorrectionCsvRow(List<ExamData> examDataList, int T, int rowIndex) {
        StringBuilder row = new StringBuilder();
        row.append(",,,"); // Empty cells for Surname, Name, Student ID.
        row.append(""); // Numeric exam number.
        row.append(",");

        String examNumberCell = "$D" + rowIndex;
        String examAnswersCell = "$E" + rowIndex;
        String studentAnswersCell = "$F" + rowIndex;
        row.append(buildExamAnswersFormula(examDataList, examNumberCell));
        row.append(",");
        row.append(""); // Student answers string.

        for (int i = 1; i <= T; i++) {
            String formula = "=IF(" + studentAnswersCell + "<>\"\"; LOWER(MID(" + studentAnswersCell + ";" + i + ";1)); \"\")";
            row.append(",").append(formula);
        }

        for (int i = 1; i <= T; i++) {
            String studentAnswerChar = "LOWER(MID(" + studentAnswersCell + ";" + i + ";1))";
            String correctAnswerChar = "MID(" + examAnswersCell + ";" + i + ";1)";
            String formula = "=IF(OR(" + examAnswersCell + "=\"\";" + studentAnswersCell + "=\"\");\"\";IF(" + studentAnswerChar + "=\"x\";\"x\";IF(" + studentAnswerChar + "=" + correctAnswerChar + ";\"c\";\"w\")))";
            row.append(",").append(formula);
        }

        int checkStartColNum = 7 + T; // Q1 starts after the T extracted-answer columns.
        int checkEndColNum = 7 + 2 * T - 1;
        String range = getColumnLetter(checkStartColNum) + rowIndex + ":" + getColumnLetter(checkEndColNum) + rowIndex;
        row.append(",").append("=COUNTIF(" + range + ";\"c\")");
        row.append(",").append("=COUNTIF(" + range + ";\"w\")");
        row.append(",").append("=COUNTIF(" + range + ";\"x\")");
        return row.toString();
    }

    private static String buildExamAnswersFormula(List<ExamData> examDataList, String examNumberCell) {
        StringBuilder formula = new StringBuilder();
        formula.append("=IFERROR(IF(").append(examNumberCell).append("<>\"\";SWITCH(VALUE(").append(examNumberCell).append(")");
        for (ExamData ed : examDataList) {
            formula.append(";").append(ed.examNumber).append(";\"").append(buildCorrectAnswersString(ed)).append("\"");
        }
        formula.append(";\"\");\"\");\"\")");
        return formula.toString();
    }

    private static String buildCorrectAnswersString(ExamData examData) {
        StringBuilder answers = new StringBuilder();
        for (Integer correctIndex : examData.correctAnswerIndices) {
            answers.append(correctIndex + 1);
        }
        return answers.toString();
    }

    /**
     * Writes the answers_key.txt file.
     *
     * @param examDataList   List of ExamData containing exam details.
     * @param answersKeyPath Path to the answers_key.txt file.
     * @throws IOException If an I/O error occurs.
     */
    private static void writeAnswersKey(List<ExamData> examDataList, String answersKeyPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(answersKeyPath))) {
            for (ExamData ed : examDataList) {
                writer.write("Exam " + ed.examNumber + " Seed: " + ed.seed);
                writer.newLine();
                int questionNum = 1;
                for (Integer correctIndex : ed.correctAnswerIndices) {
                    int oneBasedIndex = correctIndex + 1;
                    writer.write("Q" + questionNum + ": " + oneBasedIndex);
                    writer.newLine();
                    questionNum++;
                }
                writer.newLine();
            }
        }
    }

    /**
     * Escapes LaTeX special characters in a string, preserving LaTeX commands within math mode.
     *
     * @param text The input string.
     * @return The escaped string.
     */
    private static String escapeLatex(String text) {
        StringBuilder escaped = new StringBuilder();
        boolean inMathMode = false;
        String mathDelimiter = null;
        int last = 0;
        int i = 0;

        while (i < text.length()) {
            if (text.charAt(i) != '$') {
                i++;
                continue;
            }

            int delimiterLength = (i + 1 < text.length() && text.charAt(i + 1) == '$') ? 2 : 1;
            String delimiter = text.substring(i, i + delimiterLength);

            if (!inMathMode) {
                if (i > last) {
                    escaped.append(escapeLatexSegment(text.substring(last, i)));
                }
                escaped.append(delimiter);
                inMathMode = true;
                mathDelimiter = delimiter;
                i += delimiterLength;
                last = i;
                continue;
            }

            if (delimiter.equals(mathDelimiter)) {
                if (i > last) {
                    escaped.append(text.substring(last, i));
                }
                escaped.append(delimiter);
                inMathMode = false;
                mathDelimiter = null;
                i += delimiterLength;
                last = i;
                continue;
            }

            i += delimiterLength;
        }

        if (last < text.length()) {
            String segment = text.substring(last);
            if (inMathMode) {
                escaped.append(segment);
            } else {
                escaped.append(escapeLatexSegment(segment));
            }
        }

        if (inMathMode) {
            System.out.println("Warning: Unbalanced math delimiter " + mathDelimiter + " in text: " + text);
        }

        return escaped.toString();
    }

    /**
     * Escapes LaTeX special characters in a segment of text.
     *
     * @param text The input string segment.
     * @return The escaped string segment.
     */
    private static String escapeLatexSegment(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '#':
                case '%':
                case '&':
                case '~':
                case '_':
                case '^':
                case '\\':
                case '{':
                case '}':
                    escaped.append('\\').append(c);
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * Escapes double quotes for CSV formulas.
     *
     * @param text The input string.
     * @return The escaped string.
     */
    private static String escapeCSV(String text) {
        if (text.contains("\"")) {
            text = text.replace("\"", "\"\"");
        }
        return text;
    }

    /**
     * Reads questions from a given file.
     *
     * @param filePath Path to the questions file.
     * @return List of Question objects.
     * @throws IOException If an I/O error occurs.
     */
    private static List<Question> readQuestionsFromFile(String filePath) throws IOException {
        if (!filePath.toLowerCase(Locale.ROOT).endsWith(".qa.md")) {
            throw new IOException("Unsupported question bank format: " + filePath
                    + ". Use .qa.md files, or convert old .qa files with scripts/convert_qa_to_md.cjs.");
        }
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        return readMarkdownQuestionsFromLines(lines, filePath);
    }

    private static List<Question> readMarkdownQuestionsFromLines(List<String> lines, String filePath) throws IOException {
        List<Question> questions = new ArrayList<>();
        List<String> promptLines = new ArrayList<>();
        List<StringBuilder> answerBuilders = new ArrayList<>();
        boolean readingAnswers = false;
        int questionStartLine = -1;

        for (int i = 0; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            String trimmed = rawLine.trim();
            int lineNumber = i + 1;

            if (isMarkdownQuestionHeading(trimmed)) {
                addMarkdownQuestion(questions, promptLines, answerBuilders, filePath, questionStartLine);
                promptLines = new ArrayList<>();
                answerBuilders = new ArrayList<>();
                readingAnswers = false;
                questionStartLine = lineNumber;

                String headingText = trimmed.substring(2).trim();
                if (!headingText.isEmpty()) {
                    promptLines.add(headingText);
                }
                continue;
            }

            if (questionStartLine < 0) {
                continue;
            }

            String bulletText = markdownBulletText(trimmed);
            if (bulletText != null) {
                readingAnswers = true;
                answerBuilders.add(new StringBuilder(bulletText));
                continue;
            }

            if (readingAnswers) {
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (answerBuilders.isEmpty()) {
                    throw new IOException("Answer continuation without an answer bullet in " + filePath + " at line " + lineNumber);
                }
                if (rawLine.startsWith(" ") || rawLine.startsWith("\t")) {
                    StringBuilder currentAnswer = answerBuilders.get(answerBuilders.size() - 1);
                    if (currentAnswer.length() > 0) {
                        currentAnswer.append(' ');
                    }
                    currentAnswer.append(trimmed);
                    continue;
                }
                throw new IOException("Expected an answer bullet or a new ## question in " + filePath + " at line " + lineNumber + ": " + trimmed);
            }

            promptLines.add(trimmed);
        }

        addMarkdownQuestion(questions, promptLines, answerBuilders, filePath, questionStartLine);
        return questions;
    }

    private static boolean isMarkdownQuestionHeading(String trimmedLine) {
        return (trimmedLine.startsWith("## ") || trimmedLine.startsWith("##\t"))
                && !trimmedLine.startsWith("###");
    }

    private static String markdownBulletText(String trimmedLine) {
        if (trimmedLine.length() < 2) {
            return null;
        }

        char bullet = trimmedLine.charAt(0);
        if ((bullet == '-' || bullet == '*' || bullet == '+') && Character.isWhitespace(trimmedLine.charAt(1))) {
            return trimmedLine.substring(2).trim();
        }

        return null;
    }

    private static void addMarkdownQuestion(
            List<Question> questions,
            List<String> promptLines,
            List<StringBuilder> answerBuilders,
            String filePath,
            int questionStartLine
    ) throws IOException {
        if (questionStartLine < 0) {
            return;
        }

        String prompt = normalizeMarkdownBlock(promptLines);
        if (prompt.isEmpty()) {
            throw new IOException("Empty question heading in " + filePath + " at line " + questionStartLine);
        }

        if (answerBuilders.size() != ANSWERS_PER_QUESTION) {
            throw new IOException("Question in " + filePath + " at line " + questionStartLine
                    + " must have exactly " + ANSWERS_PER_QUESTION + " answers; found " + answerBuilders.size());
        }

        List<String> answers = new ArrayList<>();
        for (StringBuilder answerBuilder : answerBuilders) {
            String answer = answerBuilder.toString().trim();
            if (answer.isEmpty()) {
                throw new IOException("Empty answer in " + filePath + " for question at line " + questionStartLine);
            }
            answers.add(answer);
        }

        questions.add(new Question(prompt, answers, 0));
    }

    private static String normalizeMarkdownBlock(List<String> lines) {
        int first = 0;
        int last = lines.size() - 1;
        while (first <= last && lines.get(first).trim().isEmpty()) {
            first++;
        }
        while (last >= first && lines.get(last).trim().isEmpty()) {
            last--;
        }

        StringBuilder normalized = new StringBuilder();
        boolean pendingParagraphBreak = false;

        for (int i = first; i <= last; i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                pendingParagraphBreak = normalized.length() > 0;
                continue;
            }

            if (normalized.length() > 0) {
                normalized.append(pendingParagraphBreak ? "\n\n" : " ");
            }
            normalized.append(line);
            pendingParagraphBreak = false;
        }

        return normalized.toString();
    }

    /**
     * Converts a column number to its corresponding Excel/Google Sheets column letter.
     *
     * @param columnNumber The 1-based column number.
     * @return The corresponding column letter (e.g., 1 -> A, 27 -> AA).
     */
    private static String getColumnLetter(int columnNumber) {
        StringBuilder column = new StringBuilder();
        while (columnNumber > 0) {
            int rem = (columnNumber - 1) % 26;
            column.append((char) (rem + 'A'));
            columnNumber = (columnNumber - 1) / 26;
        }
        return column.reverse().toString();
    }
}
