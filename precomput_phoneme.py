import argparse
import json
from pathlib import Path

import requests


def precompute_phonemes(llm_name="QWEN2.5", output_file="phoneme_cache.json"):
    """
    Pré-calcule les phonèmes pour tout le corpus et les sauvegarde dans un fichier JSON.
    """
    
    # Charger tokenizer_dict.txt
    #tokenizer_path = Path(f"./src/main/java/minicpbp/examples/data/LLM/{llm_name}/tokenizer_dict.txt")
    corpus_domain_path = Path(f"./src/main/java/minicpbp/examples/data/LLM/{llm_name}/corpus_tokenized_words.json")
    
    # print(f"Chargement du tokenizer depuis {tokenizer_path}...")
    # tokenizer_dict = {}
    # with open(tokenizer_path, "r", encoding="utf-8") as f:
    #     for line in f:
    #         if "::" in line:
    #             idx_str, token = line.split("::", 1)
    #             tokenizer_dict[int(idx_str)] = token.rstrip('\n')
    
    print(f"Chargement du corpus_domain depuis {corpus_domain_path}...")
    with open(corpus_domain_path, "r") as f:
        corpus_domain = json.load(f)
    
    print(f"Corpus size: {len(corpus_domain)}")
    #print(f"Tokenizer size: {len(tokenizer_dict)}")
    
    # Appeler /phonemize_ids en batch (chunks de 100 pour éviter timeout)
    phoneme_cache = {}
    
    print(f"\nPhonémisation...")
        
    try:
        response = requests.post(
            "http://localhost:5000/batch_phonemize",
            json={"position": list(range(len(corpus_domain)))}
        )
        
        if response.status_code == 200:
            data = response.json()
            for result in data.get("results", []):
                vocab_id = result.get("id")
                # Capturer correctement phonemes (peut être null ou string)
                phonemes_str = result.get("phonemes")
                if phonemes_str is None:
                    phonemes_str = ""
                
                # Convertir phonemes string en dict {phoneme: count}
                phoneme_counts = {}
                if phonemes_str and phonemes_str.strip():
                    phonemes = phonemes_str.split(" ")
                    for phoneme in phonemes:
                        if phoneme.strip():
                            phoneme_counts[phoneme] = phoneme_counts.get(phoneme, 0) + 1
                
                phoneme_cache[str(vocab_id)] = {
                    "token": result.get("token", ""),
                    "phonemes": phonemes_str,
                    "phoneme_counts": phoneme_counts
                }
            print("✓")
        else:
            print(f"✗ (status {response.status_code})")
            print(f"Response body: {response.text}")
    except Exception as e:
        print(f"✗ ({str(e)})")
    
    # Sauvegarder le cache
    output_path = Path(output_file)
    print(f"\nSauvegarde du cache dans {output_path}...")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(phoneme_cache, f, indent=2, ensure_ascii=False)
    
    print(f"✓ Pré-calcul terminé ! {len(phoneme_cache)} tokens phonémisés.")
    print(f"Fichier: {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Pré-calculer les phonèmes pour le corpus")
    parser.add_argument("--llm", type=str, default="QWEN2.5", help="Nom du LLM")
    parser.add_argument("--output", type=str, default="phoneme_cache.json", help="Fichier de sortie")
    parser.add_argument("--port", type=int, default=5000, help="Port du serveur Flask")
    args = parser.parse_args()
    
    precompute_phonemes(llm_name=args.llm, output_file=args.output)