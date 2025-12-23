/*package minicpbp.examples;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import minicpbp.cp.Factory;
import minicpbp.engine.core.BoolVar;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.util.exception.InconsistencyException;

import static minicpbp.cp.Factory.*;

public class Orthophonie {

    public static void main(String[] args) throws Exception {
        try {
            System.out.println("20 octobre");
            System.out.println("12 décembre");
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[0]);
            final int process_id = Integer.parseInt(args[2]);

            final String llm_name = "zephyr";//

            List<Logging> logs = new ArrayList<>();

            List<String> lines = Collections.emptyList();
            try {
                lines = Files.readAllLines(
                        Paths.get("./src/main/java/minicpbp/examples/data/MNREAD/" + llm_name + "/tokenizer_dict.txt"),
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
            }

            int token_size = Integer.parseInt(lines.get(lines.size() - 1).split(":")[0]);

            String[] corrected_lines = new String[token_size];
            Arrays.fill(corrected_lines, "");
            for (int i = 0; i < lines.size(); i++) {
                String[] line = lines.get(i).split("::");
                if (line.length > 1) {
                    corrected_lines[Integer.parseInt(line[0])] = line[1];
                }
            }
            final List<String> words = Arrays.asList(corrected_lines);

            final boolean PRINT_TRACE = true;
            final int NUM_PB = 3;
            final double w = 2;
            // final int NUM_ITERATIONS = 8;
            final int size = 10; // Vous pouvez ajuster ce nombre selon vos besoins

            // // TEST COPILOT
            // // Définir les contraintes phonémiques souhaitées
            // List<PhonemeRange> phonemeRanges = new ArrayList<>();

            // // Exemple : entre 2 et 4 voyelles fermées (i, y, ɨ)
            // phonemeRanges.add(new PhonemeRange(new int[] { 1, 2, 3 }, 5, 10));

            // // Exemple : entre 1 et 3 plosives sourdes (p, t, k)
            // phonemeRanges.add(new PhonemeRange(new int[] { 29, 31, 33 }, 3, 10));
            // // FIN TEST
            for (int k = 0; k < NUM_ITERATIONS; k += 1) {

                Solver cp = makeSolver();

                PhonemeConstraints phonemeConstraints = new PhonemeConstraints(
                        "./src/main/java/minicpbp/examples/data/Phoneme/phoneme_map.json");

                IntVar[] word_index = makeIntVarArray(cp, size, 0, corpusDomains.size() - 1);
                // Contraintes phonémiques
                IntVar[] numVowels = makeIntVarArray(cp, size, 0, 15);
                // IntVar[] numConsonants = makeIntVarArray(cp, size, 0, 1000);
                // IntVar[] numStress = makeIntVarArray(cp, size, 0, 100);
                // IntVar[] totalPhonemes = makeIntVarArray(cp, size, 1, 200);
                // BoolVar[] isSpace = makeBoolVarArray(cp, size);

                // // TEST COPILOT
                // Map<Integer, IntVar[]> phonemeCounts = new HashMap<>();

                // // Pour chaque groupe de phonèmes à contraindre
                // for (PhonemeRange range : phonemeRanges) {
                //     // Créer un tableau de variables pour chaque position possible
                //     IntVar[] counts = makeIntVarArray(cp, size, 0, range.maxCount);
                //     phonemeCounts.put(Arrays.hashCode(range.phonemeIds), counts);
                // }
                // // FIN TEST

                for (int i = 0; i < size; i++) {
                    word_index[i].setName("word_index[" + i + "]");
                    int[] phonemeInfo = phonemeConstraints.getPhonemeInfo(words.get(i));
                    numVowels[i].assign(phonemeInfo[0]);
                    // numConsonants[i].assign(phonemeInfo[1]);
                    // numStress[i].assign(phonemeInfo[2]);
                    // totalPhonemes[i].assign(phonemeInfo[3]);
                    // // TEST COPILOT
                    // IntVar wordIndexVar = word_index[i];

                    // for (PhonemeRange range : phonemeRanges) {
                    //     IntVar[] countsForGroup = phonemeCounts.get(Arrays.hashCode(range.phonemeIds));

                    //     // Créer une table de correspondance pour cette contrainte
                    //     int[][] tableau = new int[corpusDomains.size()][2];

                    //     // Remplir la table avec les comptes de phonèmes pour chaque mot possible
                    //     for (int j = 0; j < corpusDomains.size(); j++) {
                    //         String word = words.get(corpusDomains.get(j));
                    //         Map<Integer, Integer> counts = phonemeConstraints.countSpecificPhonemes(word,
                    //                 range.phonemeIds);

                    //         // Calculer le total pour ce groupe de phonèmes
                    //         int total = counts.values().stream().mapToInt(Integer::intValue).sum();

                    //         tableau[j][0] = j; // Index du mot
                    //         tableau[j][1] = total; // Nombre total de phonèmes du groupe
                    //     }

                    //     // Lier le mot choisi au compte de phonèmes
                    //     cp.post(table(new IntVar[] { wordIndexVar, countsForGroup[i] }, tableau));
                    // }
                    // //FIN TEST
                }

                // Contraintes globales sur les phonèmes
                IntVar totalVowelsInText = makeIntVar(cp, 0, 500);
                // IntVar totalConsonantsInText = makeIntVar(cp, 5, 40);
                // IntVar totalStressInText = makeIntVar(cp, 0, 15);
                // IntVar totalPhonemesInText = makeIntVar(cp, 0, 80);

                cp.post(sum(numVowels, totalVowelsInText));
                // cp.post(sum(numConsonants, totalConsonantsInText));
                // cp.post(sum(numStress, totalStressInText));
                // cp.post(sum(totalPhonemes, totalPhonemesInText));

                // //TEST COPILOT
                //  // Ajouter les contraintes sur les totaux
                // for (PhonemeRange range : phonemeRanges) {
                //     IntVar[] countsForGroup = phonemeCounts.get(Arrays.hashCode(range.phonemeIds));
                    
                //     // Variable pour le total de ce groupe de phonèmes
                //     IntVar totalForGroup = makeIntVar(cp, range.minCount, range.maxCount);
                    
                //     // La somme des comptes doit être dans l'intervalle spécifié
                //     cp.post(sum(countsForGroup, totalForGroup));
                // }
                // //FIN TEST

                HttpClient client = HttpClient.newHttpClient();

                
                String current_sentence = "";
                Double logSumProbs = 0.0;
                int i =0;
                int num_tok = i;
                for (; i < size; i++) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:5000/token"))
                            .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
                            .build();
                    String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body)
                            .join();

                    int[] tokens = new int[corpusDomains.size()];
                    double[] scores = new double[corpusDomains.size()];

                    int max_token = -1;
                    double max_score = 0;
                    double total_score = 0;

                    for (String tuple : response.split("\\],\\[")) {
                        try {
                            String[] token_score = tuple.split(",");
                            token_score[1] = token_score[1].replaceAll("\\]", "");
                            token_score[0] = token_score[0].replaceAll("\\[", "");
                            int token = Integer.parseInt(token_score[0]);
                            double score = Double.parseDouble(token_score[1]);

                            if (!indexToCorpusDomain.containsKey(token)) {
                                continue;
                            }
                            int token_index = indexToCorpusDomain.get(token);
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
                    //boucle inutile enft je crois
                    for (int j = 0; j < tokens.length; j++) {
                        double score = scores[j];
                        if (score > 0) {
                            score /= total_score;
                        } else if (score == 0) {
                            if (PRINT_TRACE) {
                                System.out.println("Score is zero: " + score);
                                System.out.println("Token: " + corpusDomains.get(tokens[j]));
                            }
                        } else {
                            if (PRINT_TRACE) {
                                System.out.println("Score is negative: " + score);
                                System.out.println("Token: " + corpusDomains.get(tokens[j]));
                            }
                            throw new RuntimeException("Score is negative or zero");
                        }
                    }
                    max_score /= total_score;

                    if (PRINT_TRACE)
                        System.out.println("token " + i);

                    Constraint c = Factory.oracle(word_index[i], tokens, scores);

                    c.setWeight(w);
                    if (PRINT_TRACE)
                        System.out.println("oracle's weight set to " + w);
                    cp.post(c);
                    if (PRINT_TRACE)
                        System.out.println("GPT, before BP (max token, 'the word', its probability) " + max_token
                                + ", '" + words.get(max_token) + "', " + max_score);
                    if (PRINT_TRACE) {
                        double[] temp = scores.clone();
                        Arrays.sort(temp);
                        for (int n = 1; n <= 5; n++) {
                            for (int m = 0; m < temp.length; m++) {
                                if (temp[temp.length - n] == scores[m]) {
                                    System.out.println("GPT, before BP (max token, 'the word', its probability) " + m
                                            + ", '" + words.get(m) + "', " + scores[m]);
                                }
                            }
                        }
                    }

                    try {
                        cp.fixPoint();
                    } catch (InconsistencyException e) {
                        if (PRINT_TRACE) {
                            System.out.println("INCONSISTENCY!");
                            for (int j = 0; j < word_index.length; j++) {
                                System.out.println(word_index[j].getName() + word_index[j].toString());
                            }
                        }
                        current_sentence += " ERROR";
                        break;
                    }
                    if (PRINT_TRACE) {
                        TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                        for (int j = 0; j < word_index[i].size(); j++) {
                            bestTokens.put(word_index[i].marginal(j), j);
                        }
                        for (int j = 0; j < 5; j++) {
                            if (bestTokens.isEmpty()) {
                                break;
                            }
                            double prob = bestTokens.lastKey();
                            int token = bestTokens.remove(prob);
                            System.out.println("CP model, before BP (max token, 'the word', its probability) " + token
                                    + ", '" + words.get(token) + "', " + prob);
                        }
                    }

                    if (PRINT_TRACE)
                        System.out.println("CP model, before BP (max token, 'the word', its probability) "
                                + word_index[i].valueWithMaxMarginal() + ", '"
                                + words.get(word_index[i].valueWithMaxMarginal()) + "', "
                                + word_index[i].maxMarginal());
                    cp.vanillaBP(NUM_PB);
                    if (PRINT_TRACE)
                        System.out.println("after BP (max token, 'the word', its probability) "
                                + word_index[i].valueWithMaxMarginal() + ", '"
                                + words.get(word_index[i].valueWithMaxMarginal()) + "', "
                                + word_index[i].maxMarginal());

                    if (PRINT_TRACE) {
                        TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                        for (int j = 0; j < word_index[i].size(); j++) {
                            bestTokens.put(word_index[i].marginal(j), j);
                        }
                        for (int j = 0; j < 5; j++) {
                            if (bestTokens.isEmpty()) {
                                break;
                            }
                            double prob = bestTokens.lastKey();
                            int token = bestTokens.remove(prob);
                            System.out.println("after BP (max token, 'the word', its probability) " + token + ", '"
                                    + words.get(token) + "', " + prob);
                        }
                    }

                    int chosen = word_index[i].biasedWheelValue();
                    word_index[i].assign(chosen);
                    num_tok++;
                    if (0 <= chosen && chosen < scores.length) {
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
                if (PRINT_TRACE)
                    System.out.println("solution : " + current_sentence);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5000/tokenize"))
                        .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
                        .build();
                String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body)
                        .join();
                int[] split_response = Arrays.stream(response.substring(1, response.length() - 2).split(","))
                        .mapToInt(Integer::parseInt).toArray();
                int[] tokens = Arrays.copyOfRange(split_response, 1, split_response.length);

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
            String outputFileName = OUTPUT_DIR + "/result_" + process_id + ".json";
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e.getMessage());
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_" + (args.length > 2 ? args[2] : "error") + ".json";
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("status", "error");
            errorResult.put("error_message", e.getMessage());
            errorResult.put("exception", e.toString());
            ObjectMapper errorMapper = new ObjectMapper();
            try {
                errorMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(),
                        errorResult);
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

    public static class PhonemeRange {
        public int[] phonemeIds;
        public int minCount;
        public int maxCount;

        public PhonemeRange(int[] phonemeIds, int minCount, int maxCount) {
            this.phonemeIds = phonemeIds;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }
    }

    public static class PhonemeConstraints {
        private JsonNode phonemeMap;
        private Map<String, Integer> phonemeToId;
        private Map<Integer, String> idToCategory;

        public PhonemeConstraints(String phonemeMapPath) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            phonemeMap = mapper.readTree(new File(phonemeMapPath));
            phonemeToId = new HashMap<>();
            idToCategory = new HashMap<>();

            // Parse all phonemes and create maps
            parsePhonemeCategory("vowels", phonemeMap.get("vowels"));
            parsePhonemeCategory("consonants", phonemeMap.get("consonants"));
            parsePhonemeCategory("suprasegmentals", phonemeMap.get("suprasegmentals"));
            parseDiacritics(phonemeMap.get("diacritics"));
        }

        private void parsePhonemeCategory(String category, JsonNode node) {
            node.fields().forEachRemaining(subcategory -> {
                subcategory.getValue().fields().forEachRemaining(phoneme -> {
                    String symbol = phoneme.getKey();
                    int id = phoneme.getValue().get("id").asInt();
                    phonemeToId.put(symbol, id);
                    idToCategory.put(id, category + "." + subcategory.getKey());
                });
            });
        }

        private void parseDiacritics(JsonNode diacritics) {
            diacritics.fields().forEachRemaining(diacritic -> {
                String symbol = diacritic.getValue().get("symbol").asText();
                int id = diacritic.getValue().get("id").asInt();
                phonemeToId.put(symbol, id);
                idToCategory.put(id, "diacritics");
            });
        }

        public int[] getPhonemeInfo(String word) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5000/phonemize"))
                        .POST(HttpRequest.BodyPublishers.ofString(word))
                        .build();

                String response = client.sendAsync(request, BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .join();

                JsonNode root = new ObjectMapper().readTree(response);
                String phonemes = root.get("phonemes").asText();
                String[] phonemeArray = phonemes.split(" ");

                int[] info = new int[4]; // [numVowels, numConsonants, numStress, totalPhonemes]

                for (String phoneme : phonemeArray) {
                    Integer id = phonemeToId.get(phoneme);
                    if (id != null) {
                        String category = idToCategory.get(id);
                        if (category.startsWith("vowels")) {
                            info[0]++;
                        } else if (category.startsWith("consonants")) {
                            info[1]++;
                        } else if (category.startsWith("suprasegmentals.stress")) {
                            info[2]++;
                        }
                        info[3]++; // Total phonemes
                    }
                }

                return info;
            } catch (Exception e) {
                e.printStackTrace();
                return new int[] { 0, 0, 0, 0 };
            }
        }

        public Map<Integer, Integer> countSpecificPhonemes(String word, int[] phonemsIds) {
            Map<Integer, Integer> counts = new HashMap<>();
            // Initialiser le compteur pour chaque ID demandé à 0
            for (int id : phonemsIds) {
                counts.put(id, 0);
            }

            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5000/phonemize"))
                        .POST(HttpRequest.BodyPublishers.ofString(word))
                        .build();

                String response = client.sendAsync(request, BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .join();

                JsonNode root = new ObjectMapper().readTree(response);
                String phonemes = root.get("phonemes").asText();

                // Diviser la chaîne de phonèmes en symboles individuels
                String[] phonemeArray = phonemes.split(" ");

                // Compter les occurrences des phonèmes spécifiés
                for (String phoneme : phonemeArray) {
                    Integer id = phonemeToId.get(phoneme);
                    if (id != null) {
                        for (int searchId : phonemsIds) {
                            if (id == searchId) {
                                counts.put(id, counts.get(id) + 1);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return counts;
        }
    }

}
*/