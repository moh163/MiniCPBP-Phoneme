import os

from sympy import im
from pathlib import Path
print("Importing server...")
os.environ['PYTHONVERBOSE'] = '1'
try:
    print("Importing sys...")
    import sys
    print("Configuring stdout...")
    sys.stdout.reconfigure(line_buffering=True)
    print("Configuring stderr...")
    sys.stderr.reconfigure(line_buffering=True)
    print("Importing time...")
    import time
    print("Importing json...")
    import json
    print("Importing traceback...")
    import traceback
    print("Importing Flask and request...")
    from flask import Flask, request
    print("Importing torch...")
    start_time = time.time()
    import torch
    print("Done importing torch in", time.time() - start_time, "seconds")
    print("Importing AutoModelForCausalLM, AutoTokenizer...")
    os.environ['TRANSFORMERS_OFFLINE'] = '1'  # Skip online model checks
    os.environ['HF_HUB_DISABLE_TELEMETRY'] = '1'  # Disable telemetry
    start_time = time.time()
    from transformers import  AutoModelForCausalLM, AutoModelForMaskedLM, AutoTokenizer
    print("Done importing transformers in", time.time() - start_time, "seconds")
    print("Importing WordNetLemmatizer...")
    from nltk.stem import WordNetLemmatizer
    print("Importing wordnet corpus...")
    from nltk.corpus import wordnet
    print("Importing gc...")
    import gc
    print("Importing argparse...")
    import argparse
    print("Imports phonemizer")
    from phonemizer import phonemize
    from phonemizer.backend import EspeakBackend
    from phonemizer.separator import Separator
    print("Imports done.")
except Exception as e:
    print("Import error:", file=sys.stderr)
    traceback.print_exc(file=sys.stderr)

parser = argparse.ArgumentParser(description="Flask server for token prediction")
parser.add_argument('--port', type=int, default=5000, help='Port to run the server on')
parser.add_argument('--language',type=str, default='en-us', help='Language for the phonemizer (either en-us or fr-fr)')
args = parser.parse_args()
app = Flask(__name__)
app.json.ensure_ascii = False

gc.collect()

mask_string = "<mask>"

def initialize_phonemizer():
    try:
        # Configuration du backend espeak
        backend = EspeakBackend(language=args.language, 
                              preserve_punctuation=True,
                              with_stress=True)
        separator = Separator(phone=' ', syllable='|', word=' || ')
        return backend, separator
    except Exception as e:
        print(f"Error initializing phonemizer: {e}")
        return None, None

def text_to_phonemes(text, separator):
    try:
        # Convertir le texte en phonèmes
        phonemes = phonemizer_backend.phonemize(text,
                           separator=separator,
                           strip=True)
        return phonemes
    except Exception as e:
        print(f"Error in phonemization: {e}")
        return None

