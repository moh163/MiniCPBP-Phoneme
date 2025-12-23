#!/bin/bash
#SBATCH --time=05:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=nvidia_h100_80gb_hbm3_2g.20gb:1
#SBATCH --mem=24G
#SBATCH --output=outputs/stdout.log
#SBATCH --error=outputs/stderr.log


module load java/21.0.1

source venv/bin/activate
export JAVA_TOOL_OPTIONS="-Xmx6g"
OUTPUT_DIR="test_weigth_result"
mkdir -p "$OUTPUT_DIR"

# Create test cases log file
TEST_LOG_FILE="$OUTPUT_DIR/test_cases_executed.txt"
echo "Test Execution Log - $(date)" > "$TEST_LOG_FILE"
echo "=================================" >> "$TEST_LOG_FILE"
echo "" >> "$TEST_LOG_FILE"

which python
python --version
python -c "import torch; import transformers; print('Preload done')"

get_random_port() {
    local base_port=5000
    local range=100  # Try ports between 5000 and 5099
    local max_tries=50

    for ((i = 0; i < max_tries; i++)); do
        port=$((base_port + RANDOM % range))
        if ! ss -tuln | grep -q ":$port "; then
            echo "$port"
            return 0
        fi
    done

    echo "No available port found near $base_port" >&2
    return 1
}

PORT=$(get_random_port)

if [ $? -eq 0 ]; then
    echo "Using port $PORT"
    echo "Server port used: $PORT" >> "$TEST_LOG_FILE"
    echo "" >> "$TEST_LOG_FILE"
    # You can now launch your server with $PORT
else
    echo "Failed to find available port"
    exit 1
fi


python -u server_cleaned.py --port "$PORT" > outputs/flask_combined.log 2>&1 &
SERVER_PID=$!


timeout=300
for ((i=0; i<timeout; i++)); do
    if curl -s "http://localhost:$PORT/ping" >/dev/null; then
        echo "Server ready on port $PORT"
        break
    else
        echo "Waiting for server... ($i)"
        sleep 1
    fi
done


if ! curl -s "http://localhost:$PORT/ping" >/dev/null; then
    echo "Server did not start in time."
    kill $SERVER_PID
    exit 1
fi

values=(0.1 0.4 0.6 0.8 1 1.2 1.5 1.8 2 2.5 3.0 3.7 4.5 5.0)


MAX_JOBS=3

