#!/bin/bash
#SBATCH --job-name=get_domain
#SBATCH --account=def-pesantg   
#SBATCH --time=01:00:00           # 1 heure (largement suffisant pour tokeniser)
#SBATCH --nodes=1                 # 1 seul nœud
#SBATCH --ntasks=1                # 1 tâche principale
#SBATCH --cpus-per-task=4         # 4 CPUs pour gérer les dictionnaires Python
#SBATCH --mem=16G                 # 16 Go RAM (Lexique383 et ELP peuvent être gourmands)
#SBATCH --output=logs/tokenization-%j.out  # Fichier de log (crée le dossier logs avant !)

# 1. Chargement de l'environnement
echo "Chargement des modules..."
module load python/3.10 scipy-stack

# 2. Activation du VENV et configuration Cache
# (Assure-toi que le chemin est bon)
source $SCRATCH/orthophonie/venv/bin/activate
export HF_HOME=$SCRATCH/orthophonie/hf_cache
export HF_HUB_OFFLINE=1

# 3. Vérification des dossiers
mkdir -p logs

# 4. Exécution pour l'ANGLAIS
# echo "------------------------------------------------"
# echo "Début du traitement : ANGLAIS"
# echo "------------------------------------------------"
# python src/main/java/minicpbp/examples/data/LLM/get_domain.py --language eng --percent 70 

# 5. Exécution pour le FRANÇAIS
echo "------------------------------------------------"
echo "Début du traitement : FRANÇAIS"
echo "------------------------------------------------"
python src/main/java/minicpbp/examples/data/LLM/get_domain.py --language fr --percent 70

echo "------------------------------------------------"
echo "Terminé avec succès."