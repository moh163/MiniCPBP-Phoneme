from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
import torch

# Configuration du nom du modèle
model_name = "mistralai/Mistral-7B-Instruct-v0.3"

# --- 1. Chargement Intelligent (GPU) ---
print(f"Loading {model_name}...")

# Configuration 8-bit explicite
quantization_config = BitsAndBytesConfig(
    load_in_8bit=True,
    llm_int8_enable_fp32_cpu_offload=False # On veut tout sur le GPU
)

tokenizer = AutoTokenizer.from_pretrained(model_name)

# Le modèle va se placer tout seul sur le GPU grâce à device_map="auto"
model = AutoModelForCausalLM.from_pretrained(
    model_name, 
    device_map="auto", 
    quantization_config=quantization_config
)

# --- 2. Fonction de prédiction ---
def get_predictions(sentence):
    # On s'assure que les inputs vont sur le même device que le modèle (le GPU)
    inputs = tokenizer.encode(sentence, return_tensors="pt").to(model.device)
    
    with torch.no_grad():
        outputs = model(inputs)
        # outputs.logits est préférable à outputs[0] pour la clarté
        predictions = outputs.logits 
    return predictions

# --- 3. Exécution ---
print("Generating predictions...")
predictions = get_predictions("<s>Hello")

# On prend le dernier token généré (la prédiction pour la suite)
next_token_candidates_tensor = predictions[0, -1, :]
print(f"Logits shape: {next_token_candidates_tensor.shape}")

# --- 4. Sauvegarde du vocabulaire ---
topk_candidates_indexes = range(len(tokenizer)) # Utiliser len(tokenizer) est plus sûr

print("Writing dictionary...")
with open('tokenizer_dict.txt', 'w', encoding="UTF-8") as tokens_dict:
    for idx in topk_candidates_indexes:
        try:
            # CORRECTION ICI : On utilise convert_ids_to_tokens au lieu de decode
            # Cela renvoie le token brut (ex: " word" avec le caractère U+2581)
            raw_token = tokenizer.convert_ids_to_tokens(idx)
            
            # Si c'est un Byte (ex: <0x0A>), on le garde tel quel
            if raw_token.startswith("<0x") and raw_token.endswith(">"):
                token_str = raw_token
            else:
                # On remplace le caractère "Lower One Eighth Block" (\u2581) par un vrai espace
                # C'est ce caractère que Mistral utilise pour les espaces
                token_str = raw_token.replace(' ', ' ')
            
            # Nettoyage optionnel des sauts de ligne pour ne pas casser le format du fichier txt
            token_str = token_str.replace('\n', '\\n')
            
            tokens_dict.write(f"{idx}::{token_str}\n")
        except Exception as e:
            print(f"Error on id {idx}: {e}")
            pass

print("Done.")