/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (c)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 *
 * mini-cpbp, replacing classic propagation by belief propagation
 * Copyright (c)  2019. by Gilles Pesant
 */

package minicpbp.examples;

import minicpbp.cp.Factory;
import minicpbp.engine.constraints.Circuit;
import minicpbp.engine.constraints.Element1D;
import minicpbp.engine.constraints.LessOrEqual;
import minicpbp.engine.constraints.Markov;
import minicpbp.engine.core.BoolVar;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.Objective;
import minicpbp.util.exception.InconsistencyException;
import minicpbp.util.io.InputReader;
import minicpbp.search.SearchStatistics;
import minicpbp.state.StateManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ibm.icu.impl.Pair;

public class CollieSent1_words {
    public static void main(String[] args) throws Exception {
        
            System.out.println("6 Aout");
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[3]);
            final double weight = Double.parseDouble(args[0]);
        try {
        final String llm_name="zephyr";//

        long startTime = System.currentTimeMillis();

        List<Logging>  logs = new ArrayList<>();

        List<String> lines = Collections.emptyList();
         try {
             lines = Files.readAllLines(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/tokenizer_dict.txt"),StandardCharsets.UTF_8);
         }
         catch (Exception e) {
             e.printStackTrace();
         }

    
         int token_size = Integer.parseInt(lines.get(lines.size()-1).split(":")[0]);

         String[] corrected_lines = new String[token_size];
         Arrays.fill(corrected_lines, "");
         for(int i=0;i<lines.size();i++){
             String[] line = lines.get(i).split("::");
             if(line.length>1){
                 corrected_lines[Integer.parseInt(line[0])]=line[1];
             }
         }
 
        final List<String> tokens_list = Arrays.asList(corrected_lines);
        ArrayList<String> words = new ArrayList<>();
        Map<Integer, List<Integer>> corpusDomainsSet = new HashMap<>();
        Map<Integer, Integer> corpusDomainToIndex = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        int k = 0;
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/corpus_tokenized_words.json")), StandardCharsets.UTF_8);
            final List<List<Integer>> parsedCorpusDomains = objectMapper.readValue(jsonContent, new TypeReference<List<List<Integer>>>() {}); 
            for (int i = 0; i < parsedCorpusDomains.size(); i++) {
                List<Integer> sublist = parsedCorpusDomains.get(i);
                if (sublist.size() != 1) continue;
                String word_string = sublist.stream().map(n -> tokens_list.get(n)).collect(Collectors.joining(""));
                if (words.contains(word_string)) {
                    continue;
                }
                words.add(word_string);
                if (!corpusDomainsSet.containsKey(sublist.get(0)))
                    corpusDomainsSet.put(sublist.get(0), new ArrayList<>(List.of(k)));
                else
                    corpusDomainsSet.get(sublist.get(0)).add(k);
                corpusDomainToIndex.put(k, sublist.get(0));
                k++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int sentence_end=15;

        words.add(".");
        words.add("<END>");
        corpusDomainsSet.put(sentence_end, new ArrayList<>(Collections.singletonList(words.size()-2)));
        corpusDomainsSet.put(tokens_list.size()-1, new ArrayList<>(Collections.singletonList(words.size()-1)));
        corpusDomainToIndex.put(words.size()-2, sentence_end);
        corpusDomainToIndex.put(words.size()-1, tokens_list.size()-1);

        List<Integer> corpusDomains = new ArrayList<>();
        for (int idx = 0; idx < words.size(); idx++) {
            corpusDomains.add(idx);
        }


        System.out.println("corpusDomains size: " + corpusDomains.size());
        final int final_sentence_end = corpusDomains.get(corpusDomains.size()-2);
        final int pad_token = corpusDomains.get(corpusDomains.size()-1);



        int[] start_words  = new int[corpusDomains.size()];
        for(int i=0; i<corpusDomains.size(); i++){
            if(words.get(corpusDomains.get(i)).strip().length()!=0 && words.get(corpusDomains.get(i)).charAt(0)==' '){
                start_words[i]=1;
            }
        }

        List<Integer> listCharNum = new ArrayList<>();
        for (int j = 0; j < corpusDomains.size(); j++) {
            int domainIndex = corpusDomains.get(j);
            String word = words.get(domainIndex);
            if(j==pad_token){
                listCharNum.add(0);
                continue;
            }
            listCharNum.add(word.length());
        }

 
        final int MAX_NUMBER_SPACE = 5;
        final int MIN_NUMBER_WORD = 9;
        final int MAX_NUMBER_WORD = 15;
        final int NUMBER_CHAR = 82;//Verify if you need to count the spaces at the beginning of lines
        final boolean PRINT_TRACE = false;
        final int NUM_PB = 3;
        final double w = weight;
        final int SENTENCE_MAX_NUMBER_TOKENS = MAX_NUMBER_WORD +1;
        final int ORACLE_TOP_K = 500;
        //final int NUM_ITERATIONS = 8;

        
        double initTime = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.println("Initialization time (s): " + initTime);

        String[] tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];
        for (int z = 0; z < NUM_ITERATIONS; z += 1) {
        
        tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];
        String[] commonWords = {
        "I",
        "You",
        "He",
        "She",
        "It",
        "We",
        "They",
        "The",
        "A",
        "An",
        "This",
        "That",
        "These",
        "Those",
        "There"};

        String selectedWord = " "+commonWords[new Random().nextInt(commonWords.length)];
        
        int index_word=-1;
        for (int i=0; i<words.size(); i++) {
            if (words.get(i).equals(selectedWord)) {
                index_word = i;
                break;
            }
        }
        if (index_word == -1) {
            words.add(selectedWord);
            corpusDomains.add(words.size()-1);
            index_word = words.size()-1;
            listCharNum.add(selectedWord.length());
        }
        



        //Words regular
        //cp.post(Factory.regular(word_index, table, 0,List.of(1) ));

        HttpClient client = HttpClient.newHttpClient();



        //word_index[0].assign(index_word);

        String instruction = "Please generate a sentence with exactly 82 characters. Include whitespace into your character count.";
        String current_sentence = selectedWord;
        Double logSumProbs = 0.0;
        int num_tok=1;
        while(num_tok<SENTENCE_MAX_NUMBER_TOKENS){
            int i = num_tok;
            System.out.println(num_tok);
            System.out.println(current_sentence);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/token"))
                .POST(HttpRequest.BodyPublishers.ofString(instruction + current_sentence))
                .build();
            String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

            int[] tokens = new int[corpusDomains.size()];
            double[] scores = new double[corpusDomains.size()];

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response);
            ArrayNode tupleList = (ArrayNode) jsonNode.get("prob");

            current_sentence = jsonNode.has("sentence") ? jsonNode.get("sentence").asText().replace(instruction, "") : "";
            System.out.println(current_sentence);
            String[] splitWords = current_sentence.replace(",", "").strip().split("\\s+");

            for (String word : splitWords) {
                word = " " + word;
                if (!words.contains(word)) {
                    words.add(word);
                    corpusDomains.add(words.size() - 1);
                    listCharNum.add(word.length());
                }
            }

            int[] charNum = listCharNum.stream().mapToInt(Integer::intValue).toArray();


            Solver cp = makeSolver();
            IntVar[] word_index = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, corpusDomains.size()-1);
            IntVar[] num_char = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, Arrays.stream(charNum).min().getAsInt(), Arrays.stream(charNum).max().getAsInt());

