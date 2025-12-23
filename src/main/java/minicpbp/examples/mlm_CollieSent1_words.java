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
import java.util.Iterator;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.icu.impl.Pair;

public class mlm_CollieSent1_words {
    final static String mask_string = "<mask>";

    private static String sentenceBuilder(List<String> bases) {
        String base = bases.get(new Random().nextInt(bases.size()));
        System.out.println("Base sentence: " + base);
        String[] words = base.split(" ");
        Random rand = new Random();
        int numMasks = rand.nextBoolean() ? 2 : 3;
        Set<Integer> maskIndices = new HashSet<>();
        while (maskIndices.size() < numMasks) {
            int idx = rand.nextInt(words.length);
            maskIndices.add(idx);
        }
        System.out.println("Masking indices: " + maskIndices);
        for (int idx : maskIndices) {
            words[idx] = mask_string;
        }
        return String.join(" ", words);
    }

    public static void main(String[] args) throws Exception {
        
            System.out.println("2 septembre");
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[3]);
            final double weight = Double.parseDouble(args[0]);
            final int seed = Integer.parseInt(args[4]);
        try {

        // Read initial base sentence from file
        ArrayList<String> base_sentence = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./output/Septembre_2025/result_SENT1_WORDS_1756741626069.json")), StandardCharsets.UTF_8);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            ArrayNode logsArray = (ArrayNode) rootNode.get("logs");
            if (logsArray != null && logsArray.size() > seed) {
                String sentence = logsArray.get(seed).get("sentence").asText().strip();
                if (sentence.endsWith(".")) {
                    sentence = sentence.substring(0, sentence.length() - 1);
                }
                base_sentence.add(sentence);
            } else {
                base_sentence.add("An sentence about a cat playing with a ball of string could be something special");
            }
        } catch (Exception e) {
            e.printStackTrace();
            base_sentence.add("An sentence about a cat playing with a ball of string could be something special");
            System.err.println("Could not read base sentence file, using default.");
        }