# --- Function to run command with semaphore ---
run_with_semaphore() {
    local cmd="$1"
    local pids_array_name="$2"

    # Run command in background
    eval "$cmd" &
    local pid=$!
    eval "$pids_array_name+=(\$pid)"

    # If max jobs reached, wait for the first to finish
    eval 'local current_pids=( "${'"$pids_array_name"'[@]}" )'
    if (( ${#current_pids[@]} >= MAX_JOBS )); then
        wait "${current_pids[0]}"
        eval "$pids_array_name=(\"\${$pids_array_name[@]:1}\")"
    fi
}


NUM_RUNS=10

# Log general test parameters
echo "General Parameters:" >> "$TEST_LOG_FILE"
echo "  Number of runs per test: $NUM_RUNS" >> "$TEST_LOG_FILE"
echo "  Maximum parallel jobs: $MAX_JOBS" >> "$TEST_LOG_FILE"
echo "  Test values: ${values[*]}" >> "$TEST_LOG_FILE"
echo "" >> "$TEST_LOG_FILE"

# -----------------------------
# Run Sentence_cleaned in parallel
# -----------------------------
#pids=()
#echo "SENTENCE_CLEANED TEST CASES" >> "$TEST_LOG_FILE"
#echo "Started at: $(date)" >> "$TEST_LOG_FILE"
#for val in "${values[@]}"; do
#    echo "Running Sentence_cleaned with argument $val"
#    echo "  - Value: $val (Runs: $NUM_RUNS)" >> "$TEST_LOG_FILE"
#    run_with_semaphore "srun --exclusive -N1 -n1 java -cp target/minicpbp-1.0.jar minicpbp.examples.Sentence_cleaned $val $PORT $OUTPUT_DIR $NUM_RUNS" pids
#done
#
## Wait for remaining Sentence_cleaned
#for pid in "${pids[@]}"; do
#    wait $pid
#done
#echo "Completed at: $(date)" >> "$TEST_LOG_FILE"
#echo "" >> "$TEST_LOG_FILE"


# -----------------------------
# Run Sentence_old_commongen in parallel
# -----------------------------
# pids=()
# echo "SENTENCE_OLD_COMMONGEN TEST CASES" >> "$TEST_LOG_FILE"
# echo "Started at: $(date)" >> "$TEST_LOG_FILE"
# for val in "${values[@]}"; do
#     echo "Running Sentence_old_commongen with argument $val"
#     echo "  - Value: $val (Runs: $NUM_RUNS)" >> "$TEST_LOG_FILE"
#     run_with_semaphore "srun --exclusive -N1 -n1 java -cp target/minicpbp-1.0.jar minicpbp.examples.Sentence_old_commongen $val $PORT $OUTPUT_DIR $NUM_RUNS" pids
# done
#
# # Wait for remaining Sentence_old_commongen
# for pid in "${pids[@]}"; do
#     wait $pid
# done
# echo "Completed at: $(date)" >> "$TEST_LOG_FILE"
# echo "" >> "$TEST_LOG_FILE"


# -----------------------------
# Run CollieSent1 in parallel
# -----------------------------
pids=()
echo "COLLIESENT1_WORDS TEST CASES" >> "$TEST_LOG_FILE"
echo "Started at: $(date)" >> "$TEST_LOG_FILE"
for val in "${values[@]}"; do
    echo "Running CollieSent1 with argument $val"
    echo "  - Value: $val (Runs: $NUM_RUNS)" >> "$TEST_LOG_FILE"
    run_with_semaphore "srun --exclusive -N1 -n1 java -cp target/minicpbp-1.0.jar minicpbp.examples.CollieSent1_words $val $PORT $OUTPUT_DIR $NUM_RUNS" pids
done

# Wait for remaining CollieSent1
for pid in "${pids[@]}"; do
    wait $pid
done
echo "Completed at: $(date)" >> "$TEST_LOG_FILE"
echo "" >> "$TEST_LOG_FILE"

# -----------------------------
# Run MNREAD_words in parallel
# -----------------------------
pids=()
echo "MNREAD_WORDS TEST CASES" >> "$TEST_LOG_FILE"
echo "Started at: $(date)" >> "$TEST_LOG_FILE"
for val in "${values[@]}"; do
   echo "Running MNREAD_words with argument $val"
   echo "  - Value: $val (Runs: $NUM_RUNS)" >> "$TEST_LOG_FILE"
    run_with_semaphore "srun --exclusive -N1 -n1 java -cp target/minicpbp-1.0.jar minicpbp.examples.MNREAD_words $val $PORT $OUTPUT_DIR $NUM_RUNS" pids
done

# Wait for remaining MNREAD_words
for pid in "${pids[@]}"; do
    wait $pid
done
echo "Completed at: $(date)" >> "$TEST_LOG_FILE"
echo "" >> "$TEST_LOG_FILE"

# Final summary
echo "TEST EXECUTION SUMMARY" >> "$TEST_LOG_FILE"
echo "======================" >> "$TEST_LOG_FILE"
echo "Total test cases executed:" >> "$TEST_LOG_FILE"
echo "  - CollieSent1_words: ${#values[@]} parameter values × $NUM_RUNS runs = $((${#values[@]} * NUM_RUNS)) total executions" >> "$TEST_LOG_FILE"
echo "  - MNREAD_words: ${#values[@]} parameter values × $NUM_RUNS runs = $((${#values[@]} * NUM_RUNS)) total executions" >> "$TEST_LOG_FILE"
echo "" >> "$TEST_LOG_FILE"
echo "All tests completed at: $(date)" >> "$TEST_LOG_FILE"

kill $SERVER_PID