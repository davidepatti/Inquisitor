import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Inquisitor {

    // Class to represent a question
    static class Question {
        String text;
        List<String> answers;
        String correctAnswer; // To store the correct answer

        Question(String text, List<String> answers, String correctAnswer) {
            this.text = text;
            this.answers = answers;
            this.correctAnswer = correctAnswer;
        }
    }

    // Class to store exam data
    static class ExamData {
        int examNumber;
        long seed;
        List<String> correctAnswers;

        ExamData(int examNumber, long seed, List<String> correctAnswers) {
            this.examNumber = examNumber;
            this.seed = seed;
            this.correctAnswers = correctAnswers;
        }
    }

    public static void main(String[] args) {
        // Parse command-line arguments
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        Long seed = null;
        Integer totalExams = null;
        String heading = null;  // For the -h option
        String subheading = null; // For the -h2 option

        // Parse for --seed or -s, --total_exams or -t, -h, and -h2
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
        }

        // After parsing, remove the flags and their values from argsList
        // Reconstruct argsList without flags
        List<String> filteredArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--seed") || arg.equalsIgnoreCase("-s") ||
                arg.equalsIgnoreCase("--total_exams") || arg.equalsIgnoreCase("-t") ||
                arg.equalsIgnoreCase("-h") || arg.equalsIgnoreCase("-h2")) {
                i++; // Skip the next argument as it's the value for the flag
            } else {
                filteredArgs.add(arg);
            }
        }

        // Now, filteredArgs contains only the question selection arguments
        if (filteredArgs.size() == 0 || filteredArgs.size() % 2 != 0) {
            System.out.println("Usage: java Inquisitor n1 questions_file1 n2 questions_file2 ... [-h <heading>] [-h2 <subheading>] [--seed <integer>] [--total_exams <integer>]");
            System.out.println("Example: java Inquisitor 5 questions1.txt 10 questions2.txt -h \"Midterm Exam\" -h2 \"Calculus Section\" --seed 1000 --total_exams 3");
            return;
        }

        // If totalExams is specified, seed must also be provided
        if (totalExams != null && seed == null) {
            System.out.println("Error: --total_exams requires that a seed is also provided using --seed <integer>");
            return;
        }

        // Set default heading if not provided
        if (heading == null) {
            heading = "Exam";
        }

        // Initialize output LaTeX file with seed prefix
        String seedPrefix = (seed != null) ? String.valueOf(seed) : "default";
        String outputFileName = seedPrefix + "_all_exams.tex";
        String csvFileName = seedPrefix + "_results.csv";

        Path outputFilePath = Paths.get(outputFileName);

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
            writer.write("\\setstretch{0.9}");                 // Reduced line spacing
            writer.newLine();
            writer.write("\\begin{document}");
            writer.newLine();
            writer.newLine();

            // Initialize a list to collect all ExamData
            List<ExamData> examDataList = new ArrayList<>();

            // Initialize a single Random instance with the provided seed
            Random random;
            if (seed != null) {
                random = new Random(seed);
            } else {
                random = new Random(); // Use default seed
            }

            // Determine the total number of exams
            int examsToGenerate = (totalExams != null) ? totalExams : 1;

            // Generate all exams
            for (int exam = 1; exam <= examsToGenerate; exam++) {
                long currentSeed = (seed != null) ? seed : new Random().nextLong();
                List<String> correctAnswers = generateExam(filteredArgs, currentSeed, exam, random);
                if (correctAnswers == null) {
                    System.out.println("Failed to generate Exam " + exam);
                    continue;
                }
                ExamData ed = new ExamData(exam, currentSeed, correctAnswers);
                examDataList.add(ed);
            }

            // Write all exams into the single LaTeX file
            int examNumber = 1;
            for (ExamData ed : examDataList) {
                // Insert a page break before each exam except the first one
                if (examNumber > 1) {
                    writer.write("\\newpage");
                    writer.newLine();
                    writer.newLine();
                }

                // Write heading for each exam
                writer.write("\\section*{" + escapeLatex(heading) + " " + examNumber + "}");
                writer.newLine();
                writer.newLine();

                // If subheading is provided, add it below the main heading
                if (subheading != null) {
                    writer.write("{\\footnotesize " + escapeLatex(subheading) + "}");
                    writer.newLine();
                    writer.newLine();
                }

                writer.write("\\begin{multicols}{2}"); // Start two-column layout
                writer.newLine();
                writer.newLine();

                int questionNumber = 1;
                for (String questionTex : ed.correctAnswers) {
                    writer.write(questionTex);
                    writer.newLine();
                    writer.newLine();
                    questionNumber++;
                }

                writer.write("\\end{multicols}");
                writer.newLine();
                writer.newLine();
                examNumber++;
            }

            writer.write("\\end{document}");
            writer.newLine();

            System.out.println("Generated " + outputFileName + " successfully.");

            // Write results.csv with seed prefix
            writeResultsCSV(examDataList, csvFileName);
            System.out.println("Generated " + csvFileName + " successfully.");

        } catch (IOException e) {
            System.out.println("Error writing to " + outputFileName);
            e.printStackTrace();
        }
    }

    /**
     * Generates a single exam and returns a list of LaTeX-formatted question strings.
     *
     * @param filteredArgs List of question selection arguments.
     * @param seed         Seed value for randomness.
     * @param examNumber   The current exam number.
     * @param random       Random instance for shuffling and selection.
     * @return List of LaTeX-formatted questions.
     */
    private static List<String> generateExam(List<String> filteredArgs, long seed, int examNumber, Random random) {
        String seedInfo = String.valueOf(seed);
        System.out.println("Inquisitor: Generating Exam " + examNumber + " with seed: " + seed);

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

            String filePath = filteredArgs.get(i + 1);
            List<Question> allQuestions = null;

            try {
                allQuestions = readQuestionsFromFile(filePath);
            } catch (IOException e) {
                System.out.println("Error reading file: " + filePath);
                e.printStackTrace();
                return null;
            }

            if (numQuestions > allQuestions.size()) {
                System.out.println("Requested " + numQuestions + " questions, but only " + allQuestions.size() + " available in " + filePath);
                return null;
            }

            // Randomly select numQuestions from allQuestions using the provided Random instance
            Collections.shuffle(allQuestions, random);
            selectedQuestions.addAll(allQuestions.subList(0, numQuestions));
        }

        // *** Removed Shuffling of Selected Questions ***
        // Ensures that the questions remain in the order they were selected
        // Collections.shuffle(selectedQuestions, random);

        // Generate LaTeX-formatted questions
        List<String> latexQuestions = new ArrayList<>();
        int questionNumber = 1;
        for (Question q : selectedQuestions) {
            StringBuilder questionTex = new StringBuilder();

            // Write question text with escaped LaTeX characters, preserving math mode
            questionTex.append("\\textbf{Question " + questionNumber + ":} " + escapeLatex(q.text));
            questionTex.append("\n\n");

            // Begin enumerate environment with numerical labels
            questionTex.append("\\begin{enumerate}[label=\\arabic*.]\n");

            // Shuffle answers for randomness within the question
            List<String> shuffledAnswers = new ArrayList<>(q.answers);
            Collections.shuffle(shuffledAnswers, random);

            for (String answer : shuffledAnswers) {
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
     * Writes the results.csv file compatible with Google Sheets.
     *
     * @param examDataList List of ExamData containing exam details.
     * @param csvFileName  Name of the CSV file to create.
     * @throws IOException If an I/O error occurs.
     */
    private static void writeResultsCSV(List<ExamData> examDataList, String csvFileName) throws IOException {
        if (examDataList.isEmpty()) {
            System.out.println("No exams to write to CSV.");
            return;
        }

        // Determine T from the first exam
        int T = examDataList.get(0).correctAnswers.size();

        // Open CSV file for writing
        try (BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFileName))) {
            // Write header
            StringBuilder header = new StringBuilder();
            header.append("Exam Number,Seed");
            for (int i = 1; i <= T; i++) {
                header.append(",Answer").append(i);
            }
            for (int i = 1; i <= T; i++) {
                header.append(",Check").append(i);
            }
            header.append(",Correct,Wrong,Not Given");
            csvWriter.write(header.toString());
            csvWriter.newLine();

            // For each exam, write a row
            for (ExamData ed : examDataList) {
                StringBuilder row = new StringBuilder();
                row.append(ed.examNumber).append(",").append(ed.seed).append(","); // Append comma after seed

                // T empty answer cells
                for (int i = 0; i < T; i++) {
                    row.append(",");
                }

                // T formula cells
                for (int i = 0; i < T; i++) {
                    // Determine column letters
                    // Columns: A - Exam Number, B - Seed, C..(C+T-1) - Answer1..T
                    // (C+T) to (C+2T-1) - Check1..T

                    // Compute the column letter for AnswerX
                    int answerColNum = 3 + i; // A=1, B=2, C=3,...
                    String answerColLetter = getColumnLetter(answerColNum);

                    // The current row number is examNumber +1 (since row 1 is header)
                    int rowIndex = ed.examNumber + 1;

                    // Correct answer is ed.correctAnswers.get(i)
                    String correctAnswer = ed.correctAnswers.get(i);

                    // Use semicolons as argument separators in formulas
                    String formula = "=IF(" + answerColLetter + rowIndex + "=" + correctAnswer + ";\"Correct\"; IF(" + answerColLetter + rowIndex + "=\"\";\"Not Given\";\"Wrong\"))";

                    // Append the formula directly without external quotes
                    row.append(formula);

                    // Only append a comma if it's not the last formula
                    if (i < T - 1) {
                        row.append(",");
                    }
                }

                // Compute the range for Check1 to CheckT
                int checkStartColNum = 3 + T; // Check1 starts after T Answer columns
                int checkEndColNum = 3 + 2 * T - 1; // CheckT ends at this column
                String checkStartColLetter = getColumnLetter(checkStartColNum);
                String checkEndColLetter = getColumnLetter(checkEndColNum);
                String range = checkStartColLetter + (ed.examNumber + 1) + ":" + checkEndColLetter + (ed.examNumber + 1);

                // Correct count
                String correctCountFormula = "=COUNTIF(" + range + ";\"Correct\")";

                // Wrong count
                String wrongCountFormula = "=COUNTIF(" + range + ";\"Wrong\")";

                // Not Given count
                String notGivenCountFormula = "=COUNTIF(" + range + ";\"Not Given\")";

                // Append the summary formulas directly without external quotes
                row.append(",").append(correctCountFormula);
                row.append(",").append(wrongCountFormula);
                row.append(",").append(notGivenCountFormula);

                // Write the row
                csvWriter.write(row.toString());
                csvWriter.newLine();
            }
        }
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
                String correctAnswer = null;

                // Read the next 4 lines as answers
                for (int i = 0; i < 4; i++) {
                    if (iterator.hasNext()) {
                        String answerLine = iterator.next().trim();
                        // Check if the answer is correct (starts with '*')
                        if (i == 0 && answerLine.startsWith("*")) {
                            correctAnswer = answerLine.substring(1).trim();
                            answers.add(correctAnswer);
                        } else {
                            answers.add(answerLine);
                        }
                    } else {
                        throw new IOException("Unexpected end of file while reading answers for question: " + questionText);
                    }
                }

                if (correctAnswer == null) {
                    throw new IOException("No correct answer found for question: " + questionText);
                }

                questions.add(new Question(questionText, answers, correctAnswer));
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
}
