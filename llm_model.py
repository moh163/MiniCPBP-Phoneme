from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer#, AutoModelForMaskedLM,
import torch

def get_predictions(sentence):
    # Encode the sentence using the tokenizer and return the model predictions.
    inputs = tokenizer.encode(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    return predictions


#model_name = "stabilityai/stablelm-zephyr-3b"
#model_name ="roberta-base"
model_name = "mistralai/Mistral-7B-Instruct-v0.3"
device='cuda' if torch.cuda.is_available() else 'cpu'
model = AutoModelForCausalLM.from_pretrained(model_name, device_map="auto", load_in_8bit=True)
#model = AutoModelForMaskedLM.from_pretrained(model_name).to(device)
tokenizer = AutoTokenizer.from_pretrained(model_name)

#SPECIAL_SPACE_CHAR_ASCII_CODE = 9601


# Get the model predictions for the sentence.
predictions = get_predictions("<s>Hello")
#inputs = tokenizer("Hello <mask>!", return_tensors="pt").to(device)
# with torch.no_grad():
#         outputs = model(**inputs)
#         predictions = outputs.logits

next_token_candidates_tensor = predictions[0, -1, :]
# mask_positions = (inputs.input_ids == tokenizer.mask_token_id)[0].nonzero(as_tuple=True)[0].tolist()[0]
# next_token_candidates_tensor = predictions[0, mask_positions, :]

print(len(next_token_candidates_tensor))

# Get the top k next token candidates.
topk_candidates_indexes = range(0, len(next_token_candidates_tensor)+1)

# Decode the top k candidates back to words.
tokens = \
    [tokenizer.decode([idx], skip_special_tokens=False) for idx in topk_candidates_indexes]

# Return the top k candidates and their probabilities.

with open('tokenizer_dict.txt', 'w', encoding="UTF-8") as tokens_dict:
    for ind,token in enumerate(tokens):
        try:
            #corrected_token = tokenizer.tokenize(token)[-1].replace(chr(SPECIAL_SPACE_CHAR_ASCII_CODE), " ")
            tokens_dict.write(str(ind)+"::"+token+"\n")
        except:
            print(token)
            pass
    


