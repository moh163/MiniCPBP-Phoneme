import glob
from pathlib import Path
import requests
import json
import unicodedata
from transformers import AutoTokenizer

def test_phonemize(test_words):
    # URL du serveur local
    url = "http://localhost:5000/phonemize"

    print("Testing phonemize endpoint...")
    print("-" * 50)

    try:
        payload = " ".join(test_words)
        # Envoyer la requête POST
        response = requests.post(url, data=payload.encode('utf-8'))

        # Vérifier si la requête a réussi
        if response.status_code == 200:
            result = response.json()
            print(f"\nInput text: {result['text']}")
            print(f"Phonemes: {result['phonemes']}")
        else:
            print(f"\nError for word '{test_words}': {response.status_code}")
            print(response.text)

    except Exception as e:
        print(f"\nException occurred for word '{test_words}': {str(e)}")

    print("\nTest completed!")

def test_token(phrase):
    url = "http://localhost:5000/token"
    phonemeizeURL = "http://localhost:5000/phonemize"
    print("Testing Token endpoint...")
    print("-" * 50)
    try:
        response = requests.post(url, data=phrase, timeout=100)
    except Exception as e:
        print("Request failed:", e)
        return

    print("Status:", response.status_code)
    if response.status_code != 200:
        print("Body:", response.text)
        return

    j = response.json()
    probs = j.get("prob", [])
    if not probs:
        print("No probabilities returned.")
        return

    # probs expected as list of [token_id, probability] or [token_id, score]
    # select token with largest probability/score
    # best = max(probs, key=lambda x: float(x[1]))
    # token_id = int(best[0])
    probs.sort(key=lambda x: float(x[1]), reverse=True)
    top_ids = [tid for tid, _ in probs[:10]]
    # Try server-side decode endpoint first
    token_text = []
    try:
        dec = requests.post("http://localhost:5000/decode_ids", json={"ids": top_ids}, timeout=10)
        if dec.status_code == 200:
            dj = dec.json()
            tokens = dj.get("tokens") or dj.get("decoded") or []
            # tokens should correspond to top_ids order
            token_texts = tokens
    except Exception as e:
        # ignore, we'll fallback to local mapping
        print("decode_ids request failed:", e)

    chosen_token_text = token_texts[0] if token_texts else f"<id:{top_ids[0]}>"

    # append to phrase and print
    new_phrase = phrase + chosen_token_text
    print("Selected token id:", top_ids[0])
    print("Decoded token:", chosen_token_text)
    print("New phrase:", new_phrase)
    print("\nTop 10 ids and scores:")
    for i in range(10):
        tid, score = probs[i]
        decoded = token_texts[i]
        resp = requests.post(phonemeizeURL, decoded)
        phonemized= resp.json()["phonemes"]
        print(f"{i+1:2d}. id={tid} score={score:.6f} token={decoded} phonemes={phonemized}")

    return new_phrase

def test_phonemize_ids(json):
    response = requests.post(
        "http://localhost:5000/phonemize_ids",
        json=json,
        timeout=10
    )

    print(response.json())
    
def test_code(codes):
    results = {}

    for code in codes:
        char = chr(code)
        try:
            name = unicodedata.name(char)
        except ValueError:
            name = "Unknown"
        results[code] = {'char': char, 'name': name}

    print(results)

def import_tokenizer():
    tokenizer = AutoTokenizer.from_pretrained("Qwen/Qwen2.5-3B-Instruct")

    # Exporter le vocabulaire complet
    vocab = tokenizer.get_vocab()
    
    # CORRECTION ICI : ajout de encoding="utf-8"
    with open("tokenizer_dict_complete.txt", "w", encoding="utf-8") as f:
        for token, idx in sorted(vocab.items(), key=lambda x: x[1]):
            # Petite sécurité supplémentaire : on remplace les caractères impossibles à écrire s'il y en a
            # (bien que utf-8 devrait tout gérer, certains tokens de contrôle peuvent être capricieux)
            try:
                f.write(f"{idx}::{token}\n")
            except Exception as e:
                print(f"Impossible d'écrire le token ID {idx}: {e}")

    # Trouver le BOS token
    print(f"BOS token: {tokenizer.bos_token}")
    print(f"BOS token ID: {tokenizer.bos_token_id}")
    
if __name__ == "__main__":
    # Liste de mots à tester
    test_words = [
        "hello",
        "world",
        "test",
        "phoneme",
        "This is a complete sentence.",
        "Multiple words with spaces"
    ]
    result = test_phonemize(["lundi", "parfum"])
    print(result)
    # phrase = "The is no denying that"
    # for i in range(3):
    #     phrase = test_token(phrase)
    #json = {"ids": [760, 1563, 954, 6495, 13140, 1223, 1293]}
    #test_phonemize_ids(json)
    #test_code([809])
    #import_tokenizer()
