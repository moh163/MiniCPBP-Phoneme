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

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class CollieSent1 {
    public static void main(String[] args) throws Exception {
        
            System.out.println("6 Aout");
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[3]);
            final double weight = Double.parseDouble(args[0]);
        try {
        final String llm_name="zephyr";//

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
        final List<String> words = Arrays.asList(corrected_lines);
        
        List<Integer> corpusDomains = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/"+llm_name+"/corpus_domain.json")), StandardCharsets.UTF_8);
            final List<Integer> parsedCorpusDomains = objectMapper.readValue(jsonContent, new TypeReference<List<Integer>>() {}); 
            corpusDomains.addAll(parsedCorpusDomains);
            corpusDomains.remove(Integer.valueOf(8));
            corpusDomains.add(Integer.valueOf(50276));
        } catch (Exception e) {
            e.printStackTrace();
        }

        int sentence_end=15;
        /*for(int i=0; i<words.size(); i++){
            if(words.get(i).equals(".")){
                sentence_end=i;
            }
        }*/
        corpusDomains.add(sentence_end);

        Map<Integer, Integer> indexToCorpusDomain = new HashMap<>();
        for (int i = 0; i < corpusDomains.size(); i++) {
            indexToCorpusDomain.put(corpusDomains.get(i), i);
        }

        System.out.println("corpusDomains size: " + corpusDomains.size());

        sentence_end = indexToCorpusDomain.get(sentence_end);
        final int final_sentence_end = sentence_end;



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

 


        final int MAX_NUMBER_SPACE = 5;
        final int MIN_NUMBER_WORD = 9;
        final int MAX_NUMBER_WORD = 15;
        final int NUMBER_CHAR = 82;//Verify if you need to count the spaces at the beginning of lines
        final boolean PRINT_TRACE = false;
        final int NUM_PB = 3;
        final double w = weight;
        final int SENTENCE_MAX_NUMBER_TOKENS = 30;
        //final int NUM_ITERATIONS = 8;

        for (int k = 0; k < NUM_ITERATIONS; k += 1) {
        
        Solver cp = makeSolver();
        IntVar[] word_index = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, corpusDomains.size()-1);
        //IntVar[] has_space = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, 0, 1);
        IntVar[] num_char = makeIntVarArray(cp, SENTENCE_MAX_NUMBER_TOKENS, Arrays.stream(charNum).min().getAsInt(), Arrays.stream(charNum).max().getAsInt());

        for (int i=0; i<SENTENCE_MAX_NUMBER_TOKENS; i++){
            word_index[i].setName("word_index["+i+"]");
            //cp.post(element(start_words, word_index[i], has_space[i]));
            cp.post(element(charNum, word_index[i], num_char[i]));
        }

        //IntVar nb_words = makeIntVar(cp,MIN_NUMBER_WORD,MAX_NUMBER_WORD);
        //IntVar nb_char = makeIntVar(cp, (int)Math.round(NUMBER_CHAR - 0.05 * NUMBER_CHAR), (int)Math.round(NUMBER_CHAR + 0.05 * NUMBER_CHAR));
        
        //cp.post(sum(has_space, nb_words));
        cp.post(sum(num_char, NUMBER_CHAR));
        //cp.post(sum(num_char, nb_char));




        List<Integer> acceptedState = new ArrayList<>();
        int[][] A = new int[2][corpusDomains.size()];
        acceptedState.add(1);
        Arrays.fill(A[0], 0);
        A[0][final_sentence_end]=1;
        Arrays.fill(A[1], -1);
        A[1][final_sentence_end]=1;
        cp.post(Factory.regular(word_index, A, 0, acceptedState));


        //Words regular
        //cp.post(Factory.regular(word_index, table, 0,List.of(1) ));

        HttpClient client = HttpClient.newHttpClient();

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

        HttpRequest request1 = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/tokenize"))
            .POST(HttpRequest.BodyPublishers.ofString("0" + selectedWord))
            .build();
        String response1 = client.sendAsync(request1, BodyHandlers.ofString()).thenApply((HttpResponse<String> r) -> r.body()).join();
        int[] split_response1 = Arrays.stream(response1.substring(1,response1.length()-2).split(",")).mapToInt(Integer::parseInt).toArray();
        int id = split_response1[0];
        int[] tokens1= Arrays.copyOfRange(split_response1, 1, split_response1.length);
        if (id != -1 && id != -3)
            throw new RuntimeException("Error in tokenization: " + response1);
        int i = 0;
        for (; i < tokens1.length; i++) {
            if (!indexToCorpusDomain.containsKey(tokens1[i])) {
                if (PRINT_TRACE) {
                    System.out.println("Token not in corpusDomains: " + tokens1[i]);
                    System.out.println(selectedWord);
                    System.out.println(Arrays.toString(tokens1));
                }
                throw new RuntimeException("Token not in corpusDomains");
            }
            word_index[i].assign(indexToCorpusDomain.get(tokens1[i]));
        }

        String instruction = "Please generate a sentence with exactly 82 characters. Include whitespace into your character count.";
        String current_sentence = selectedWord;
        Double logSumProbs = 0.0;
        int num_tok=i;
        for (; i < SENTENCE_MAX_NUMBER_TOKENS; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/token"))
                .POST(HttpRequest.BodyPublishers.ofString(instruction + current_sentence))
                .build();
            String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

            int[] tokens = new int[corpusDomains.size()];
            double[] scores = new double[corpusDomains.size()];

            int max_token = -1;
            double max_score = 0;
            double total_score = 0;

            for (String tuple : response.split("\\],\\[")) {
                try {
                    String[] token_score = tuple.split(",");
                    token_score[1]=token_score[1].replaceAll("\\]","");
                    token_score[0]=token_score[0].replaceAll("\\[","");
                    int token = Integer.parseInt(token_score[0]);
                    double score = Double.parseDouble(token_score[1]);
                    
                    if (!indexToCorpusDomain.containsKey(token)) {
                        continue;
                    }
                    int token_index=indexToCorpusDomain.get(token);
                    tokens[token_index] = token_index;
                    scores[token_index] = score;
                    if (score < 0) {
                        if (PRINT_TRACE) {
                            System.out.println("Score is negative: " + score);
                            System.out.println("Token: " + token);
                        }
                        continue;
                    }
                    total_score += score;

                    if (score > max_score) {
                        max_score = score;
                        max_token = token_index;
                    }

                } catch (Exception e) {
                    if (PRINT_TRACE) {
                        System.err.println(tuple);
                        System.err.println(e);
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
                        System.out.println("Token: " + corpusDomains.get(tokens[j]));
                    }
                }
                else {
                    if (PRINT_TRACE) {
                        System.out.println("Score is negative: " + score);
                        System.out.println("Token: " + corpusDomains.get(tokens[j]));
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

            int chosen = word_index[i].biasedWheelValue();
            word_index[i].assign(chosen);
            num_tok++;
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
            current_sentence += words.get(corpusDomains.get(chosen));
            
            if (PRINT_TRACE) {
                System.out.println("sentence so far: " + current_sentence);
                System.out.println("index chosen: " + corpusDomains.get(chosen));
            }
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

        
        logs.add(new Logging(current_sentence, perplexityScore, tokens));

        }
    
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "ok");
    result.put("port", port);
    result.put("num_iterations", NUM_ITERATIONS);
    result.put("num_pb", NUM_PB);
    result.put("weight", w);
    result.put("llm_name", llm_name);
    result.put("logs", logs);
    String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
    Files.createDirectories(Paths.get(OUTPUT_DIR));
    String outputFileName = OUTPUT_DIR + "/result_" + weight + ".json";
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
    }
    catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e.getMessage());
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_" + weight + "_error.json";
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

        public Logging() {
        }

        public Logging(String sentence, double perplexityScore, int[] tokens) {
            this.sentence = sentence;
            this.perplexity = perplexityScore;
            this.tokens = tokens;
        }
    }
}
    

