import pandas as pd
import requests
from tqdm import tqdm # Pour afficher une barre de progression

# Configuration
INPUT_FILE = 'ELP.csv'
OUTPUT_FILE = 'ELP_with_IPA_server.csv'
SERVER_URL = "http://localhost:5000/phonemize"
BATCH_SIZE = 100  # Nombre de mots envoyés par requête (plus rapide)

def phonemize_csv():
    # 1. Charger le CSV
    print(f"Chargement de {INPUT_FILE}...")
    df = pd.read_csv(INPUT_FILE, low_memory=False)
    
    # On s'assure que la colonne Word est bien du texte
    words = df['Word'].astype(str).tolist()
    all_phonemes = []

    print(f"Début de la phonemisation de {len(words)} mots...")

    # 2. Traitement par lots
    for i in tqdm(range(0, len(words), BATCH_SIZE)):
        batch = words[i : i + BATCH_SIZE]
        
        # On joint les mots par des sauts de ligne pour le serveur
        payload = " ".join(batch)
        
        try:
            response = requests.post(SERVER_URL, data=payload.encode('utf-8'))
            
            if response.status_code == 200:
                result = response.json()
                # On récupère la chaîne de phonèmes et on la sépare par ligne
                # Le serveur renvoie généralement les phonèmes séparés par le même séparateur (newline)
                batch_result = result['phonemes'].strip().split('||')
                
                # Sécurité : si le nombre ne correspond pas, on traite mot par mot pour ce lot
                if len(batch_result) == len(batch):
                    all_phonemes.extend(batch_result)
                else:
                    # Mode secours : mot par mot pour ce lot spécifique
                    print("Erreur passage en mot par mot")
                    for word in batch:
                        r = requests.post(SERVER_URL, data=word.encode('utf-8'))
                        all_phonemes.append(r.json().get('phonemes', 'ERROR'))
            else:
                print(f"\nErreur serveur au lot {i}: {response.status_code}")
                all_phonemes.extend(["ERROR"] * len(batch))
                
        except Exception as e:
            print(f"\nErreur de connexion : {e}")
            all_phonemes.extend(["ERROR"] * len(batch))

    # 3. Ajout de la colonne et sauvegarde
    # On s'assure que la longueur correspond avant d'ajouter
    if len(all_phonemes) == len(df):
        df['Word_IPA'] = all_phonemes
        df.to_csv(OUTPUT_FILE, index=False)
        print(f"\nSuccès ! Fichier sauvegardé sous : {OUTPUT_FILE}")
    else:
        print(f"\nErreur de taille : {len(all_phonemes)} phonèmes pour {len(df)} mots.")

if __name__ == "__main__":
    phonemize_csv()