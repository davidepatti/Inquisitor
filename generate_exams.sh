#!/bin/bash

# Define variables at the beginning
SEED=1012025                # Removed leading zero to avoid octal interpretation
TOT_EXAMS=10
TOT_STUDENTS=3
HEADING="Test pollo"
HEADING2="Durata 50 min Ciao"

# Execute the Java program with proper variable expansion and quoting
java Inquisitor \
    1 1_intro.qa \
    1 2_es.qa \
    1 3_circuit.qa \
    1 4_arduino.qa \
    1 5_arduino2.qa \
    1 6_lab.qa \
    1 dpiot2.qa \
    1 dpiot3.qa \
    1 dpiot4.qa \
    1 dpiot5.qa \
    -t "$TOT_EXAMS" -st "$TOT_STUDENTS" -s "$SEED" -h "$HEADING" -h2 "$HEADING2"

# Check if Java execution was successful
if [ $? -ne 0 ]; then
    echo "Inquisitor execution failed. Exiting script."
    exit 1
fi

# Create output directory name by concatenating HEADING and SEED with an underscore
OUTPUT_DIR="${HEADING// /_}_${SEED}"

# Navigate to the output directory
cd "$OUTPUT_DIR" || { echo "Failed to enter directory $OUTPUT_DIR"; exit 1; }

# Compile the LaTeX file
echo "Compiling LaTeX file..."
pdflatex "${SEED}_all_exams.tex"
pdflatex "${SEED}_all_exams.tex"

# Check if LaTeX compilation was successful
if [ $? -ne 0 ]; then
    echo "LaTeX compilation failed. Check ${SEED}_all_exams.log for details."
    exit 1
fi

echo "Compilation successful. Generated ${SEED}_all_exams.pdf."
echo "CSV file generated as ${SEED}_results.csv."
echo "Answers key generated as ${SEED}_answers_key.txt."
