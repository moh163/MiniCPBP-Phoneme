import pandas as pd

def compter_lemmes(chemin_fichier):
    """
    Compte le nombre de lemmes uniques à partir du fichier CSV Lexique4.
    """
    print(f"Analyse du fichier {chemin_fichier} en cours...")
    
    # L'option low_memory=False évite les avertissements liés aux types de données
    df = pd.read_csv(chemin_fichier)
    
    # La colonne correspondant au lemme dans Lexique 4
    colonne_lemme = '4_Lemme'
    
    if colonne_lemme not in df.columns:
        raise ValueError(f"La colonne '{colonne_lemme}' est introuvable dans le fichier.")
    
    # dropna() supprime les éventuelles cases vides, unique() garde un seul exemplaire de chaque
    lemmes_uniques = df[colonne_lemme].dropna().unique()
    
    # On calcule la taille de la liste (le nombre d'éléments uniques)
    nombre_lemmes = len(lemmes_uniques)
    
    return nombre_lemmes

# ==========================================
# Exécution du script
# ==========================================
if __name__ == "__main__":
    # Nom exact du fichier
    fichier_csv = "src\main\java\minicpbp\examples\data\Phoneme\Lexique4.csv"
    
    try:
        total = compter_lemmes(fichier_csv)
        print("\n" + "="*40)
        print(f"RÉSULTAT : {total} lemmes différents ont été trouvés.")
        print("="*40)
    except FileNotFoundError:
        print(f"Erreur : Le fichier {fichier_csv} est introuvable. Vérifiez le chemin.")