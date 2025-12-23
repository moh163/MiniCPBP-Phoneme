package minicpbp.examples;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import minicpbp.cp.Factory;
import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.util.exception.InconsistencyException;

import static minicpbp.cp.Factory.*;

public class OrthophonieTopk {
    public static final boolean PRINT_TRACE = true;

    public static void main(String[] args) throws Exception {
        try {
            System.out.println("12 décembre - Phonemize top-k only");
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[0]);
            final int process_id = Integer.parseInt(args[2]);
            final String llm_name = "zephyr";

            List<Logging> logs = new ArrayList<>();
            ObjectMapper objectMapper = new ObjectMapper();

            // ===== Charger tokenizer_dict.txt =====
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

            // ===== Charger corpus_domain.json =====
            List<Integer> corpusDomains = new ArrayList<>();
            Map<Integer, Integer> indexToCorpusDomain = new HashMap<>();
            try {
                String jsonContent = new String(
                        Files.readAllBytes(Paths.get(
                                "./src/main/java/minicpbp/examples/data/MNREAD/" + llm_name + "/corpus_domain.json")),
                        StandardCharsets.UTF_8);
                final List<Integer> parsedCorpusDomains = objectMapper.readValue(jsonContent,
                        new TypeReference<List<Integer>>() {
                        });
                corpusDomains.addAll(parsedCorpusDomains);
            } catch (Exception e) {
                e.printStackTrace();
                for (int i = 0; i < words.size(); i++) {
                    corpusDomains.add(i);
                }
            }

            for (int i = 0; i < corpusDomains.size(); i++) {
                indexToCorpusDomain.put(corpusDomains.get(i), i);
            }

            System.out.println("Vocab size: " + corpusDomains.size());

            // ===== Initialiser PhonemeConstraints =====
            PhonemeConstraints phonemeConstraints = new PhonemeConstraints(
                    "./src/main/java/minicpbp/examples/data/Phoneme/phoneme_map.json");

            // ===== Définir contraintes phonémiques (cibles globales) =====
            List<PhonemeRange> phonemeRanges = new ArrayList<>();
            // Exemple : entre 10 et 30 voyelles au total dans la phrase
            // int[] vowelIds = IntStream.rangeClosed(1, 28).toArray();
            // phonemeRanges.add(new PhonemeRange(vowelIds, 10, 30));
            // // Exemple : entre 15 et 40 consonnes
            // int[] consonantIds = IntStream.rangeClosed(29, 55).toArray();
            // phonemeRanges.add(new PhonemeRange(consonantIds, 15, 40));
            // Exemple : Je veux un focus sur les lettre p et b
            int[] p_Ids = new int[] { 29 }; // IDs pour 'p'
            // int[] b_Ids = new int[]{30}; // IDs pour 'b'
            phonemeRanges.add(new PhonemeRange(p_Ids, 4, 2000));
            // phonemeRanges.add(new PhonemeRange(b_Ids, 3, 2000));

            final int NUM_PB = 3;
            final double w = 2;
            final int size = 15;
            final int TOP_K = 40; // Seulement phonemiser top-k tokens

            for (int k = 0; k < NUM_ITERATIONS; k++) {
                Solver cp = makeSolver();

                // Créer variables pour chaque position
                IntVar[] word_index = makeIntVarArray(cp, size, 0, corpusDomains.size() - 1);

                // ===== CRÉER VARIABLES PHONÉMIQUES GLOBALES =====
                IntVar[][] phonemeCountsPerGroupPerPos = new IntVar[phonemeRanges.size()][size];
                IntVar[] totalPhonemePerGroup = new IntVar[phonemeRanges.size()];
                boolean first = true;
                for (int g = 0; g < phonemeRanges.size(); g++) {
                    PhonemeRange range = phonemeRanges.get(g);
                    totalPhonemePerGroup[g] = makeIntVar(cp, range.minCount, range.maxCount);

                    for (int pos = 0; pos < size; pos++) {
                        phonemeCountsPerGroupPerPos[g][pos] = makeIntVar(cp, 0, 1000);
                    }

                    // Contrainte globale : la somme de tous les phonèmes du groupe = total voulu
                    cp.post(sum(phonemeCountsPerGroupPerPos[g], totalPhonemePerGroup[g]));
                }

                HttpClient client = HttpClient.newHttpClient();

                // Choisir mot de départ
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
                        "There" };
                String selectedWord = " " + commonWords[new Random().nextInt(commonWords.length)];

                HttpRequest request1 = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/tokenize"))
                        .POST(HttpRequest.BodyPublishers.ofString("0" + selectedWord))
                        .build();
                String response1 = client.sendAsync(request1, BodyHandlers.ofString())
                        .thenApply(HttpResponse::body).join();
                int[] split_response1 = Arrays.stream(response1.substring(1, response1.length() - 2).split(","))
                        .mapToInt(Integer::parseInt).toArray();
                int[] tokens1 = Arrays.copyOfRange(split_response1, 1, split_response1.length);

                int i = 0;
                for (; i < tokens1.length && i < size; i++) {
                    if (indexToCorpusDomain.containsKey(tokens1[i])) {
                        word_index[i].assign(indexToCorpusDomain.get(tokens1[i]));
                    }
                }

                String current_sentence = selectedWord;
                Double logSumProbs = 0.0;
                int num_tok = i;

                // ===== Boucle génération =====
                for (; i < size; i++) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:5000/token"))
                            .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
                            .build();
                    String response = client.sendAsync(request, BodyHandlers.ofString())
                            .thenApply(HttpResponse::body).join();

                    int[] tokens = new int[corpusDomains.size()];
                    double[] scores = new double[corpusDomains.size()];

                    int max_token = -1;
                    double max_score = 0;
                    double total_score = 0;

                    // ===== Extraire top-k et leurs scores =====
                    List<TokenScorePair> allTokens = new ArrayList<>();

                    JsonNode rootNode = objectMapper.readTree(response);

                    JsonNode probNode = rootNode.get("prob");

                    if (probNode != null && probNode.isArray()) {
                        for (JsonNode tuple : probNode) {
                            try {
                                // Le format est [token_id, score]
                                int token = tuple.get(0).asInt();
                                double score = tuple.get(1).asDouble();

                                if (!indexToCorpusDomain.containsKey(token)) {
                                    continue;
                                }
                                int token_index = indexToCorpusDomain.get(token);
                                tokens[token_index] = token_index;
                                scores[token_index] = score;

                                total_score += score;

                                if (score > max_score) {
                                    max_score = score;
                                    max_token = token_index;
                                }

                                allTokens.add(new TokenScorePair(token, token_index, score));

                            } catch (Exception e) {
                                if (PRINT_TRACE)
                                    System.err.println("Erreur parsing tuple: " + e.getMessage());
                            }
                        }
                    }

                    // ===== TRIER PAR SCORE DÉCROISSANT =====
                    allTokens.sort((a, b) -> Double.compare(b.score, a.score));

                    // Limiter à top-K et normaliser
                    int k_actual = Math.min(TOP_K, allTokens.size());
                    List<int[]> topKPairs = new ArrayList<>();
                    for (int idx = 0; idx < k_actual; idx++) {
                        TokenScorePair pair = allTokens.get(idx);
                        topKPairs.add(new int[] { pair.vocabId, pair.corpusIdx });
                    }

                    if (total_score > 0) {
                        for (int j = 0; j < scores.length; j++) {
                            if (scores[j] > 0)
                                scores[j] /= total_score;
                        }
                    }
                    max_score /= total_score;
                    // ===== Phonémiser seulement top-k tokens =====
                    Map<Integer, Map<Integer, Integer>> topKPhonemeInfo = new HashMap<>();
                    if (!topKPairs.isEmpty()) {
                        int[] topKVocabIds = new int[k_actual];
                        for (int idx = 0; idx < k_actual; idx++) {
                            topKVocabIds[idx] = topKPairs.get(idx)[0];
                        }
                        topKPhonemeInfo = phonemeConstraints.getPhonemesForTopK(topKVocabIds);
                    }

                    if (PRINT_TRACE) {
                        System.out.println("Position " + i + ", top-k phoneme counts:");
                        for (Map.Entry<Integer, Map<Integer, Integer>> e : topKPhonemeInfo.entrySet()) {
                            System.out.println("  TokenID: " + e.getKey() + " Token: "+words.get(e.getKey()) +" Phoneme: "+ e.getValue());
                        }
                    }

                    // ===== LIER LES PHONÈMES DU TOP-K AUX VARIABLES CP =====
                    for (int g = 0; g < phonemeRanges.size(); g++) {
                        PhonemeRange range = phonemeRanges.get(g);

                        // Créer un tableau : corpus_index -> count phonèmes du groupe pour cette
                        // position
                        // Initialiser avec des 0 pour tous
                        int[] phonemeCountsForThisGroup = new int[corpusDomains.size()];
                        Arrays.fill(phonemeCountsForThisGroup, 0);

                        // ===== REMPLIR SEULEMENT POUR LES TOP-K =====
                        for (int idx = 0; idx < topKPairs.size(); idx++) {
                            int vocabId = topKPairs.get(idx)[0];
                            int corpusIdx = topKPairs.get(idx)[1];

                            if (topKPhonemeInfo.containsKey(vocabId)) {
                                Map<Integer, Integer> phonemeCounts = topKPhonemeInfo.get(vocabId);
                                int groupCount = 0;
                                for (int phonemeId : range.phonemeIds) {
                                    groupCount += phonemeCounts.getOrDefault(phonemeId, 0);
                                }
                                phonemeCountsForThisGroup[corpusIdx] = groupCount;
                            }
                        }

                        // Lier : word_index[i] -> phonemeCountsPerGroupPerPos[g][i]
                        cp.post(element(phonemeCountsForThisGroup, word_index[i], phonemeCountsPerGroupPerPos[g][i]));
                    }
                    // ===== RESTREINDRE word_index[i] AU TOP-K =====
                    int[] topKCorpusIndices = new int[topKPairs.size()];
                    for (int idx = 0; idx < topKPairs.size(); idx++) {
                        topKCorpusIndices[idx] = topKPairs.get(idx)[1];
                    }

                    // Restreindre explicitement le domaine de word_index[i] au top-k bizarre
                    boolean[] allowed = new boolean[corpusDomains.size()];
                    for (int v : topKCorpusIndices) allowed[v] = true;
                    for (int v = 0; v < corpusDomains.size(); v++) {
                        if (!allowed[v]) {
                            try {
                                word_index[i].remove(v);
                            } catch (Exception ex) {
                                
                                
                            }
                        }
                    }

                    // Créer scores et tokens limités au top-k
                    int[] tokens_topk = new int[topKPairs.size()];
                    double[] scores_topk = new double[topKPairs.size()];
                    for (int idx = 0; idx < topKPairs.size(); idx++) {
                        tokens_topk[idx] = topKPairs.get(idx)[1];
                        scores_topk[idx] = scores[topKPairs.get(idx)[1]];
                    }

                    // Appeler oracle avec le domaine restreint au top-k
                    Constraint c = Factory.oracle(word_index[i], tokens_topk, scores_topk);
                    c.setWeight(w);
                    cp.post(c);
                    if (PRINT_TRACE)
                        System.out.println("GPT, before BP (max token, 'the word', its probability) " + max_token
                                + ", '" + words.get(corpusDomains.get(max_token)) + "', " + max_score);
                    if (PRINT_TRACE) {
                        double[] temp = scores.clone();
                        Arrays.sort(temp);
                        for (int n = 1; n <= 5; n++) {
                            for (int m = 0; m < temp.length; m++) {
                                if (temp[temp.length - n] == scores[m]) {
                                    System.out.println("GPT, before BP (max token, 'the word', its probability) " + m
                                            + ", '" + words.get(corpusDomains.get(m)) + "', " + scores[m]);
                                }
                            }
                        }
                    }

                    try {
                        cp.fixPoint();
                    } catch (InconsistencyException e) {
                        if (PRINT_TRACE)
                            System.out.println("INCONSISTENCY at position " + i);
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
                                    + ", '" + words.get(corpusDomains.get(token)) + "', " + prob);
                        }
                    }

                    if (PRINT_TRACE)
                        System.out.println("CP model, before BP (max token, 'the word', its probability) "
                                + word_index[i].valueWithMaxMarginal() + ", '"
                                + words.get(corpusDomains.get(word_index[i].valueWithMaxMarginal())) + "', "
                                + word_index[i].maxMarginal());
                    cp.vanillaBP(NUM_PB);
                    if (PRINT_TRACE)
                        System.out.println("after BP (max token, 'the word', its probability) "
                                + word_index[i].valueWithMaxMarginal() + ", '"
                                + words.get(corpusDomains.get(word_index[i].valueWithMaxMarginal())) + "', "
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
                                    + words.get(corpusDomains.get(token)) + "', " + prob);
                        }
                    }

                    int chosen = word_index[i].biasedWheelValue();
                    word_index[i].assign(chosen);
                    num_tok++;

                    if (0 <= chosen && chosen < scores.length && scores[chosen] > 0) {
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
                        System.out.println("Sentence so far: " + current_sentence);
                        System.out.println("index chosen: " + corpusDomains.get(chosen));
                    }
                }

                double perplexityScore = Math.exp(-logSumProbs / num_tok);
                logs.add(new Logging(current_sentence, perplexityScore, new int[] {}));
            }

            // ===== Écriture résultat =====
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("num_iterations", NUM_ITERATIONS);
            result.put("logs", logs);
            String OUTPUT_DIR = args.length > 3 ? args[3] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_" + process_id + ".json";
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Logging {
        public String sentence;
        public double perplexity;
        public int[] tokens;

        public Logging(String sentence, double perplexity, int[] tokens) {
            this.sentence = sentence;
            this.perplexity = perplexity;
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
            String jsonContent = new String(Files.readAllBytes(Paths.get(phonemeMapPath)), StandardCharsets.UTF_8);
            phonemeMap = mapper.readTree(jsonContent);
            phonemeToId = new HashMap<>();
            idToCategory = new HashMap<>();
            parsePhonemeCategory("vowels", phonemeMap.get("vowels"));
            parsePhonemeCategory("consonants", phonemeMap.get("consonants"));
            parsePhonemeCategory("modifiers", phonemeMap.get("modifiers"));
            parsePhonemeCategory("diacritics_standalone", phonemeMap.get("diacritics_standalone"));
        }

        private void parsePhonemeCategory(String category, JsonNode node) {
            try {
                System.setOut(new PrintStream(System.out, true, "UTF-8"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            node.fields().forEachRemaining(subcategory -> {
                subcategory.getValue().fields().forEachRemaining(phoneme -> {
                    String symbol = phoneme.getKey();
                    int id = phoneme.getValue().get("id").asInt();
                    phonemeToId.put(symbol, id);
                    idToCategory.put(id, category);
                });
            });
        }

        /**
         * Phonémise seulement les top-k tokens (pas le corpus entier)
         * Retourne Map: vocab_id -> (phoneme_id -> count)
         */
        public Map<Integer, Map<Integer, Integer>> getPhonemesForTopK(int[] topKVocabIds) {
            Map<Integer, Map<Integer, Integer>> result = new HashMap<>();
            try {
                System.setOut(new PrintStream(System.out, true, "UTF-8"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                HttpClient client = HttpClient.newHttpClient();
                ObjectMapper mapper = new ObjectMapper();

                // Appel batch /phonemize_ids au serveur
                String body = mapper.writeValueAsString(Collections.singletonMap("ids", topKVocabIds));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5000/phonemize_ids"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                String response = client.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8))
                        .thenApply(HttpResponse::body).join();

                JsonNode root = mapper.readTree(response);
                JsonNode results = root.get("results");

                if (results != null && results.isArray()) {
                    for (JsonNode r : results) {
                        int vocabId = r.get("id").asInt();
                        JsonNode phonemesNode = r.get("phonemes");
                        String phonemesStr = phonemesNode.isNull() ? "" : phonemesNode.asText();
                        String normalizedStr = Normalizer.normalize(phonemesStr, Normalizer.Form.NFD);

                        Map<Integer, Integer> phonemeCounts = new HashMap<>();
                        if (!phonemesStr.isEmpty()) {
                            String[] phonemeArray = phonemesStr.split(" ");
                            for (String phoneme : phonemeArray) {
                                phoneme = phoneme.trim();
                                if (phoneme.isEmpty() || phoneme.equals("|") || phoneme.equals("||")) {
                                    continue;
                                }
                                if (phoneme.contains("ː")) {
                                    phoneme = phoneme.replace("ː", "");
                                }
                                // TODO: verifier cela avec Cimon
                                for (int i = 0; i < phoneme.length(); i++) {
                                    char ch = phoneme.charAt(i);
                                    String key = String.valueOf(ch);
                                    Integer id = phonemeToId.get(key);
                                    if (id != null) {
                                        phonemeCounts.put(id, phonemeCounts.getOrDefault(id, 0) + 1);
                                    } else {
                                        if (PRINT_TRACE) {
                                            System.out.println("Unknown phoneme: [" + key + "] (code: " + (int) ch
                                                    + ") in " + r.toString());
                                        }
                                    }
                                }

                            }
                        }
                        result.put(vocabId, phonemeCounts);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return result;
        }
    }

    static class TokenScorePair {
        int vocabId;
        int corpusIdx;
        double score;

        TokenScorePair(int vocabId, int corpusIdx, double score) {
            this.vocabId = vocabId;
            this.corpusIdx = corpusIdx;
            this.score = score;
        }
    }
}