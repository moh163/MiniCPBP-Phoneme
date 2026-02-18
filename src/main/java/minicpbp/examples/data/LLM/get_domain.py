from transformers import AutoModelForCausalLM, AutoTokenizer
import torch
import json
# from lemminflect import getAllInflections
# from pylexique import Lexique383
import argparse
import csv
import math

parser = argparse.ArgumentParser(description="Expand vocabulary and tokenize.")
parser.add_argument("--language", type=str, default='fr')
parser.add_argument("--percent", type=float, default=100.0, help="Percentage of most frequent words to keep")
args = parser.parse_args()


# def EnglishInflections(lemset):
#     words = []
#     words.extend(list(lemset))
#     for word in lemset:
#         inflections = getAllInflections(word)
#         for forms in inflections.values():
#             words.extend(forms)
#     return list(set(words))

# def FrenchInflections(lemset):
#     print("Loading French Lexicon...")
#     lexique = Lexique383()
    
#     print("Building lemma index...")
#     lemma_to_forms = {}
    
#     # Parcourir tout le lexique
#     for form, data in lexique.lexique.items():
#         # 1. CORRECTION "NOT ITERABLE" : 
#         # Si 'data' n'est pas une liste, on le transforme en liste
#         entries = data if isinstance(data, list) else [data]
        
#         for entry in entries:
#             # 2. CORRECTION ATTRIBUT : Utiliser .lemme (et non .lemma)
#             try:
#                 if entry.lemme not in lemma_to_forms:
#                     lemma_to_forms[entry.lemme] = set()
#                 lemma_to_forms[entry.lemme].add(form)
#             except AttributeError:
#                 # Sécurité au cas où une entrée serait mal formée
#                 continue
            
#     print("Index built.")
    
#     words = []
#     for lemma in lemset:
#         found_forms = lemma_to_forms.get(lemma, [lemma])
#         words.extend(found_forms)

#     return list(set(words))

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
                if row and ' ' not in row[0]:
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

            try:
                # Nettoyage des titres pour éviter les soucis de guillemets
                clean_header = [h.replace('"', '').strip() for h in header]
                idx_word = clean_header.index("Word")
                idx_freq = clean_header.index("Log_Freq_HAL")
            except ValueError:
                print("Erreur: Colonnes 'Word' ou 'Log_Freq_HAL' introuvables dans le CSV.")
                # Fallback manuel si les headers ne correspondent pas exactement
                idx_word = 0 
                idx_freq = 4 # Souvent la colonne Log_Freq_HAL est la 7ème (index 6) dans ELP
            
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
        print(f"Most frequent word: '{top_words[0][0]}' (Log_Freq_HAL: {top_words[0][1]})")
        print(f"Least frequent word kept: '{top_words[-1][0]}' (Log_Freq_HAL: {top_words[-1][1]})")

elif args.language == 'fr':
    print(f"Mode: French (Lexique383_with_IPA) - Keeping top {args.percent}% most frequent words")
    
    # Mettez le bon chemin vers votre nouveau fichier
    path = r'src/main/java/minicpbp/examples/data/Phoneme/Lexique383_with_IPA.csv' 
    
    # Dictionnaire pour additionner les fréquences des mots qui ont plusieurs entrées (ex: "suis" verbe vs "suis" nom)
    word_freqs = {}
    
    try:
        with open(path, 'r', encoding="UTF-8") as file:
            # DictReader permet de récupérer les valeurs par le nom de la colonne
            # Si les colonnes sont séparées par des tabulations, ajoutez: delimiter='\t'
            reader = csv.DictReader(file) 
            
            for row in reader:
                # On récupère le mot (forme orthographique de surface)
                word = row.get('ortho', '').strip()
                # Filtre : pas d'espace (mots composés) et mot non vide
                if ' ' in word or not word:
                    continue
                    
                try:
                    # On récupère les deux fréquences (films et livres)
                    freq_films = float(row.get('freqlemfilms2', 0.0))
                    freq_livres = float(row.get('freqlemlivres', 0.0))
                    
                    # On crée un score global (Moyenne des deux modalités)
                    combined_freq = (freq_films + freq_livres) / 2.0
                    
                    # On additionne si le mot existe déjà dans le dictionnaire
                    if word in word_freqs:
                        word_freqs[word] += combined_freq
                    else:
                        word_freqs[word] = combined_freq
                        
                except ValueError:
                    continue 
                    
    except FileNotFoundError:
        print(f"Erreur : Le fichier {path} est introuvable.")
        exit(1)

    # On transforme le dictionnaire en liste de tuples (mot, frequence)
    word_candidates = list(word_freqs.items())
    print(f"Total unique words found: {len(word_candidates)}")
    
    # Tri par fréquence combinée décroissante
    word_candidates.sort(key=lambda x: x[1], reverse=True)
    
    # Découpage selon le pourcentage
    cutoff_index = int(len(word_candidates) * (args.percent / 100.0))
    top_words = word_candidates[:cutoff_index]
    words = [w[0] for w in top_words]
    
    if len(top_words) > 0:
        print(f"Most frequent word: '{top_words[0][0]}' (Score: {top_words[0][1]:.2f})")
        print(f"Least frequent word kept: '{top_words[-1][0]}' (Score: {top_words[-1][1]:.2f})")

else:
    raise ValueError("Language Not recognized (use 'eng' or 'fr')")

print(f"Final vocabulary size to tokenize: {len(words)}")

# --- TOKENIZATION ---

#model_name = "stabilityai/stablelm-zephyr-3b"
#model_name = "Qwen/Qwen2.5-3B-Instruct"
model_name = "mistralai/Mistral-7B-Instruct-v0.3"
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
    
    # token_ids = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(" " + word.capitalize()))
    # tokens.extend(token_ids)
    # tokenized_words.append(token_ids)

tokens = list(set(tokens))
print(f"Final unique tokens: {len(tokens)}")
print(f"Final word size: {len(words)}")
print(f"Final tokenized word size: {len(tokenized_words)}")

# --- SAUVEGARDE ---
suffix = "_French" if args.language == 'fr' else ""

with open(f'corpus_domain{suffix}.json', 'w', encoding="UTF-8") as f:
    json.dump(tokens, f)
    
with open(f'corpus_words{suffix}.json', 'w', encoding="UTF-8") as f:
    json.dump(words, f)
    
with open(f'corpus_tokenized_words{suffix}.json', 'w', encoding="UTF-8") as f:
    json.dump(tokenized_words, f)

print("Done.")