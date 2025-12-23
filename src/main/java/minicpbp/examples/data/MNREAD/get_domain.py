from transformers import pipeline, AutoModelForCausalLM, AutoModelForMaskedLM, AutoTokenizer
import torch
import json
from lemminflect import getAllLemmas, getInflection, getAllInflections, getAllInflectionsOOV

def get_predictions(sentence):
    # Encode the sentence using the tokenizer and return the model predictions.
    inputs = tokenizer.encode(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    return predictions



with open(r'C:\Users\arnau\Documents\Ecole\MiniCPBP\src\main\java\minicpbp\examples\data\MNREAD\ENGLISH_LEMSET_CORRECTED.json', 'r', encoding="UTF-8") as file:
    lemset = json.load(file)

print(len(lemset))

words = list(lemset)
for word in lemset:
    inflections = getAllInflections(word)
    for forms in inflections.values():
        words.extend(forms)
words = list(set(words))

print(len(words))


model_name = "roberta-base"
device='cuda' if torch.cuda.is_available() else 'cpu'
#model = AutoModelForCausalLM.from_pretrained(model_name, device_map="auto")
model = AutoModelForMaskedLM.from_pretrained(model_name).to(device)
tokenizer = AutoTokenizer.from_pretrained(model_name)

tokens = []
tokenized_words= []
for word in words:
    token_ids = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(" "+word))
    tokens.extend(token_ids)
    tokenized_words.append(token_ids)
    token_ids = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(" "+word.capitalize()))
    tokens.extend(token_ids)
    tokenized_words.append(token_ids)
tokens = list(set(tokens))

print(len(tokens))

with open('corpus_domain.json', 'w', encoding="UTF-8") as tokens_dict:
    json.dump(tokens, tokens_dict)
    
with open('corpus_words.json', 'w', encoding="UTF-8") as words_file:
    json.dump(words, words_file)
    
with open('corpus_tokenized_words.json', 'w', encoding="UTF-8") as tokenized_words_file:
    json.dump(tokenized_words, tokenized_words_file)