            for (int j=0; j<SENTENCE_MAX_NUMBER_TOKENS; j++){
                word_index[j].setName("word_index["+j+"]");
                cp.post(element(charNum, word_index[j], num_char[j]));
            }

            //IntVar nb_char = makeIntVar(cp, (int)Math.round(NUMBER_CHAR - 0.05 * NUMBER_CHAR), (int)Math.round(NUMBER_CHAR + 0.05 * NUMBER_CHAR));
            
            cp.post(sum(num_char, NUMBER_CHAR));
            //cp.post(sum(num_char, nb_char));


            List<Integer> acceptedState = new ArrayList<>();
            int[][] A = new int[2][corpusDomains.size()];
            acceptedState.add(1);
            acceptedState.add(0);
            Arrays.fill(A[0], 0);
            A[0][final_sentence_end]=1;
            Arrays.fill(A[1], -1);
            A[1][pad_token]=1;
            cp.post(Factory.regular(word_index, A, 0, acceptedState));

            System.out.println("Words in the sentence: " + Arrays.toString(splitWords));

            // Assign each word in splitWords to word_index
            for (int idx = 0; idx < splitWords.length; idx++) {
                String word = " " + splitWords[idx];
                int corpusIdx = words.indexOf(word);
                if (corpusIdx == -1) {
                    System.out.println("Word not in corpus: " + word);
                    current_sentence += " ERROR";
                    break;
                }
                int domainIdx = corpusDomains.indexOf(corpusIdx);
                try {
                    word_index[idx].assign(domainIdx);
                } catch (Exception e) {
                    current_sentence += " ERROR";
                    System.out.println("Inconsistency detected when assigning word: " + word);
                    System.out.println(Arrays.toString(splitWords));
                    break;
                }
            }


            int max_token = -1;
            double max_score = 0;
            double total_score = 0;



