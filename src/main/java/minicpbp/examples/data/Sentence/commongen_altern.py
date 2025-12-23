import json
import random

# Load the JSON file
file_path = "commongen_hard_nohuman.json"
with open(file_path, "r") as file:
    data = json.load(file)

# Collect all concepts from the dataset
all_concepts = set()
for entry in data:
    all_concepts.update(entry["concept_set"])

all_concepts = list(all_concepts)

# Function to replace one random concept with an unrelated one
def modify_entry(entry):
    # Select a random concept from the entry to replace
    concept_to_replace = random.choice(entry["concept_set"])
    
    # Find a replacement concept that is not already in the set
    replacement_concept = random.choice(all_concepts)
    while replacement_concept in entry["concept_set"]:
        replacement_concept = random.choice(all_concepts)
    
    # Replace the concept
    new_concept_set = [replacement_concept if c == concept_to_replace else c for c in entry["concept_set"]]
    
    # Update the instruction to reflect the new concept list
    new_instruction = entry["instruction"].replace(concept_to_replace.split('_')[0], replacement_concept.split('_')[0])
    
    # Modify the entry
    entry["concept_set"] = new_concept_set
    entry["instruction"] = new_instruction

# Apply the modifications to each entry
for entry in data:
    modify_entry(entry)

# Save the modified JSON file
modified_file_path = "modified_commongen.json"
with open(modified_file_path, "w") as file:
    json.dump(data, file, indent=4)

# Provide the path to the modified file
modified_file_path
