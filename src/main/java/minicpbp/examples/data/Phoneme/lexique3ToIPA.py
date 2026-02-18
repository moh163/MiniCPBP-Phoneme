import pandas as pd

# 1. Charger le fichier CSV Lexique383
# L'en-tête est sur la première ligne (par défaut)
df = pd.read_csv('src\main\java\minicpbp\examples\data\Phoneme\Lexique383.csv', low_memory=False)

# 2. Table de correspondance Lexique3 -> IPA
transcription_table = {
    # Voyelles orales
    'a': 'a',   # patte
    'i': 'i',   # pire
    'o': 'o',   # pot
    'u': 'u',   # pou
    'y': 'y',   # pu
    'e': 'e',   # pré
    'E': 'ɛ',   # prêt
    'O': 'ɔ',   # port
    '2': 'ø',   # peu
    '9': 'œ',   # peur
    '°': 'ə',   # le (schwa)
    
    # Voyelles nasales
    '@': 'ɑ̃',  # banc, sans 
    '5': 'ɛ̃',  # brin, plein
    '1': 'œ̃',  # brun
    '§': 'ɔ̃',  # bon, pont (comme dans "ôtons" -> ot§)
    
    # Semi-consonnes
    'j': 'j',   # yeux, fille
    'w': 'w',   # oui
    '8': 'ɥ',   # huit, lui
    
    # Consonnes
    'p': 'p', 
    'b': 'b', 
    't': 't', 
    'd': 'd', 
    'k': 'k', 
    'g': 'ɡ',
    'f': 'f', 
    'v': 'v', 
    's': 's', 
    'z': 'z',
    'S': 'ʃ',   # chat
    'Z': 'ʒ',   # jeu
    'm': 'm', 
    'n': 'n',
    'N': 'ɲ',   # agneau
    'G': 'ŋ',   # camping
    'l': 'l', 
    'R': 'ʁ'   # rat
}

def convert_lexique_to_ipa(phon_str):
    # Sécurité si la case est vide (NaN)
    if pd.isna(phon_str) or not isinstance(phon_str, str):
        return phon_str
    
    # Lexique3 n'utilise pas de séparateurs. 
    # On traduit simplement chaque caractère un par un.
    ipa_symbols = [transcription_table.get(char, char) for char in phon_str]
    return "".join(ipa_symbols)

# 3. Application du traitement sur la colonne 'phon'
if 'phon' in df.columns:
    df['phon_IPA'] = df['phon'].apply(convert_lexique_to_ipa)
    
    # 4. Sauvegarde
    output_name = 'Lexique383_with_IPA.csv'
    df.to_csv(output_name, index=False)
    print(f"Succès ! Colonne 'phon_IPA' ajoutée. Fichier sauvegardé sous : {output_name}")
    
    # Affichage d'un aperçu pour vérifier le résultat
    print("\nAperçu de la conversion :")
    print(df[['ortho', 'phon', 'phon_IPA']].head(10))
else:
    print("Erreur : La colonne 'phon' est introuvable.")
    print("Voici les colonnes disponibles dans votre fichier :", df.columns.tolist())