            List<Pair<Integer, Double>> tokenScoreList = new ArrayList<>();
            for (JsonNode tuple : tupleList) {
                try {
                    int token = ( tuple.get(0)).asInt();
                    double score = ( tuple.get(1)).asDouble();
                    if (!corpusDomainsSet.containsKey(token)) continue;
                    if (score < 0) continue;
                    tokenScoreList.add(Pair.of(token, score));
                } catch (Exception e) {
                    if (PRINT_TRACE) {
                        System.err.println(tuple);
                        System.err.println(e);
                    }
                }
            }

            tokenScoreList.sort((a, b) -> Double.compare(
                b.second, a.second
            ));
            for (int t = 0; t < Math.min(5, tokenScoreList.size()); t++) {
                Pair<Integer, Double> pair = tokenScoreList.get(t);
                System.out.println("Top " + (t + 1) + ": token=" + tokens_list.get(pair.first) + ", score=" + pair.second);
            }

            int limit = Math.min(ORACLE_TOP_K, tokenScoreList.size());
            for (int l = 0; l < limit; l++) {
                int token = tokenScoreList.get(l).first;
                double score = tokenScoreList.get(l).second;
                int[] token_indexs = corpusDomainsSet.get(token).stream().mapToInt(Integer::intValue).toArray();
                for (int token_index : token_indexs) {
                    tokens[token_index] = token_index;
                    scores[token_index] = score;
                    total_score += score;
                }
                if (PRINT_TRACE) {
                    if (score > max_score) {
                        max_score = score;
                        max_token = token_indexs[0];
                    }
                }
            }
            for (int j=0; j<tokens.length; j++) {
                double score=scores[j];
                if (score > 0) {
                    score /= total_score;
                }
                else if (score == 0) {
                    if (PRINT_TRACE) {
                        System.out.println("Score is zero: " + score);
                        System.out.println("Word: " + words.get(j));
                        System.out.println("Token: " + j);
                    }
                }
                else {
                    if (PRINT_TRACE) {
                        System.out.println("Score is negative: " + score);
                        System.out.println("Word: " + words.get(j));
                        System.out.println("Token: " + j);
                    }
                    throw new RuntimeException("Score is negative or zero");
                }
            }
            max_score /= total_score;

            if(PRINT_TRACE) System.out.println("token "+i);

            Constraint c = Factory.oracle(word_index[i], tokens, scores);

            c.setWeight(w);
            if(PRINT_TRACE)  System.out.println("oracle's weight set to "+w);
            cp.post(c);
            if(PRINT_TRACE)  System.out.println("GPT, before BP (max token, 'the word', its probability) "+max_token+", '"+words.get(max_token)+"', "+max_score);
            if(PRINT_TRACE) 
            {
                double[] temp = scores.clone();
                Arrays.sort(temp);
                for(int n=1; n<=5; n++){
                    for(int m=0; m<temp.length; m++){
                        if(temp[temp.length-n]==scores[m]){
                            System.out.println("GPT, before BP (max token, 'the word', its probability) "+m+", '"+words.get(m)+"', "+scores[m]);
                        }
                    }
                }
            }

            try {
                cp.fixPoint();
            }
            catch (InconsistencyException e) {
                if (PRINT_TRACE) {
                    System.out.println("INCONSISTENCY!");
                    for(int j=0; j<word_index.length; j++){
                        System.out.println(word_index[j].getName()+word_index[j].toString());
                    }
                }
                current_sentence += " ERROR";
                break;
            }
            if(PRINT_TRACE) 
            {
                TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                for(int j=0; j<word_index[i].size(); j++){
                    bestTokens.put(word_index[i].marginal(j), j);
                }
                for(int j=0; j<5; j++){
                    if(bestTokens.isEmpty()){
                        break;
                    }
                    double prob = bestTokens.lastKey();
                    int token = bestTokens.remove(prob);
                    System.out.println("CP model, before BP (max token, 'the word', its probability) "+token+", '"+words.get(token)+"', "+prob);
                }
            }

            if(PRINT_TRACE)  System.out.println("CP model, before BP (max token, 'the word', its probability) "+word_index[i].valueWithMaxMarginal()+", '"+words.get(word_index[i].valueWithMaxMarginal())+"', "+word_index[i].maxMarginal());
            cp.vanillaBP(NUM_PB);
            if(PRINT_TRACE)  System.out.println("after BP (max token, 'the word', its probability) "+word_index[i].valueWithMaxMarginal()+", '"+words.get(word_index[i].valueWithMaxMarginal())+"', "+word_index[i].maxMarginal());
            
