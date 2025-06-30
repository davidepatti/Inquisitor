import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Inquisitor {
    private static String generateObfuscatedExamId(String commandLineArgs, int examNumber) {
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
            return hexString.append(examNumber).toString().toUpperCase(); // e.g., "5C8A72F0"
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
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

        // If totalExams is specified, seed must also be provided
        if (totalExams != null && seed == null) {
            System.out.println("Error: --total_exams requires that a seed is also provided using --seed <integer>");
            return;
        }

        // Set default heading if not provided
        if (heading == null) {
            heading = "Exam";
        }

        // Create output folder name by concatenating HEADING and SEED with an underscore
        String sanitizedHeading = heading.replaceAll("\\s+", "_"); // Replace spaces with underscores
        String outputFolderName;
        if (seed != null) {
            outputFolderName = sanitizedHeading + "_" + seed;
        } else {
            outputFolderName = sanitizedHeading + "_default";
        }
        Path outputFolderPath = Paths.get(outputFolderName);

        // Create the output folder if it doesn't exist
        if (!Files.exists(outputFolderPath)) {
            try {
                Files.createDirectories(outputFolderPath);
                System.out.println("Created output directory: " + outputFolderName);
            } catch (IOException e) {
                System.out.println("Error creating output directory: " + outputFolderName);
                e.printStackTrace();
                return;
            }
        }

        // Initialize output filenames with seed prefix
        String seedPrefix = (seed != null) ? String.valueOf(seed) : "default";
        String outputFileName = seedPrefix + "_all_exams.tex";
        String csvFileName = seedPrefix + "_results.csv";
        String answersKeyFileName = seedPrefix + "_answers_key.txt";

        Path outputFilePath = outputFolderPath.resolve(outputFileName);
        Path csvFilePath = outputFolderPath.resolve(csvFileName);
        Path answersKeyFilePath = outputFolderPath.resolve(answersKeyFileName);

        // Initialize a list to collect all ExamData
        List<ExamData> examDataList = new ArrayList<>();

        // Initialize a single Random instance with the provided seed
        Random randomInstance;
        if (seed != null) {
            randomInstance = new Random(seed);
        } else {
            randomInstance = new Random(); // Use default seed
        }

        // Determine the total number of exams
        int examsToGenerate = (totalExams != null) ? totalExams : 1;

        // Generate all exams
        for (int exam = 1; exam <= examsToGenerate; exam++) {
            long currentSeed = (seed != null) ? seed : new Random().nextLong();
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

        // Start writing the LaTeX file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath.toString()))) {
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
            writer.write("\\geometry{a4paper, margin=0.75in}"); // Thinner margins
            writer.newLine();
            writer.write("\\setstretch{0.8}");                 // Reduced line spacing
            writer.newLine();
            writer.write("\\begin{document}");
            writer.newLine();
            writer.newLine();

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
                writer.write("\\section*{" + escapeLatex(heading) + " \\footnotesize [" + obfuscatedExamId+"]}");
                //writer.write("{\\footnotesize " + obfuscatedExamId + "}");
                //writer.newLine();
                writer.newLine();

                // If subheading is provided, add it below the main heading
                if (subheading != null) {
                    writer.write("{\\footnotesize " + escapeLatex(subheading) + "}");
                    //writer.newLine();
                    writer.newLine();
                }

                writer.write("\\begin{multicols}{2}"); // Start two-column layout
                writer.newLine();
                writer.newLine();

                // Generate LaTeX content for the current exam
                List<String> latexQuestions = generateLatex(ed.questions);

                // Write each question's LaTeX content
                for (String questionTex : latexQuestions) {
                    writer.write(questionTex);
                    //writer.newLine();
           //         writer.newLine();
                }

                writer.write("\\end{multicols}");
                writer.newLine();
                writer.newLine();
            }

            writer.write("\\end{document}");
            writer.newLine();

            System.out.println("Generated " + outputFilePath.getFileName() + " successfully.");

            // Write results.csv with new columns
            writeResultsCSV(examDataList, csvFilePath.toString(), T, totalStudents, commandLineString);
            System.out.println("Generated " + csvFilePath.getFileName() + " successfully.");

            // Write answers_key.txt with seed prefix
            writeAnswersKey(examDataList, answersKeyFilePath.toString());
            System.out.println("Generated " + answersKeyFileName + " successfully.");

        } catch (IOException e) {
            System.out.println("Error writing to " + outputFilePath.getFileName());
            e.printStackTrace();
        }
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
    private static List<String> generateLatex(List<Question> questions) {
        List<String> latexQuestions = new ArrayList<>();
        int questionNumber = 1;
        for (Question q : questions) {
            StringBuilder questionTex = new StringBuilder();

            // Write question text with escaped LaTeX characters, preserving math mode
            questionTex.append("\\textbf{Q" + questionNumber + ": " + escapeLatex(q.text)+"}");
            questionTex.append("\n\n");

            // Begin enumerate environment with numerical labels
            questionTex.append("\\begin{enumerate}[label=\\arabic*.]\n");

            // Use the already shuffled answers
            for (String answer : q.answers) {
                questionTex.append("\\item " + escapeLatex(answer) + "\n");
            }

            questionTex.append("\\end{enumerate}\n");

            // Add to LaTeX questions list
            latexQuestions.add(questionTex.toString());

            questionNumber++;
        }
        return latexQuestions;
    }

    /**
     * Writes the results.csv file compatible with Google Sheets, including new columns and empty rows for students.
     *
     * @param examDataList  List of ExamData containing exam details.
     * @param csvFileName   Name of the CSV file to create.
     * @param T             Number of questions per exam.
     * @param totalStudents Total number of students.
     * @param commandLineString The string of command line arguments, for ID obfuscation.
     * @throws IOException If an I/O error occurs.
     */
    private static void writeResultsCSV(List<ExamData> examDataList, String csvFileName, int T, Integer totalStudents, String commandLineString) throws IOException {
        if (examDataList.isEmpty()) {
            System.out.println("No exams to write to CSV.");
            return;
        }

        // Open CSV file for writing
        try (BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFileName))) {
            // Write header with new columns: "Surname", "Name", "Student ID"
            StringBuilder header = new StringBuilder();
            header.append("Surname,Name,Student ID,Exam ID,Seed,answers");
            for (int i = 1; i <= T; i++) {
                header.append(",A").append(i);
            }
            for (int i = 1; i <= T; i++) {
                header.append(",Q").append(i);
            }
            header.append(",Correct,Wrong,Not Given");
            csvWriter.write(header.toString());
            csvWriter.newLine();

            // Calculate examInstances = totalStudents/totalExames
            int examInstances = 1; // Default value
            if (totalStudents != null && examDataList.size() > 0) {
                double exactX = totalStudents/(double) examDataList.size();
                examInstances = (int) Math.ceil(exactX);
                if (examInstances < 1) {
                    examInstances = 1;
                }
            }

            // For each exam, write a row and then examInstances empty rows
            for (ExamData ed : examDataList) {
                StringBuilder row = new StringBuilder();
                row.append(",").append(",").append(","); // Empty cells for Surname, Name, Student ID
                String obfuscatedExamId = generateObfuscatedExamId(commandLineString, ed.examNumber);
                row.append(obfuscatedExamId).append(",").append(ed.seed).append(",");
                // Insert empty "answers" cell
                row.append(",");

                // For each A1..AT, insert formula referencing "answers" column, else empty
                // "answers" is at column 6 (1-based)
                int answersColNum = 6;
                String answersColLetter = getColumnLetter(answersColNum);
                int rowIndex = ed.examNumber + 1;
                rowIndex = rowIndex+examInstances*(ed.examNumber-1);
                for (int i = 1; i <= T; i++) {
                    // Formula: =IF($COL_ANSWERSR<>""; MID($COL_ANSWERSR; K; 1); "")
                    String formula = "=IF($" + answersColLetter + rowIndex + "<>\"\"; MID($" + answersColLetter + rowIndex + ";" + i + ";1); \"\")";
                    row.append(formula);
                    if (i < T) row.append(",");
                }

                // For each Q1..QT, insert formula as before, but update column indices
                // A1..AT now start at column 7 (after answers), so Q1..QT start at 7+T = 7+T (1-based)
                for (int i = 0; i < T; i++) {
                    // Determine column letters
                    int answerColNum = 7 + i; // A1 is col 7, A2 col 8, ..., so Q1 is col 7+T
                    String answerColLetter = getColumnLetter(answerColNum);
                    int correctIndex = ed.correctAnswerIndices.get(i) + 1;
                    // Compare as string, not number
                    String formula = "=IF(" + answerColLetter + rowIndex + "=\"" + correctIndex + "\";\"C\"; IF(" + answerColLetter + rowIndex + "=\" \";\"NA\";\"W\"))";
                    row.append(",");
                    row.append(formula);
                }

                // Update indices for Correct, Wrong, Not Given
                int checkStartColNum = 7 + T; // Q1 starts after T Answer columns
                int checkEndColNum = 7 + 2 * T - 1; // QT ends at this column
                String checkStartColLetter = getColumnLetter(checkStartColNum);
                String checkEndColLetter = getColumnLetter(checkEndColNum);
                String range = checkStartColLetter + rowIndex + ":" + checkEndColLetter + rowIndex;
                String correctCountFormula = "=COUNTIF(" + range + ";\"C\")";
                String wrongCountFormula = "=COUNTIF(" + range + ";\"W\")";
                String notGivenCountFormula = "=COUNTIF(" + range + ";\"NA\")";
                row.append(",").append(correctCountFormula);
                row.append(",").append(wrongCountFormula);
                row.append(",").append(notGivenCountFormula);
                csvWriter.write(row.toString());
                csvWriter.newLine();
                for (int i = 0; i < examInstances; i++) {
                    StringBuilder emptyRow = new StringBuilder();
                    // Total columns: 5 (Surname, Name, Student ID, Exam Number, Seed) + 1 ("answers") + 2*T (A<N>, Q<N>) + 3 (Correct, Wrong, Not Given)
                    // So total columns = 6 + 2*T + 3 = 9 + 2*T
                    int totalColumns = 9 + 2 * T;
                    for (int j = 0; j < totalColumns - 1; j++) {
                        emptyRow.append(",");
                    }
                    emptyRow.append(","); // Last column
                    csvWriter.write(emptyRow.toString());
                    csvWriter.newLine();
                }
            }
        }
    }

    /**
     * Writes the answers_key.txt file with seed prefix.
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
                    // Directly use the number for the correct answer
                    writer.write("Q" + questionNum + ": " + correctIndex);
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
        int last = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '$') {
                // Append the segment before the $
                if (i > last) {
                    String segment = text.substring(last, i);
                    if (!inMathMode) {
                        escaped.append(escapeLatexSegment(segment));
                    } else {
                        escaped.append(segment);
                    }
                }
                // Append the $
                escaped.append('$');
                // Toggle math mode
                inMathMode = !inMathMode;
                last = i + 1;
            }
        }
        // Append any remaining text after the last $
        if (last < text.length()) {
            String segment = text.substring(last);
            if (!inMathMode) {
                escaped.append(escapeLatexSegment(segment));
            } else {
                escaped.append(segment);
            }
        }
        // Warn if dollars are unbalanced
        int dollarCount = countChar(text, '$');
        if (dollarCount % 2 != 0) {
            System.out.println("Warning: Unbalanced '$' symbols in text: " + text);
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
     * Counts the occurrences of a specific character in a string.
     *
     * @param text The input string.
     * @param c    The character to count.
     * @return The number of occurrences.
     */
    private static int countChar(String text, char c) {
        int count = 0;
        for (char current : text.toCharArray()) {
            if (current == c) {
                count++;
            }
        }
        return count;
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
        List<Question> questions = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        Iterator<String> iterator = lines.iterator();

        while (iterator.hasNext()) {
            String line = iterator.next().trim();
            if (line.startsWith("[Q]") && line.endsWith("[/Q]")) {
                // Extract question text
                String questionText = extractQuestionText(line);
                List<String> answers = new ArrayList<>();
                String correctAnswerStr = null;

                // Read the next 4 lines as answers
                for (int i = 0; i < 4; i++) {
                    if (iterator.hasNext()) {
                        String answerLine = iterator.next().trim();
                        // Check if the answer is correct (starts with '*')
                        if (i == 0 && answerLine.startsWith("*")) {
                            correctAnswerStr = answerLine.substring(1).trim();
                            answers.add(correctAnswerStr);
                        } else {
                            answers.add(answerLine);
                        }
                    } else {
                        throw new IOException("Unexpected end of file while reading answers for question: " + questionText);
                    }
                }

                if (correctAnswerStr == null) {
                    throw new IOException("No correct answer found for question: " + questionText);
                }

                // Initially, correctAnswerIndex is set to 0 (will be updated after shuffling)
                questions.add(new Question(questionText, answers, 0));
            }
        }

        return questions;
    }

    /**
     * Extracts the question text from a line.
     *
     * @param line Line containing the question.
     * @return Extracted question text.
     */
    private static String extractQuestionText(String line) {
        // Remove [Q] and [/Q]
        line = line.substring(3, line.length() - 4).trim();
        return escapeLatex(line);
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
