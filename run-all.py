import os
import re
import subprocess
import argparse
import time
import json
from concurrent.futures import ThreadPoolExecutor, as_completed


parser = argparse.ArgumentParser(description="Run all tasks in parallel.")
parser.add_argument("--input", type=str, help="Input file path", default=None)
parser.add_argument("--num-iter", "-i", type=int, help="Number of iterations", default=1)
parser.add_argument("--num-threads", "-t", type=int, help="Number of threads", default=1)
args = parser.parse_args()

INPUT_FILE = args.input
NUM_ITERATIONS = args.num_iter
NUM_THREADS = args.num_threads
# Constantes
#INPUT_FILE = None
#NUM_ITERATIONS = 1  # Nombre d'itérations par défaut
INPUT_SPLIT_DIR = "inputs_split"
OUTPUT_DIR = "outputs"
FINAL_OUTPUT = "resultats_combines.json"

BASE_PORT = 5000
#NUM_THREADS = 1
GPU_ID = "0"
TIMEOUT_SEC = 3000

# Crée les dossiers
os.makedirs(INPUT_SPLIT_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Étape 1 : découper le fichier d'entrée
def split_input_file():
    with open(INPUT_FILE, "r") as f:
        lines = f.readlines()
    chunk_size = (len(lines) + NUM_THREADS - 1) // NUM_THREADS
    for i in range(NUM_THREADS):
        chunk = lines[i * chunk_size:(i + 1) * chunk_size]
        with open(os.path.join(INPUT_SPLIT_DIR, f"part_{i}.txt"), "w") as out_f:
            out_f.writelines(chunk)

# Vérifie que le serveur Flask est prêt
def wait_for_server(port, timeout=10):
    import requests

    for _ in range(timeout):
        try:
            r = requests.get(f"http://localhost:{port}/ping")
            if r.ok:
                return
        except Exception:
            time.sleep(1)

# Lance un processus (serveur + appel Java)
def run_task(task_id, input_path, port):
    result_json = {}
    result_file = os.path.join(OUTPUT_DIR, f"result_{task_id}.json")


    cmd = f'java -cp target/minicpbp-1.0.jar minicpbp.examples.OrthophonieTopk {input_path} {port} {task_id}'

    try:
        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            timeout=TIMEOUT_SEC
        )
        if result.returncode != 0:
            error_file = os.path.join(OUTPUT_DIR, f"error_{task_id}.txt")
            result_json = {
                "task_id": task_id,
                "error": f"Process exited with code {result.returncode}",
                "output": result.stdout,
                "stderr": result.stderr
            }
            with open(error_file, "w") as ef:
                json.dump(result_json, ef, indent=2)

    except Exception as e:
        e.printStackTrace()
        result_json = {
            "task_id": task_id,
            "error": str(e),
            "output": None
        }


    finally:
        if os.path.exists(result_file):
            with open(result_file, "r") as f:
                try:
                    result_json = json.load(f)
                except Exception as e:
                    result_json = {
                        "task_id": task_id,
                        "error": f"Failed to load result file: {e}",
                        "output": None
                    }
        

    return result_json

def main():
    start_time = time.time()
    # 1. Découper l'entrée
    if INPUT_FILE:
        split_input_file()

    combined_results = []
    
    port = BASE_PORT
    
    #  # Lance le serveur Python
    # server = subprocess.Popen(
    #     ["python", "server_cleaned.py", str(port)],
    #     env={**os.environ, "CUDA_VISIBLE_DEVICES": GPU_ID},
    #     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    # )

    # wait_for_server(port)

    with ThreadPoolExecutor(max_workers=NUM_THREADS) as executor:
        futures = []
        for i in range(NUM_THREADS):
            if INPUT_FILE:
                part_input = os.path.join(INPUT_SPLIT_DIR, f"part_{i}.txt")
            else:
                part_input = NUM_ITERATIONS
            futures.append(
                executor.submit(run_task, i, part_input, port)
            )

        for future in as_completed(futures):
            combined_results.append(future.result())

   # server.terminate()
    
    # 3. Combine tous les résultats
    with open(FINAL_OUTPUT, "w") as f:
        json.dump(combined_results, f, indent=2)

    print(f"✅ Résultats combinés écrits dans {FINAL_OUTPUT}")
    print(f"⏱️ Temps total écoulé : {time.time() - start_time:.2f} secondes")

if __name__ == "__main__":
    main()
