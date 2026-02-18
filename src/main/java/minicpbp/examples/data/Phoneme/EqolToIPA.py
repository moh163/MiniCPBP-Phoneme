import pandas as pd

# 1. Charger le fichier CSV
# Note: le fichier semble utiliser la virgule comme séparateur
df = pd.read_csv('eqol_infra_all_v1.2.csv', header=1, low_memory=False)

# On supprime la première ligne de données si elle est vide (souvent le cas dans ÉQOL)
if df.iloc[0].isnull().all() or df.iloc[0].astype(str).str.contains(',').all():
    df = df.drop(0).reset_index(drop=True)

# 2. Table de correspondance (Mapping)
# Basé sur les standards ÉQOL / Lexique3 (table ÉQOL_infra)
transcription_table = {
    # Voyelles orales
    'i': 'i',   # iris, vie, lys
    'u': 'u',   # ours, loup
    'y': 'y',   # une, lune
    'e': 'e',   # égal, nez
    'E': 'ɛ',   # lève, aile, jouet
    'a': 'a',   # adulte, table
    'A': 'ɑ',   # âne, âge
    'O': 'ɔ',   # sol, boi
    'o': 'o',   # hôtel, eau, jaune
    '2': 'ø',   # deux, ceufs
    '9': 'œ',   # neuf, œuf, soeur
    '%': 'ə',   # atelier, ceci
    '#': '',    # amie, scierie, asseoir (liaison/muet)
    '°': '',
    # Voyelles nasales
    '§': 'ɔ̃',  # on, ombre
    '@': 'ɑ̃',  # antique, tente, septembre
    '5': 'ɛ̃',  # cinq, bain, chien, plein
    '1': 'œ̃',  # un, lundi, parfum
    # Semi-voyelles
    'j': 'j',   # lieu, paye, paille, fille
    'w': 'w',   # oie, fois, loin, western
    '8': 'ɥ',   # huit, lui, nuit
    # Consonnes occlusives
    'p': 'p',   # pin, loupe, appel
    't': 't',   # terre, vite, attaque
    'k': 'k',   # col, accord, qui, coq, kilo
    'b': 'b',   # brosse, cube
    'd': 'd',   # danse, aide, addition
    'g': 'ɡ',   # gare, guide, bague, toboggan
    # Consonnes fricatives
    'f': 'f',   # foule, affaire, phare
    's': 's',   # sol, ce, tasse, science, garçon
    'S': 'ʃ',   # chat, vache
    'v': 'v',   # vent, rêve
    'z': 'z',   # zéro, rose
    'Z': 'ʒ',   # jeudi, gel, bourgeon
    # Consonnes nasales
    'm': 'm',   # main, femme
    'n': 'n',   # nage, laine, panne
    'N': 'ɲ',   # ligne, peigne, ognon
    'G': 'ŋ',   # parking, ring
    # Autres consonnes
    'l': 'l',   # lune, aller, pull
    'R': 'ʁ',   # rue, air, arrière
}

def convert_to_ipa(phono_str):
    if pd.isna(phono_str):
        return phono_str
    
    # Nettoyage : enlever les points séparateurs (ex: .z.o.n.e -> zone)
    parts = list(phono_str)
    
    # Remplacement par les caractères IPA
    ipa_parts = [transcription_table.get(p, p) for p in parts]
    
    return " ".join(ipa_parts)

# 3. Appliquer la conversion
# On crée une nouvelle colonne 'phono_IPA'
print(df.columns)
df['phono_IPA'] = df['phono'].apply(convert_to_ipa)

# 4. Sauvegarder le résultat
df.to_csv('eqol_with_ipa.csv', index=False)

print("Conversion terminée. Aperçu :")
print(df[['ortho', 'phono', 'phono_IPA']].head(10))