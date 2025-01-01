SEED=20250101
TOT_EXAMS=10
HEADING="Test pollo"
HEADING2="Durata 50 min Ciao"

java Inquisitor		1 1_intro.qa \
			1 2_es.qa \
			1 3_circuit.qa \
			1 4_arduino.qa \
			1 5_arduino2.qa \
			1 6_lab.qa \
			1 dpiot2.qa \
			1 dpiot3.qa \
			1 dpiot4.qa \
			1 dpiot5.qa \
-t "$TOT_EXAMS" -s "$SEED" -h "$HEADING" -h2 "$HEADING2"