            if(PRINT_TRACE) 
            {
                TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                for(int j=0; j<word_index[i].size(); j++){
                    bestTokens.put(word_index[i].marginal(j), j);
                }
                for(int j=0; j<5; j++){
                    if(bestTokens.isEmpty()){
                        break;
                    }
                    double prob = bestTokens.lastKey();
                    int token = bestTokens.remove(prob);
                    System.out.println("after BP (max token, 'the word', its probability) "+token+", '"+words.get(token)+"', "+prob);
                }
            }
            int chosen;
            try {
                if (word_index[i].maxMarginal() == 0.0) {
                            System.out.println("No valid tokens found");
                            current_sentence += " ERROR";
                            break;
                }
                chosen = word_index[i].biasedWheelValue();
            } catch (Exception e) {
                System.out.println("Inconsistency detected");
                current_sentence += " ERROR";
                break;
            }
            //word_index[i].assign(chosen);
            System.out.println("chosen: "+chosen+", '"+words.get(chosen)+"', "+word_index[i].marginal(chosen));
            try
            {
                System.out.println(tokens_list.get(corpusDomainToIndex.get(chosen)));
            }
            catch (Exception e) {
                System.out.println("not in original corpus");
            }
            
            if (0<=chosen && chosen<scores.length) {
                logSumProbs += Math.log(scores[chosen]);
            } else {
                if (PRINT_TRACE) {
                    System.out.println("index chosen: " + chosen);
                    System.out.println(scores.length);
                    System.out.println("Chose a value not in the nlp model");
                }
                logSumProbs = -Double.MAX_VALUE;
            }
            if(chosen == final_sentence_end && i<SENTENCE_MAX_NUMBER_TOKENS-1){
                System.out.println("sentence end reached");
                StateManager sm = cp.getStateManager();
                try {
                    sm.saveState();
                    word_index[i].assign(chosen);
                    word_index[i+1].assign(pad_token);
                    cp.fixPoint();
                    current_sentence += words.get(corpusDomains.get(chosen));
                    try
                    {
                        tokens_used[num_tok] = tokens_list.get(corpusDomainToIndex.get(chosen));
                    }
                    catch (NullPointerException e) {
                        tokens_used[num_tok] = words.get(corpusDomains.get(chosen))+ " (not in original corpus)";
                    }
                    break;
                } catch (InconsistencyException e) {
                    sm.restoreState();
                    System.out.println("Inconsistency detected, state restored");
                    while(chosen == final_sentence_end)
                        chosen = word_index[i].biasedWheelValue();
                }
            }
            try
            {
                current_sentence += tokens_list.get(corpusDomainToIndex.get(chosen));
                tokens_used[num_tok] = tokens_list.get(corpusDomainToIndex.get(chosen));
            }
            catch (Exception e) {
                current_sentence += words.get(corpusDomains.get(chosen));
                tokens_used[num_tok] = words.get(corpusDomains.get(chosen))+ " (not in original corpus)";
            }

            if (PRINT_TRACE) {
                System.out.println("sentence so far: " + current_sentence);
                System.out.println("index chosen: " + corpusDomains.get(chosen));
            }
            num_tok++;
        }
        double perplexityScore = Math.exp(-logSumProbs / num_tok);
        if (PRINT_TRACE) System.out.println("solution : " + current_sentence);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/tokenize"))
            .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
            .build();
        String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
        int[] split_response = Arrays.stream(response.substring(1,response.length()-2).split(",")).mapToInt(Integer::parseInt).toArray();
        int[] tokens= Arrays.copyOfRange(split_response, 1, split_response.length);

        
        logs.add(new Logging(current_sentence, perplexityScore, tokens, tokens_used.clone()));

        }
    
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "ok");
    result.put("port", port);
    result.put("num_iterations", NUM_ITERATIONS);
    result.put("num_pb", NUM_PB);
    result.put("weight", w);
    result.put("llm_name", llm_name);
    result.put("logs", logs);
    result.put("date", java.time.LocalDateTime.now().toString());
    String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
    Files.createDirectories(Paths.get(OUTPUT_DIR));
    String outputFileName = OUTPUT_DIR + "/result_SENT1_WORDS_" + System.currentTimeMillis()  + ".json";
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
    }
    catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e.getMessage());
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_SENT1_WORDS_" + System.currentTimeMillis()  + "_error.json";
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("status", "error");
            errorResult.put("error_message", e.getMessage());
            errorResult.put("exception", e.toString());
            ObjectMapper errorMapper = new ObjectMapper();
            try {
                errorMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), errorResult);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            System.exit(1);
    }
    }


    public static class Logging {

        public String sentence;
        public int[] tokens;
        public double perplexity;
        public String[] tokens_used;

        public Logging() {
        }

        public Logging(String sentence, double perplexityScore, int[] tokens, String[] tokens_used) {
            this.sentence = sentence;
            this.perplexity = perplexityScore;
            this.tokens = tokens;
            this.tokens_used = tokens_used;
        }
    }
}
    

