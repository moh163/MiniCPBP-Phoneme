#!/bin/bash
#SBATCH --job-name=mistral_exp_fr_en
#SBATCH --account=def-pesantg    # <--- REMPLACE CECI par ton compte (ex: def-prof)
#SBATCH --time=02:00:00            # Temps max (2 heures)
#SBATCH --gres=gpu:1               # 1 GPU requis pour le serveur Python
#SBATCH --cpus-per-task=4          # 4 CPUs
#SBATCH --mem=32G                  # 32 Go RAM
#SBATCH --output=logs/experience-%j.out  # Fichier de log Slurm

# ==============================================================================
# 0. CONFIGURATION
# ==============================================================================
# Chemins (Ajuste si nécessaire)
VENV_PATH="$SCRATCH/orthophonie/venv" 
HF_CACHE="$SCRATCH/orthophonie/hf_cache"
PORT=5000

# Attention : vérifie bien le nom exact du JAR dans le dossier target après le build
JAR_PATH="target/minicpbp-1.0.jar" 
MAIN_CLASS="minicpbp.examples.NLP_v5"
ITERATIONS=3
LLM_MODEL="Mistral"

# Création du dossier de logs pour les sorties Python
mkdir -p logs

# ==============================================================================
# 1. ENVIRONNEMENT & BUILD MAVEN
# ==============================================================================
echo "=== [1/5] Chargement de l'environnement ==="
module load python/3.10 java/17 maven/3.9.10
source $VENV_PATH/bin/activate

# Configuration Hugging Face pour le mode hors-ligne
export HF_HOME=$HF_CACHE
export HF_HUB_OFFLINE=1 
export TRANSFORMERS_OFFLINE=1

# Gestion propre de l'arrêt du serveur (si on annule le job avec scancel)
cleanup() {
    echo "Arrêt du serveur Python..."
    if [ ! -z "$SERVER_PID" ]; then
        kill $SERVER_PID
    fi
    exit
}
trap cleanup SIGINT SIGTERM EXIT

echo "=== [2/5] Compilation Maven ==="
# On build avant de lancer le serveur pour ne pas gaspiller de temps GPU si le build plante
mvn clean install -DskipTests -o

if [ $? -ne 0 ]; then
    echo "❌ Erreur critique : La compilation Maven a échoué."
    exit 1
fi
echo "✅ Compilation réussie."

# ==============================================================================
# 2. DÉMARRAGE DU SERVEUR PYTHON
# ==============================================================================
echo "=== [3/5] Démarrage du Serveur Python ==="

# On lance le serveur en arrière-plan (&) et on redirige sa sortie vers un fichier log
# (Comme le phonemizer n'est pas critique, on lance avec un paramètre par défaut)
python server_cleaned.py --port $PORT --language en-us > logs/server_python.log 2>&1 &
SERVER_PID=$!

echo "Serveur lancé avec PID $SERVER_PID. En attente du chargement du modèle..."

# Boucle d'attente active jusqu'à ce que le serveur réponde "pong"
MAX_RETRIES=240 # environ 2 minutes (60 * 2s)
COUNT=0
SERVER_READY=0

while [ $COUNT -lt $MAX_RETRIES ]; do
    if curl -s "http://localhost:$PORT/ping" > /dev/null; then
        SERVER_READY=1
        break
    fi
    sleep 2
    COUNT=$((COUNT+1))
    echo -n "."
done
echo ""

if [ $SERVER_READY -eq 0 ]; then
    echo "❌ Erreur : Le serveur n'a pas répondu après 2 minutes. Voir logs/server_python.log"
    cat logs/server_python.log # Affiche les dernières lignes d'erreur
    exit 1
fi
echo "✅ Serveur prêt et modèle chargé sur le GPU !"

# ==============================================================================
# 3. EXÉCUTION DES TESTS JAVA
# ==============================================================================

echo "=== [4/5] Exécution Java : FRANÇAIS ==="
echo "Lancement de NLP_v5 pour Mistral (FR)..."
java -cp $JAR_PATH $MAIN_CLASS $ITERATIONS $PORT $LLM_MODEL fr

echo "----------------------------------------------------------------"

echo "=== [5/5] Exécution Java : ANGLAIS ==="
echo "Lancement de NLP_v5 pour Mistral (ENG)..."
java -cp $JAR_PATH $MAIN_CLASS $ITERATIONS $PORT $LLM_MODEL eng

echo "----------------------------------------------------------------"
echo "✅ TOUTES LES TÂCHES SONT TERMINÉES."

# Le 'trap' à la fin du script s'occupera de tuer le serveur automatiquement