        final String llm_name="roberta";//

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
        objectMapper = new ObjectMapper();
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/corpus_tokenized_words.json")), StandardCharsets.UTF_8);
            final List<List<Integer>> parsedCorpusDomains = objectMapper.readValue(jsonContent, new TypeReference<List<List<Integer>>>() {}); 
            int j = 0;
            for (int i = 0; i < parsedCorpusDomains.size(); i++) {
                List<Integer> sublist = parsedCorpusDomains.get(i);
                if(sublist.size() != 1) continue;
                String word_string = sublist.stream().map(n -> tokens_list.get(n)).collect(Collectors.joining("")).strip();
                if (words.contains(word_string)) {
                    continue;
                }
                words.add(word_string.replace("##", ""));
                if (!corpusDomainsSet.containsKey(sublist.get(0)))
                    corpusDomainsSet.put(sublist.get(0), new ArrayList<>(List.of(j)));
                else
                    corpusDomainsSet.get(sublist.get(0)).add(j);
                corpusDomainToIndex.put(j, sublist.get(0));
                j++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (String w : base_sentence.get(0).split(" ")) {
            if (!words.contains(w)) {
                words.add(w);
            }
        }

        List<Integer> corpusDomains = new ArrayList<>();
        for (int idx = 0; idx < words.size(); idx++) {
            corpusDomains.add(idx);
        }


        System.out.println("corpusDomains size: " + corpusDomains.size());


        int[] start_words  = new int[corpusDomains.size()];
        for(int i=0; i<corpusDomains.size(); i++){
            if(words.get(corpusDomains.get(i)).strip().length()!=0 && words.get(corpusDomains.get(i)).charAt(0)==' '){
                start_words[i]=1;
            }
        }

        int[] charNum = new int[corpusDomains.size()];
        for (int i = 0; i < corpusDomains.size(); i++) {
            int domainIndex = corpusDomains.get(i);
            String word = words.get(domainIndex);
            if(i==corpusDomains.size()-1){
                charNum[i]=0;
                continue;
            }
            charNum[i]=word.length();
            
        }


        
        
        
        final boolean PRINT_TRACE = false;
        final int NUM_PB = 3;
        final double w = weight;
        final int SENTENCE_MAX_NUMBER_TOKENS = base_sentence.get(0).split(" ").length;
        final int ORACLE_TOP_K = 100;
        final int NUMBER_CHAR = 82 - 1 - SENTENCE_MAX_NUMBER_TOKENS;//Le point et les espaces enlevés
        //final int NUM_ITERATIONS = 8;

        String[] tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];

        
        Solver cp = makeSolver();
        IntVar[] word_index = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, corpusDomains.size()-1);
        IntVar[] num_char = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, Arrays.stream(charNum).min().getAsInt(), Arrays.stream(charNum).max().getAsInt());

        for (int i=0; i<SENTENCE_MAX_NUMBER_TOKENS; i++){
            word_index[i].setName("word_index["+i+"]");
            cp.post(element(charNum, word_index[i], num_char[i]));
        }

        //IntVar nb_char = makeIntVar(cp, (int)Math.round(NUMBER_CHAR - 0.05 * NUMBER_CHAR), (int)Math.round(NUMBER_CHAR + 0.05 * NUMBER_CHAR));
        
        cp.post(sum(num_char, NUMBER_CHAR));
        //cp.post(sum(num_char, nb_char));




        HttpClient client = HttpClient.newHttpClient();
        Random rand = new Random();

        Double logSumProbs = 0.0;
        int num_tok=1;


        StateManager sm = cp.getStateManager();
        sm.saveState();

        int l = -1;
        while (l < NUM_ITERATIONS-1 || (base_sentence.size() < 5 && l < 3*NUM_ITERATIONS)) {
            l++;
            System.out.println("Iteration: " + l);
            sm.restoreState();
            sm.saveState();

            String current_sentence = sentenceBuilder(base_sentence);
            String original_sentence = current_sentence;
            
            System.out.println("Current sentence: " + current_sentence);

            String[] sentenceWords = current_sentence.split(" ");
            List<Integer> masked_indexs = new ArrayList<>();
            for (int idx = 0; idx < sentenceWords.length; idx++) {
                if (!sentenceWords[idx].equals(mask_string)) {
                    try {
                        word_index[idx].assign(words.indexOf(sentenceWords[idx]));
                    } catch (Exception e) {
                        System.out.println(e);
                        System.err.println("Error assigning index " + idx + " to word " + sentenceWords[idx]);
                        System.err.println(words.contains(sentenceWords[idx]));
                    }
                } else {
                    masked_indexs.add(idx);
                }
            }
            int i = -1;
            while (current_sentence.contains(mask_string)) {
                i++;
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/mlm"))
                    .POST(HttpRequest.BodyPublishers.ofString("<s>"+current_sentence+"."))
                    .build();
                String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

                System.out.println("Response: Received");

                JsonNode jsonNode = objectMapper.readTree(response);
                ObjectNode  maskedTokens = (ObjectNode) jsonNode;
                System.out.println("Masked tokens found: " + maskedTokens.size());

                for (Iterator<String> it = maskedTokens.fieldNames(); it.hasNext(); ) {
                    String fieldName = it.next();
                    JsonNode tok = maskedTokens.get(fieldName);
                    System.out.println("Masked token: " + tok.get("mask_word_position").asInt());

                    int z = tok.get("mask_word_position").asInt();
                    ArrayNode probsNode = (ArrayNode) tok.get("probs");
                    ArrayNode tokensNode = (ArrayNode) tok.get("tokens");
                    List<Pair<Integer, Double>> tokenScoreList = new ArrayList<>();
                    for (int idx = 0; idx < probsNode.size(); idx++) {
                        try {
                        double prob = probsNode.get(idx).asDouble();
                        int token = tokensNode.get(idx).asInt();
                        if (!corpusDomainsSet.containsKey(token)) continue;
                        if (prob < 0) continue;

                        Pair<Integer, Double> tuple = Pair.of(token, prob);
                        tokenScoreList.add(tuple);
                        } catch (Exception e) {
                            if (PRINT_TRACE) {
                                System.err.println(idx);
                                System.err.println(e);
                            }
                        }
                    }

                    int[] tokens = new int[corpusDomains.size()];
                    double[] scores = new double[corpusDomains.size()];

                    int max_token = -1;
                    double max_score = 0;
                    double total_score = 0;
    

                    tokenScoreList.sort((a, b) -> Double.compare(
                        b.second, a.second
                    ));


                    int limit = Math.min(ORACLE_TOP_K, tokenScoreList.size());
                    for (int k = 0; k < limit; k++) {
                        int token = tokenScoreList.get(k).first;
                        double score = tokenScoreList.get(k).second;
                        int[] token_indexes = corpusDomainsSet.get(token).stream().mapToInt(Integer::intValue).toArray();
                        for (int token_index : token_indexes) {
                            tokens[token_index] = token_index;
                            scores[token_index] = score;
                            total_score += score;
                        }
                        if (PRINT_TRACE) {
                            if (score > max_score) {
                                max_score = score;
                                max_token = token_indexes[0];
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

                if(PRINT_TRACE) System.out.println("token "+z);

                Constraint c = Factory.oracle(word_index[z], tokens, scores);

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
                }   
                List<Integer> masked_indexs_copy = new ArrayList<>(masked_indexs);
                
                    System.out.println("Processing masked index: " + i);
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
                        current_sentence = String.join(" ", sentenceWords);
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
                    try{cp.vanillaBP(NUM_PB);}
                    catch (Exception e) {
                        if (PRINT_TRACE) {
                            System.out.println("INCONSISTENCY during BP!");
                            for(int j=0; j<word_index.length; j++){
                                System.out.println(word_index[j].getName()+word_index[j].toString());
                            }
                        }
                        current_sentence = String.join(" ", sentenceWords);
                        current_sentence += " ERROR";
                        break;
                    }
                    if(PRINT_TRACE)  System.out.println("after BP (max token, 'the word', its probability) "+word_index[i].valueWithMaxMarginal()+", '"+words.get(word_index[i].valueWithMaxMarginal())+"', "+word_index[i].maxMarginal());
                    System.out.println("BP completed for index: " + i);
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
                    int index = masked_indexs_copy.remove(rand.nextInt(masked_indexs_copy.size()));
                    try {
                        if (word_index[index].maxMarginal() == 0.0) {
                            System.out.println("No valid tokens found");
                            current_sentence = String.join(" ", sentenceWords);
                            current_sentence += " ERROR";
                            break;
                        }
                        chosen = word_index[index].biasedWheelValue();
                    } catch (Exception e) {
                        System.out.println("Inconsistency detected");
                        break;
                    }
                    System.out.println("Chosen index: " + index +", chosen: " + chosen +", chosen word: " + words.get(corpusDomains.get(chosen)) + ", probability: " + word_index[index].marginal(chosen));
                    word_index[index].assign(chosen);
                    num_tok++;
                
                    
                    sentenceWords[index] = words.get(corpusDomains.get(chosen)).strip();
                    System.out.println("Assigning index " + index + " to word " + sentenceWords[index]);
                    System.out.println(words.contains(sentenceWords[index]));
                    current_sentence = String.join(" ", sentenceWords);
                    try
                    {
                        tokens_used[index] = tokens_list.get(corpusDomainToIndex.get(chosen));
                    }
                    catch (Exception e)
                    {
                        tokens_used[index] = "ORIGINAL WORD";
                    }

                    if (PRINT_TRACE) {
                        System.out.println("sentence so far: " + current_sentence);
                        System.out.println("index chosen: " + corpusDomains.get(chosen));
                    }
                    
            }
            double perplexityScore = Math.exp(-logSumProbs / num_tok);
            //if (PRINT_TRACE) 
            // Capitalize first word if not already capitalized
            if (!current_sentence.isEmpty() && Character.isLowerCase(current_sentence.charAt(0))) {
                current_sentence = Character.toUpperCase(current_sentence.charAt(0)) + current_sentence.substring(1);
            }
            if (base_sentence.contains(current_sentence)) {
                System.out.println("Duplicate sentence, skipping: " + current_sentence);
                continue;
            }
            current_sentence = current_sentence.endsWith("ERROR") ? current_sentence : current_sentence + ".";
            System.out.println("solution : " + current_sentence);

            HttpRequest request2 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mlm_tokenize"))
                .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
                .build();
            String response2 = client.sendAsync(request2, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
            JsonNode jsonNode2 = objectMapper.readTree(response2);
            ArrayNode tokensArray = (ArrayNode) jsonNode2.get("token_ids");
            int[] tokens = new int[tokensArray.size()];
            for (int j = 0; j < tokensArray.size(); j++) {
                tokens[j] = tokensArray.get(j).asInt();
            }
            logs.add(new Logging(current_sentence, original_sentence, perplexityScore, tokens, tokens_used.clone()));

            String[] wordsArr = current_sentence.split(" ");
            /*if (wordsArr.length > 0 && wordsArr[wordsArr.length - 1].equals("ERROR")) {
                current_sentence = String.join(" ", Arrays.copyOf(wordsArr, wordsArr.length - 1));
            }*/
            if(!current_sentence.contains("ERROR")){
                if (current_sentence.endsWith(".")) {
                    current_sentence = current_sentence.substring(0, current_sentence.length() - 1);
                }
                base_sentence.add(current_sentence);
            }
            }
  
    
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "ok");
    result.put("port", port);
    result.put("num_iterations", NUM_ITERATIONS);
    result.put("num_pb", NUM_PB);
    result.put("weight", w);
    result.put("llm_name", llm_name);
    result.put("base_sentence", base_sentence.get(0));
    result.put("logs", logs);
    result.put("date", java.time.LocalDateTime.now().toString());
    String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
    Files.createDirectories(Paths.get(OUTPUT_DIR));
    String outputFileName = OUTPUT_DIR + "/result_MLM_SENT1_WORDS_" + System.currentTimeMillis()  + ".json";
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
    }
    catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e.getMessage());
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_MLM_SENT1_WORDS_" + System.currentTimeMillis()  + "_error.json";
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
        public String original_sentence;

        public Logging() {
        }

        public Logging(String sentence, String original_sentence, double perplexityScore, int[] tokens, String[] tokens_used) {
            this.sentence = sentence;
            this.original_sentence = original_sentence;
            this.perplexity = perplexityScore;
            this.tokens = tokens;
            this.tokens_used = tokens_used;
        }
    }
}
    

