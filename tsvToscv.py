import csv

input_tsv = "src\main\java\minicpbp\examples\data\Phoneme\Lexique383.tsv"
output_csv = "Lexique383.csv"

with open(input_tsv, 'r', encoding='utf-8') as tsv_in, \
     open(output_csv, 'w', encoding='utf-8', newline='') as csv_out:
    
    # Read with tab delimiter
    tsv_reader = csv.reader(tsv_in, delimiter='\t')
    # Write with comma delimiter
    csv_writer = csv.writer(csv_out, delimiter=',')
    
    for row in tsv_reader:
        csv_writer.writerow(row)

print("Conversion complete!")