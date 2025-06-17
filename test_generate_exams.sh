#!/bin/bash

# Define variables at the beginning
SEED=20250620
TOT_EXAMS=2
TOT_STUDENTS=30
HEADING="Calcoltori Elettronici 2025 06 20"
HEADING2="(Corretta: 1, Errata: -0.5, N/A: 0)"
# Directory where question files are located
BASE_PATH="./questions"

# Execute the Java program with proper variable expansion and quoting
java Inquisitor \
    --base_path "$BASE_PATH" \
    1 ce02-algebracomm.qa \
    1 ce03.qa \
    1 ce07.qa \
    1 ce08.qa \
    1 ce12-circuiti.qa \
    1 1_isa.qa \
    1 2_cpuseq.qa \
    1 3_pipeline.qa \       
    1 4_hazard.qa \
    1 5_cache.qa \
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
