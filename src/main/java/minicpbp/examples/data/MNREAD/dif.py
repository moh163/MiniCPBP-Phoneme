import json

# Load words from corpus_words.txt
with open('corpus_words.json', 'r') as file:
    corpus_words = set(word.strip().lower() for word in json.load(file))

# Load words from ENGLISH_LEMSET_CORRECTED.json
with open('ENGLISH_LEMSET_CORRECTED.json', 'r') as file:
    lemset_words = set(json.load(file))


# Find the difference
difference = corpus_words - lemset_words

missing = lemset_words - corpus_words
print("Words in ENGLISH_LEMSET_CORRECTED.json but not in corpus_words.txt:")
print(len(missing))
for word in missing:
    print(word)

# Print the difference
#print("Words in corpus_words.txt but not in ENGLISH_LEMSET_CORRECTED.json:")
#print(len(difference))
#for word in difference:
#    print(word)