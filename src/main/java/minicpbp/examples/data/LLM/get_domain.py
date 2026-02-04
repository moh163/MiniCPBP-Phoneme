from transformers import AutoModelForCausalLM, AutoTokenizer
import torch
import json
from lemminflect import getAllInflections
from pylexique import Lexique383
import argparse
import csv
import math

parser = argparse.ArgumentParser(description="Expand vocabulary and tokenize.")
parser.add_argument("--language", type=str, default='eng')
parser.add_argument("--percent", type=float, default=100.0, help="Percentage of most frequent words to keep")
args = parser.parse_args()

# --- FONCTIONS CORRIGEES ---

def EnglishInflections(lemset):
    words = []
    words.extend(list(lemset))
    for word in lemset:
        inflections = getAllInflections(word)
        for forms in inflections.values():
            words.extend(forms)
    return list(set(words))

def FrenchInflections(lemset):
    print("Loading French Lexicon...")
    lexique = Lexique383()
    
    print("Building lemma index...")
    lemma_to_forms = {}
    
    # Parcourir tout le lexique
    for form, data in lexique.lexique.items():
        # 1. CORRECTION "NOT ITERABLE" : 
        # Si 'data' n'est pas une liste, on le transforme en liste
        entries = data if isinstance(data, list) else [data]
        
        for entry in entries:
            # 2. CORRECTION ATTRIBUT : Utiliser .lemme (et non .lemma)
            try:
                if entry.lemme not in lemma_to_forms:
                    lemma_to_forms[entry.lemme] = set()
                lemma_to_forms[entry.lemme].add(form)
            except AttributeError:
                # Sécurité au cas où une entrée serait mal formée
                continue
            
    print("Index built.")
    
    words = []
    for lemma in lemset:
        found_forms = lemma_to_forms.get(lemma, [lemma])
        words.extend(found_forms)

    return list(set(words))

def load_words_from_csv(file_path):
    words_list = []
    try:
        with open(file_path, 'r', encoding="UTF-8") as file:
            # Si votre CSV utilise des points-virgules, ajoutez delimiter=';' dans csv.reader
            reader = csv.reader(file) 
            
            # On saute la ligne de titre (header)
            next(reader, None)
            
            for row in reader:
                # On prend la première colonne (index 0) si la ligne n'est pas vide
                if row:
                    words_list.append(row[0])
                    
    except FileNotFoundError:
        print(f"Erreur : Le fichier {file_path} est introuvable.")
        exit(1)
        
    return list(set(words_list)) # On enlève les doublons éventuels du CSV

# --- FONCTION DE LECTURE CSV ADAPTÉE (Anglais) ---
def load_words_from_csv_with_freq(file_path):
    word_candidates = []
    try:
        with open(file_path, 'r', encoding="UTF-8") as file:
            reader = csv.reader(file) 
            header = next(reader, None) # On lit l'en-tête
            if args.language=='fr':
                header = next(reader, None) # On lit l'en-tête

            
            # On cherche l'index de la colonne "Word" et "Log10WF" (Fréquence)
            # Si les colonnes changent, on adapte ici. Selon votre fichier ELP.csv :
            # Word est souvent col 0 ou 1, Log10WF est plus loin.
            # Pour être robuste, on cherche l'index par le nom.
            
            try:
                # Nettoyage des titres pour éviter les soucis de guillemets
                clean_header = [h.replace('"', '').strip() for h in header]
                idx_word = clean_header.index("Word")
                idx_freq = clean_header.index("Log10WF")
            except ValueError:
                print("Erreur: Colonnes 'Word' ou 'Log10WF' introuvables dans le CSV.")
                # Fallback manuel si les headers ne correspondent pas exactement
                idx_word = 0 
                idx_freq = 6 # Souvent la colonne Log10WF est la 7ème (index 6) dans ELP
            
            for row in reader:
                if row and len(row) > idx_freq:
                    word = row[idx_word].replace('"', '') # Nettoyage guillemets
                    
                    # Filtre anti-espace pour l'anglais aussi
                    if ' ' in word:
                        continue
                        
                    try:
                        freq = float(row[idx_freq])
                    except ValueError:
                        freq = -99.0 # Valeur très basse si erreur
                        
                    word_candidates.append((word, freq))
                    
    except FileNotFoundError:
        print(f"Erreur : Le fichier {file_path} est introuvable.")
        exit(1)
        
    return word_candidates
# --- LOGIQUE PRINCIPALE ---


words = []

if args.language == 'eng':
    print(f"Mode: English (ELP.csv) - Keeping top {args.percent}% most frequent words")
    path = r'src/main/java/minicpbp/examples/data/Phoneme/ELP.csv'
    
    # 1. Chargement avec fréquences
    word_candidates = load_words_from_csv_with_freq(path)
    print(f"Total eligible words found: {len(word_candidates)}")
    
    # 2. Tri par fréquence décroissante (Log10WF plus grand = plus fréquent)
    word_candidates.sort(key=lambda x: x[1], reverse=True)
    
    # 3. Découpage
    cutoff_index = int(len(word_candidates) * (args.percent / 100.0))
    top_words = word_candidates[:cutoff_index]
    words = [w[0] for w in top_words]
    
    if len(top_words) > 0:
        print(f"Most frequent word: '{top_words[0][0]}' (Log10WF: {top_words[0][1]})")
        print(f"Least frequent word kept: '{top_words[-1][0]}' (Log10WF: {top_words[-1][1]})")

elif args.language == 'fr':
    # print("Mode: French (Extracting exhaustive list from Lexique383)")
    # lexique = Lexique383()
    # # On récupère toutes les entrées
    # raw_words = list(lexique.lexique.keys())
    # print(f"Total entries found: {len(raw_words)}")
    # # FILTRAGE : On garde seulement les mots qui n'ont PAS d'espace
    # words = [w for w in raw_words if ' ' not in w]
    # print(f"Filtered vocabulary size (no spaces): {len(words)}")
    path = r'src/main/java/minicpbp/examples/data/Phoneme/eqol_infra_all_v1.2.csv'
    # On charge juste les mots, sans inflexion
    words = load_words_from_csv(path)
    print(f"Total vocabulary: {len(words)}")
else:
    raise ValueError("Language Not recognized (use 'eng' or 'fr')")



# --- TOKENIZATION ---

#model_name = "stabilityai/stablelm-zephyr-3b"
model_name = "Qwen/Qwen2.5-3B-Instruct"
device = 'cuda' if torch.cuda.is_available() else 'cpu'
print(f"Loading model {model_name} on {device}...")

#model = AutoModelForCausalLM.from_pretrained(model_name).to(device)
tokenizer = AutoTokenizer.from_pretrained(model_name)

tokens = []
tokenized_words = []

print("Tokenizing...")
for word in words:
    token_ids = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(" " + word))
    tokens.extend(token_ids)
    tokenized_words.append(token_ids)
    
    token_ids = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(" " + word.capitalize()))
    tokens.extend(token_ids)
    tokenized_words.append(token_ids)

tokens = list(set(tokens))
print(f"Final unique tokens: {len(tokens)}")

# --- SAUVEGARDE ---
suffix = "_French" if args.language == 'fr' else ""

with open(f'corpus_domain{suffix}.json', 'w', encoding="UTF-8") as f:
    json.dump(tokens, f)
    
with open(f'corpus_words{suffix}.json', 'w', encoding="UTF-8") as f:
    json.dump(words, f)
    
with open(f'corpus_tokenized_words{suffix}.json', 'w', encoding="UTF-8") as f:
    json.dump(tokenized_words, f)

print("Done.")