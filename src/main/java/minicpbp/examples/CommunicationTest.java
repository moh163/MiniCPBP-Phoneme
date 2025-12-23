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
 */

 package minicpbp.examples;

 import minicpbp.cp.Factory;
 import minicpbp.engine.core.IntVar;
 import minicpbp.engine.core.Solver;
import minicpbp.examples.Sentence_cleaned.Logging;
import minicpbp.search.DFSearch;
 import minicpbp.search.SearchStatistics;

import java.io.File;
import java.io.IOException;
 import java.net.http.HttpClient;
 import java.nio.charset.StandardCharsets;
 import java.nio.file.Files;
 import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
 import java.util.Collections;
 import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;
 import java.util.concurrent.CompletableFuture;
 import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.HttpURLConnection;
 import java.net.URI;
 import java.net.URL;
 import java.net.http.HttpClient;
 import java.net.http.HttpRequest;
 import java.net.http.HttpResponse;
 import java.net.http.HttpRequest.BodyPublisher;
 import java.net.http.HttpResponse.BodyHandlers;
 
 import static minicpbp.cp.BranchingScheme.*;
 
 
 public class CommunicationTest{
    public static void main(String[] args) throws IOException {
        /*ObjectMapper objectMapper = new ObjectMapper();
        
        //ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(new File("./src/main/java/minicpbp/examples/data/Sentence/unmatched_instructions.json"));
        ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(new File("./src/main/java/minicpbp/examples/data/Sentence/commongen_hard_nohuman.json"));
        Iterator<JsonNode> elements = arrayNode.elements();

        List<Logging> logs = new ArrayList<>();*/
        int count=0;
        while (count<1) {
        //while (elements.hasNext() && count<40) {
            count++;/* 
            JsonNode element = elements.next();
            String instruction = element.get("instruction").asText();
            instruction = instruction.replace("\n", " ");
            instruction = instruction.replace("\"", "");

            List<String> required_words_temp = new ArrayList<String>();
            Iterator<JsonNode> required_words_JsonNode= element.get("concept_set").elements();
            while (required_words_JsonNode.hasNext()) {
                String word = required_words_JsonNode.next().asText();
                required_words_temp.add(word.substring(0, word.indexOf('_')));
            }
            String[] REQUIRED_WORDS = required_words_temp.toArray(new String[0]);

            String current_sentence = "";
            Double logSumProbs = 0.0;
            int num_tok=0;*/
            /*HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5000/altpred"))
                .POST(HttpRequest.BodyPublishers.ofString("<s>Hello"))
                .build();
                String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();

                System.out.println(response);
                
                HashMap<String, Double> tokenToScoreNLP = new HashMap<>();
                String maxtoken = "";
                Double max = 0.0;*/
            for (int i = 0; i < 1; i++) {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5000/tokenize"))
                .POST(HttpRequest.BodyPublishers.ofString("0 They"))
                .build();
                String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
                System.out.println(response);
                HashMap<String, Double> tokenToScoreNLP = new HashMap<>();
                String maxtoken = "";
                Double max = 0.0;
                

            }
            //System.out.println(current_sentence);
            //double perplexityScore = Math.exp(-logSumProbs / num_tok);
            /*System.out.println("solution : " + current_sentence);
            System.out.println("Perplexity is of " + perplexityScore);*/
            //logs.add(new Logging(current_sentence, perplexityScore, REQUIRED_WORDS));

        }
        //objectMapper.writeValue(Paths.get("llm_notbarebone.json").toFile(), logs);
    }
 }
 