def get_predictions(sentence):
    # Encode the sentence using the tokenizer and return the model predictions.
    inputs = tokenizer.encode(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    return predictions

def get_next_token(sentence, temperature=1):
    predictions = get_predictions(sentence)
    next_token_candidates_tensor = predictions[0, -1, :]

    # Appliquer la température. 
    # T < 1.0 = plus confiant/répétitif
    # T > 1.0 = plus créatif/aléatoire
    print("Temperature set to: ", temperature, flush=True)
    next_token_candidates_tensor = next_token_candidates_tensor / temperature

    all_candidates_probabilities = torch.nn.functional.softmax(
        next_token_candidates_tensor, dim=-1).tolist()
    
    # Créer liste de (idx, prob) et trier par prob décroissante
    token_probs = [(idx, prob) for idx, prob in enumerate(all_candidates_probabilities)]
    token_probs.sort(key=lambda x: x[1], reverse=True)
    
    # Chercher le premier token valide (sans \n, \r)
    top_token = None
    for idx, prob in token_probs:
        decoded = tokenizer.decode([idx], skip_special_tokens=False)
        if "\n" not in decoded and "\r" not in decoded:
            top_token = decoded
            break
    
    if top_token is None:
        top_token = " "  # Fallback si aucun token valide trouvé

    print("DEBUG top_token:", repr(top_token), flush=True)
    print("DEBUG sentence:", repr(sentence), flush=True)
    # Filtrer probs finales pour retourner seulement tokens valides
    filtered_probs = [
        [idx, prob] for idx, prob in enumerate(all_candidates_probabilities) #TODO  verifier si il vaudrais mieux pas prendre les anciennes proba
        if "\n" not in tokenizer.decode([idx], skip_special_tokens=True) 
        and "\r" not in tokenizer.decode([idx], skip_special_tokens=True)
    ]
    
    return {
        "prob": filtered_probs,
        "sentence": sentence
    }

def get_next_word_probabilities(sentence, temperature=1):
    NEW_WORD=False
    wordStarted=False
    old_all_candidate_probabilities = None
    while not NEW_WORD:
        predictions = get_predictions(sentence)
        next_token_candidates_tensor = predictions[0, -1, :]

        # Appliquer la température. 
        # T < 1.0 = plus confiant/répétitif
        # T > 1.0 = plus créatif/aléatoire
        print("Temperature set to: ", temperature, flush=True)
        next_token_candidates_tensor = next_token_candidates_tensor / temperature

        all_candidates_probabilities = torch.nn.functional.softmax(
            next_token_candidates_tensor, dim=-1).tolist()
        
        # Créer liste de (idx, prob) et trier par prob décroissante
        token_probs = [(idx, prob) for idx, prob in enumerate(all_candidates_probabilities)]
        token_probs.sort(key=lambda x: x[1], reverse=True)
        
        # Chercher le premier token valide (sans \n, \r)
        top_token = None
        for idx, prob in token_probs:
            decoded = tokenizer.decode([idx], skip_special_tokens=False)
            if "\n" not in decoded and "\r" not in decoded:
                top_token = decoded
                break
        
        if top_token is None:
            top_token = " "  # Fallback si aucun token valide trouvé

        print("DEBUG top_token:", repr(top_token), flush=True)        
        if top_token.startswith(" ") or top_token.startswith("Ġ") or top_token == ".":
            if wordStarted:
                NEW_WORD = True
            else:
                wordStarted=True
                old_all_candidate_probabilities = all_candidates_probabilities
        else:
            sentence += top_token
    print("DEBUG sentence:", repr(sentence), flush=True)
    # Filtrer probs finales pour retourner seulement tokens valides
    filtered_probs = [
        [idx, prob] for idx, prob in enumerate(all_candidates_probabilities) #TODO  verifier si il vaudrais mieux pas prendre les anciennes proba
        if "\n" not in tokenizer.decode([idx], skip_special_tokens=True) 
        and "\r" not in tokenizer.decode([idx], skip_special_tokens=True)
    ]
    
    return {
        "prob": filtered_probs,
        "sentence": sentence
    }

def get_next_word_probabilitiesV5(sentence):
    
    # Get the model predictions for the sentence.
    predictions = get_predictions(sentence)
    
    # Get the next token candidates.
    next_token_candidates_tensor = predictions[0, -1, :]
    
    # Get the token probabilities for all candidates.
    all_candidates_probabilities = torch.nn.functional.softmax(
        next_token_candidates_tensor, dim=-1).tolist()
    token_probs = [(idx, prob) for idx, prob in enumerate(all_candidates_probabilities)]
    token_probs.sort(key=lambda x: x[1], reverse=True)
    top_token = None
    for idx, prob in token_probs:
        decoded = tokenizer.decode([idx], skip_special_tokens=False)
        if "\n" not in decoded and "\r" not in decoded:
            top_token = decoded
            break
        
    print("DEBUG top_token:", repr(top_token), flush=True)
 
    return {
        "prob": list(zip(range(0, len(next_token_candidates_tensor)), all_candidates_probabilities)),
        "sentence": sentence,
        "tokenized_last_word": tokenizer.convert_tokens_to_ids(tokenizer.tokenize(" " + sentence.split()[-1]))
    }
@app.route('/decode_ids', methods=['POST'])
def decode_ids():
    try:
        data = request.get_json(force=True)
        ids = data.get("ids", [])
        tokens = [tokenizer.decode([int(i)], skip_special_tokens=False) for i in ids]
        return {"ids": ids, "tokens": tokens}, 200
    except Exception as e:
        return {"error": str(e)}, 500
    
@app.route('/decode_id', methods=['POST'])
def decode_id():
    try:
        data = request.get_json(force=True)
        id = int(data.get("id", []))
        token = [tokenizer.decode([id])]
        return {"token": token}, 200
    except Exception as e:
        return {"error": str(e)}, 500

@app.route('/phonemize_ids', methods=['POST'])
def phonemize_ids():
    """
    Phonemise une liste de token IDs en batch.
    Entrée: {"ids": [int, ...]}
    Sortie: {"results": [{"id": int, "token": str, "phonemes": str}, ...]}
    """
    try:
        data = request.get_json(force=True)
        ids = data.get("ids", [])
        if not isinstance(ids, list):
            return {"error": "ids must be a list"}, 400

        results = []
        for token_id in ids:
            try:
                tok = tokenizer.decode([int(token_id)], skip_special_tokens=False)
                phon = None
                if phonemizer_backend is not None:
                    phon = text_to_phonemes(tok, phoneme_separator)
                    if "?" in phon:
                        print("word is: "+tok+" phonemes are: "+phon, flush=True)

                results.append({"id": int(token_id), "token": tok, "phonemes": phon})
            except Exception as e:
                return {"id": int(token_id), "token": tok, "phonemes": phon, "error": str(e)}, 400
        
        return {"results": results}, 200
    except Exception as e:
        import traceback, io
        buf = io.StringIO()
        traceback.print_exc(file=buf)
        return {"error": str(e), "trace": buf.getvalue()}, 500
    

       

    
@app.route('/batch_phonemize', methods=['POST'])
def batchPhonemization():
    """
    Phonemise une liste de mot en batch.
    Entrée: {"position": [int, ...]}
    Sortie: {"results": [{"id": int, "token": str, "phonemes": str}, ...]}
    """
    try:
        data = request.get_json(force=True)
        positions = data.get("position", [])
        if not isinstance(positions, list):
            return {"error": "positions must be a list"}, 400
        results = []
        
        corpus_domain_path = Path(f"./src/main/java/minicpbp/examples/data/LLM/QWEN2.5/corpus_tokenized_words.json")
        with open(corpus_domain_path, "r") as f:
            corpus_domain = json.load(f)
        for token_position in positions:
            try:
                if token_position < len(corpus_domain):
                    token_ids = corpus_domain[token_position]  # e.g., [498]
                    # Decode token IDs to get the actual word
                    tok = tokenizer.decode(token_ids, skip_special_tokens=False)
                else:
                    return {"error": f"Position {token_position} out of bounds"}, 400
                
                phon = None
                if phonemizer_backend is not None:
                    phon = text_to_phonemes(tok, phoneme_separator)
                    if "?" in phon:
                        print("word is: "+tok+" phonemes are: "+phon, flush=True)

                results.append({"id": int(token_position), "token": tok, "phonemes": phon})
            except Exception as e:
                return {"id": int(token_position), "token": "", "phonemes": None, "error": str(e)}, 400
        
        return {"results": results}, 200
    except Exception as e:
        import traceback, io
        buf = io.StringIO()
        traceback.print_exc(file=buf)
        return {"error": str(e), "trace": buf.getvalue()}, 500

    
#java -Xms2g -Xmx16g  -cp minicpbp-1.0.jar minicpbp.examples.MNREAD


def get_mask_distributions(sentence):
    inputs = mlm_tokenizer(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = mlm_model(**inputs)
        logits = outputs.logits

    mask_token_id = mlm_tokenizer.mask_token_id
    mask_positions = (inputs.input_ids == mask_token_id).nonzero(as_tuple=True)[1].tolist()
    mask_positions.sort()

    if sentence.startswith("<s>"):
        sentence = sentence[len("<s>"):].lstrip()
    if sentence.endswith("."):
        sentence = sentence[:-1].rstrip()
    mask_word_positions = [i for i, x in enumerate(sentence.split()) if x == mask_string]
    
    if len(mask_positions) != len(mask_word_positions):
        print(mask_positions, mask_word_positions)
        print(sentence)

    distributions = {}
    for idx_in_mask_positions, pos in enumerate(mask_positions):
        probs = torch.softmax(logits[0, pos], dim=-1).cpu().tolist()
        mask_word_pos = mask_word_positions[idx_in_mask_positions] if idx_in_mask_positions < len(mask_word_positions) else None
        distributions[int(pos)] = {
            "mask_index": int(pos),
            "mask_word_position": mask_word_pos,
            "tokens": list(range(len(probs))),
            "probs": probs
        }

    return distributions


try:
    print("Setting model_name...")
    #model_name = "meta-llama/Llama-3.2-3B"
    #model_name = "../Ctrl-G/ctrlg/gpt2-large_common-gen"
    #model_name ="stabilityai/stablelm-zephyr-3b"
    model_name = "Qwen/Qwen2.5-3B-Instruct"
    #model_name = "mistralai/Mistral-7B-Instruct-v0.3"


    print("Detecting device...")
    device='cuda' if torch.cuda.is_available() else 'cpu'
    if device == 'cuda':
        print("Using GPU")
        torch.cuda.set_device(0)
    else:
        print("Using CPU")

    # print("Loading model...")
    # model = AutoModelForCausalLM.from_pretrained(model_name, device_map="auto")
    print("Loading model with local_files_only=True...")
    model = AutoModelForCausalLM.from_pretrained(model_name, local_files_only=True).to(device)

    # print("Loading MLM model...")
    # mlm_model_name = "roberta-base"
    # mlm_model = AutoModelForMaskedLM.from_pretrained(mlm_model_name).to(device)
    # mlm_tokenizer = AutoTokenizer.from_pretrained(mlm_model_name)
    # print("MLM model ready")
    
    print("Loading tokenizer with local_files_only=True...")
    tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=True)

    print("Initializing phonemizer...")
    phonemizer_backend, phoneme_separator = initialize_phonemizer()

    # print("Getting next_token_candidates_tensor...")
    # next_token_candidates_tensor = get_predictions("Hello")[0, -1, :]

    # print("Printing length of next_token_candidates_tensor...")
    # print(len(next_token_candidates_tensor))

    # print("Printing current time...")
    # print(time.time())

    # print("Generating all_tokens...")
    # all_tokens = [tokenizer.decode([idx], skip_special_tokens=False) for idx in range(0, len(next_token_candidates_tensor)+1)]

    # print("Printing current time...")
    # print(time.time())

    # print("Generating all_lemmes_nouns...")
    # all_lemmes_nouns = [WordNetLemmatizer().lemmatize(token.strip().lower()) for token in all_tokens]

    # print("Printing current time...")
    # print(time.time())

    # print("Generating all_lemmes_verbs...")
    # all_lemmes_verbs = [WordNetLemmatizer().lemmatize(token.strip().lower(),"v") for token in all_tokens]

    # print("Printing current time...")
    # print(time.time())

    # print("Generating all_lemmes_adjectives...")
    # all_lemmes_adjectives = [WordNetLemmatizer().lemmatize(token.strip().lower(),"a") for token in all_tokens]

    # print("Printing current time...")
    # print(time.time())

    # print("Generating all_lemmes_adverbs...")
    # all_lemmes_adverbs = [WordNetLemmatizer().lemmatize(token.strip().lower(),"r") for token in all_tokens]

    # print("Printing current time...")
    # print(time.time())

    # print("Generating all_lemmes_satellites...")
    # all_lemmes_satellites = [WordNetLemmatizer().lemmatize(token.strip().lower(),"s") for token in all_tokens]

    # print("Printing current time...")
    # print(time.time())

    print("Ready")
except Exception as e:
    print("Error during model/tokenizer/lemmatizer setup:"+str(e), file=sys.stderr)
    traceback.print_exc(file=sys.stderr)
    sys.exit(1)



@app.route('/tokenize', methods=['POST'])
def get_tokens():
    return tokenizer.convert_tokens_to_ids(tokenizer.tokenize(request.data.decode()))
    # soft_constaint_flage=request.data.decode()[0]
    # if len(tokens) > 1:return [-1]+tokens
    # elif len(tokens) == 1: 
    #     if soft_constaint_flage == '1':
    #         similar_tokens=set()
    #         lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower())
    #         for index,lemme in enumerate(all_lemmes_nouns):
    #             if lemme == lemme_token:
    #                 similar_tokens.add(index)
    #         lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"v")
    #         for index,lemme in enumerate(all_lemmes_verbs):
    #             if lemme == lemme_token :
    #                 similar_tokens.add(index)
    #         lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"a")
    #         for index,lemme in enumerate(all_lemmes_adjectives):
    #             if lemme == lemme_token:
    #                 similar_tokens.add(index)
    #         lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"r")
    #         for index,lemme in enumerate(all_lemmes_adverbs):
    #             if lemme == lemme_token:
    #                 similar_tokens.add(index)
    #         lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"s")
    #         for index,lemme in enumerate(all_lemmes_satellites):
    #             if lemme == lemme_token:
    #                 similar_tokens.add(index)
    #         return [-2]+list(similar_tokens)
    #     else:
    #         return [-3] + tokens
    # else: return [-4]
    


@app.route('/')
def testing():

    probabilities = get_next_word_probabilities("<s>Hello")
    return probabilities


@app.route('/token', methods=['POST'])
def next_token():
    # Vous pouvez lire la température depuis les headers ou utiliser une valeur fixe
    # Une valeur de 1.2 ou 1.5 forcera beaucoup plus de variété
    raw_probs = get_next_word_probabilitiesV5(request.data.decode())

    return raw_probs

@app.route('/ping', methods=['GET'])
def ping():
    return 'pong', 200

@app.route('/phonemize', methods=['POST'])
def get_phonemes():
    try:
        text = request.data.decode()
        listStr = text.split(" ")
        phonemes = text_to_phonemes(listStr, phoneme_separator)
        return {"text": text, "phonemes": phonemes}
    except Exception as e:
        return {"error": str(e)}, 500

@app.route('/mlm', methods=['POST'])
def mlm_predict():
    try:
        
        sentence = request.data.decode()


        if mask_string not in sentence:
            return {"error": f"Sentence must contain a mask token ({mask_string})"}, 400

        distributions = get_mask_distributions(sentence)
        return distributions, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500
    
@app.route('/mlm_tokenize', methods=['POST'])
def mlm_tokenize():
    try:
        sentence = request.data.decode()
        tokens = tokenizer.tokenize(sentence)
        token_ids = tokenizer.convert_tokens_to_ids(tokens)
        return {"tokens": tokens, "token_ids": token_ids}, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500

if __name__ == '__main__':
    print("Starting server...")
    try:
        app.run(host="0.0.0.0", port=args.port)
    except Exception as e:
        exc_type = type(e).__name__
        print(f"Server crashed with exception type: {exc_type}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)  # Optional: Full stack trace
        sys.exit(1)
