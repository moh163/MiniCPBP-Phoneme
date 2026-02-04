from transformers import AutoModelForCausalLM, AutoTokenizer

#model_name = "mistralai/Mistral-7B-Instruct-v0.3"
model_name = "NousResearch/Hermes-2-Pro-Mistral-7B"
print(f"Téléchargement de {model_name} dans le cache...")

# Cela va télécharger les fichiers dans ton HF_HOME défini plus haut
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForCausalLM.from_pretrained(model_name)

print("Téléchargement terminé.")