package minicpbp.examples;

import java.io.PrintStream;
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
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
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

public class OrthophoniePreCalc {
    public static final boolean PRINT_TRACE = true;

    public static void main(String[] args) throws Exception {
        try {
            System.out.println("12 décembre - Pre-calced phoneme");
            int port = Integer.parseInt(args[1]);
            final int NUM_ITERATIONS = Integer.parseInt(args[0]);
            final int process_id = Integer.parseInt(args[2]);
            final int[][] idlist = { { 36, 37 }, { 29 }, { 30 } };
            final String llm_name = "QWEN2.5";

            List<Logging> logs = new ArrayList<>();
            ObjectMapper objectMapper = new ObjectMapper();

            // ===== Charger tokenizer_dict.txt =====
            List<String> lines = Collections.emptyList();
            try {
                lines = Files.readAllLines(
                        Paths.get("./src/main/java/minicpbp/examples/data/LLM/" + llm_name + "/tokenizer_dict.txt"),
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Tokenizer size (raw lines): " + lines.size());

            int token_size = Integer.parseInt(lines.get(lines.size() - 1).split(":")[0]);
            String[] corrected_lines = new String[token_size];
            Arrays.fill(corrected_lines, "");
            for (int i = 0; i < lines.size() - 1; i++) {
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
                                "./src/main/java/minicpbp/examples/data/LLM/" + llm_name + "/corpus_domain.json")),
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

            // ===== Charger cache phonèmes pré-calculé =====
            System.out.println("Chargement du cache phonèmes...");
            Map<Integer, Map<String, Object>> allPhonemeInfo = new HashMap<>();
            try {
                String cacheContent = new String(
                        Files.readAllBytes(Paths.get(
                                "./src/main/java/minicpbp/examples/data/LLM/" + llm_name + "/phoneme_cache.json")),
                        StandardCharsets.UTF_8);
                Map<String, Map<String, Object>> rawCache = objectMapper.readValue(cacheContent,
                        new TypeReference<Map<String, Map<String, Object>>>() {
                        });

                // Convertir String keys en Integer keys
                for (Map.Entry<String, Map<String, Object>> e : rawCache.entrySet()) {
                    int vocabId = Integer.parseInt(e.getKey());
                    allPhonemeInfo.put(vocabId, e.getValue());
                }
            } catch (Exception e) {
                System.err.println("Erreur lors du chargement du cache : " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("Cache chargé : " + allPhonemeInfo.size() + " tokens");
            System.out.println("Exemple: " + allPhonemeInfo.get(32773));

            // ===== Charger NSyll du CSV ELP =====
            System.out.println("Chargement des données NSyll du CSV ELP...");
            Map<String, Integer> wordToNSyll = new HashMap<>();
            try {
                List<String> csvLines = Files.readAllLines(
                        Paths.get("./src/main/java/minicpbp/examples/data/Phoneme/ELP.csv"),
                        StandardCharsets.UTF_8);

                if (csvLines.size() > 0) {
                    // Parser le header pour trouver l'index de la colonne "NSyll"
                    String[] header = csvLines.get(0).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Respecte les
                                                                                                  // guillemets
                    int nsyllIndex = -1;
                    int wordIndex = -1;

                    for (int i = 0; i < header.length; i++) {
                        if (header[i].trim().equals("\"NSyll\"")) {
                            nsyllIndex = i;
                        } else if (header[i].trim().equals("\"Word\"")) {
                            wordIndex = i;
                        }
                    }

                    System.out.println("  • NSyll column index: " + nsyllIndex + ", Word column index: " + wordIndex);

                    for (int i = 1; i < csvLines.size(); i++) {
                        try {
                            String[] row = csvLines.get(i).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                            if (row.length > Math.max(nsyllIndex, wordIndex)) {
                                String word = row[wordIndex].replaceAll("\"", "").trim(); // Retirer guillemets
                                String nsyllStr = row[nsyllIndex].replaceAll("\"", "").trim();

                                if (!word.isEmpty() && !nsyllStr.isEmpty() && !nsyllStr.equals("#")) {
                                    try {
                                        int nsyll = Integer.parseInt(nsyllStr);
                                        wordToNSyll.put(word.toLowerCase(), nsyll);
                                    } catch (NumberFormatException e) {
                                        // Ignorer les valeurs NSyll invalides
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignorer les lignes mal formées
                        }
                    }

                }
                System.out.println("  • " + wordToNSyll.size() + " mots chargés avec NSyll");
            } catch (Exception e) {
                System.err.println("Erreur lors du chargement du CSV ELP : " + e.getMessage());
                e.printStackTrace();
            }
            // ===== Créer mapping NSyll pour chaque indice de corpus domain =====
            int[] nsyllPerIndex = new int[corpusDomains.size()];
            Arrays.fill(nsyllPerIndex, -1); // -1 = NSyll inconnu
            int unknownCount = 0;
            for (int idx = 0; idx < corpusDomains.size(); idx++) {
                int vocabId = corpusDomains.get(idx);
                String word = words.get(vocabId).toLowerCase().trim().replaceAll("ġ", ""); // Nettoyer le mot

                if (wordToNSyll.containsKey(word)) {
                    nsyllPerIndex[idx] = wordToNSyll.get(word);
                } else {
                    unknownCount++;
                    // System.out.println("NSyll inconnu pour le mot: '" + word + "'");
                }
            }

            System.out.println("  • Mapping NSyll créé pour corpus domain");
            System.out.println("  • Mots avec NSyll inconnu: " + unknownCount + " / " + corpusDomains.size());

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
            // int[] p_Ids = new int[] { 37 }; // IDs pour 'p'
            // int[] b_Ids = new int[]{30}; // IDs pour 'b'
            int[] f_u_Ids = new int[] { 6, 41 }; // IDs pour 'f' et 'u'
            // phonemeRanges.add(new PhonemeRange(b_Ids, 3, 2000));

            final int NUM_PB = 3; // Nombre de passages de BP
            final double w = 0.8; // Poids des contraintes oracle
            final int size = 20;// Longueur de la séquence à générer
            final int MAX_NSYLL = 2;
            final int MIN_NSYLL = 1;

            for (int k = 0; k < NUM_ITERATIONS; k++) {
                Solver cp = makeSolver();
                // 2 genenration par contrainte exemple
                System.out.println("=== Itération " + k + " avec k/2 = " + k / 2 + " ===");
                // phonemeRanges.add(new PhonemeRange(idlist[k / 2], 4, 5));

                phonemeRanges.add(new PhonemeRange(f_u_Ids, size, 2000));

                // Créer variables pour chaque position
                IntVar[] word_index = makeIntVarArray(cp, size, 0, corpusDomains.size() - 1);

                // ===== CRÉER VARIABLES PHONÉMIQUES GLOBALES =====
                IntVar[][] phonemeCountsPerGroupPerPos = new IntVar[phonemeRanges.size()][size];
                IntVar[] totalPhonemePerGroup = new IntVar[phonemeRanges.size()];
                for (int g = 0; g < phonemeRanges.size(); g++) {
                    PhonemeRange range = phonemeRanges.get(g);
                    totalPhonemePerGroup[g] = makeIntVar(cp, range.minCount, range.maxCount);

                    for (int pos = 0; pos < size; pos++) {
                        phonemeCountsPerGroupPerPos[g][pos] = makeIntVar(cp, 0, 2000);
                    }

                    // Contrainte globale : la somme de tous les phonèmes du groupe = total voulu
                    cp.post(sum(phonemeCountsPerGroupPerPos[g], totalPhonemePerGroup[g]));
                }

                // Contraintes Syllabique
                // ===== Variable NSyll pour chaque position =====
                IntVar[] nsyllPerPos = new IntVar[size];
                for (int j = 0; j < size; j++) {
                    nsyllPerPos[j] = makeIntVar(cp, MIN_NSYLL, MAX_NSYLL);
                }
                // ===== CONTRAINTE ÉLÉMENT : lier word_index à nsyllPerPos =====
                for (int j = 0; j < size; j++) {
                    cp.post(element(nsyllPerIndex, word_index[j], nsyllPerPos[j]));
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
                String selectedWord = "" + commonWords[new Random().nextInt(commonWords.length)];

                // On demande explicitement de la variété
                // In english
                String SystemPrompt = "You are a speech therapist assistant that generate diverse text complying to certain constraints to help children with speech disability.";
                String UserPrompt = "Give me a list of " + size
                        + " DIFFERENT unrelated words WITHOUT repetitions. Give me directly the list of words without introduction and do NOT make phrases.";
                // En français
                // String SystemPrompt = "Tu es un assistant orthophoniste. Tu génères
                // UNIQUEMENT ce qui est demandé, sans introduction, sans explication, sans
                // conclusion et en français.";
                // String UserPrompt = "Liste UNIQUEMENT des mots en rapport avec l'espace.
                // Aucune introduction. Aucun mot hors sujet."; //Exemples de mots corrects:
                // étoile, planète, fusée, astronaute, lune, soleil, orbite, galaxie, météore.";

                // Format spécifique à Zephyr / Mistral
                // String bos = "<|system|>\n" + SystemPrompt + "</s>\n<|user|>\n" + UserPrompt
                //         + "</s>\n<|assistant|>\n";

                // Construction du prompt complet format ChatML
                // Note : Les \n sont obligatoires pour Qwen
                String bos = "<|im_start|>system\n" + SystemPrompt + "<|im_end|>\n" +
                 "<|im_start|>user\n" + UserPrompt + "<|im_end|>\n" +
                 "<|im_start|>assistant\n";
                /*
                 * HttpRequest request1 = HttpRequest.newBuilder()
                 * .uri(URI.create("http://localhost:" + port + "/tokenize"))
                 * .POST(HttpRequest.BodyPublishers.ofString(selectedWord))
                 * .build();
                 * String response1 = client.sendAsync(request1, BodyHandlers.ofString())
                 * .thenApply(HttpResponse::body).join();
                 * int[] split_response1 = Arrays.stream(response1.substring(1,
                 * response1.length() - 2).split(","))
                 * .mapToInt(Integer::parseInt).toArray();
                 * int[] tokens1 = Arrays.copyOfRange(split_response1, 1,
                 * split_response1.length);
                 * 
                 * int i = 0;
                 * for (; i < tokens1.length && i < size; i++) {
                 * if (indexToCorpusDomain.containsKey(tokens1[i])) {
                 * word_index[i].assign(indexToCorpusDomain.get(tokens1[i]));
                 * }
                 * }
                 */
                String current_sentence = bos;// + selectedWord;
                Double logSumProbs = 0.0;
                int i = 0;// TODO: a commenter apres
                int num_tok = i;
                for (int j = 0; j < size; j++) {
                    for (int g = 0; g < phonemeRanges.size(); g++) {
                        PhonemeRange range = phonemeRanges.get(g);
                        int[] phonemeCountsForThisGroup = new int[corpusDomains.size()];
                        Arrays.fill(phonemeCountsForThisGroup, 0);

                        for (int idx = 0; idx < corpusDomains.size(); idx++) {
                            int vocabId = corpusDomains.get(idx);

                            if (allPhonemeInfo.containsKey(vocabId)) {
                                Map<String, Object> phonemeData = allPhonemeInfo.get(vocabId);
                                int groupCount = 0;
                                for (int phonemeId : range.phonemeIds) {
                                    Map<String, Integer> countObj = (Map<String, Integer>) phonemeData
                                            .get("phoneme_counts");
                                    String phonemeIdStr = phonemeConstraints.idToPhoneme.get(phonemeId);
                                    Map<String, Integer> cleanedMap = countObj.entrySet().stream()
                                            .collect(Collectors.toMap(
                                                    entry -> entry.getKey().replace("ː", ""),
                                                    Map.Entry::getValue,
                                                    Integer::sum)); //TODO: verifier si les length modifier sont utile
                                    int count = cleanedMap.getOrDefault(phonemeIdStr, 0);
                                    groupCount += count;
                                }
                                phonemeCountsForThisGroup[idx] = groupCount;
                            }
                        }
                        cp.post(element(phonemeCountsForThisGroup, word_index[j], phonemeCountsPerGroupPerPos[g][j]));
                    }
                }

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

                            } catch (Exception e) {
                                if (PRINT_TRACE)
                                    System.err.println("Erreur parsing tuple: " + e.getMessage());
                            }
                        }
                    }

                    if (total_score > 0) {
                        for (int j = 0; j < scores.length; j++) {
                            if (scores[j] > 0)
                                scores[j] /= total_score;
                        }
                    }
                    max_score /= total_score;

                    // postElementConstraint(cp, word_index, corpusDomains, allPhonemeInfo,
                    // phonemeRanges,
                    // phonemeCountsPerGroupPerPos, i);

                    Constraint c = Factory.oracle(word_index[i], tokens, scores);
                    c.setWeight(w);
                    cp.post(c);
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
                            System.out.println("INCONSISTENCY at position " + i + " error: " + e);
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
                    cp.vanillaBP(NUM_PB);

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
                    // Retirer le mot choisi des positions suivantes pour éviter répétition si liste de mot
                    //Peut etre remplacer par un all different surement
                    for (int j = i + 1; j < word_index.length; j++) {
                        word_index[j].remove(chosen);
                    }
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
                    current_sentence = current_sentence.replace("Ġ", " ");

                    if (PRINT_TRACE) {
                        System.out.println("Sentence so far: " + current_sentence);
                        System.out.println("index chosen: " + corpusDomains.get(chosen));
                    }
                }
                System.out.println(current_sentence);

                double perplexityScore = Math.exp(-logSumProbs / num_tok);
                logs.add(new Logging(current_sentence, perplexityScore, "Phoneme: " + Arrays.stream(idlist[k / 2])
                        .mapToObj(phonemeConstraints.idToPhoneme::get)
                        .collect(Collectors.toList()) +
                        " min:4 max:5"));
            }
            // ===== Écriture résultat =====
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("num_iterations", NUM_ITERATIONS);
            result.put("logs", logs);
            String OUTPUT_DIR = args.length > 3 ? args[3] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_" + llm_name + "_" + process_id + ".json";
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Logging {
        public String sentence;
        public double perplexity;
        public String details;

        public Logging(String sentence, double perplexity, String details) {
            this.sentence = sentence;
            this.perplexity = perplexity;
            this.details = details;
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
        private Map<Integer, String> idToPhoneme;
        private Map<Integer, String> idToCategory;

        public PhonemeConstraints(String phonemeMapPath) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            String jsonContent = new String(Files.readAllBytes(Paths.get(phonemeMapPath)), StandardCharsets.UTF_8);
            phonemeMap = mapper.readTree(jsonContent);
            phonemeToId = new HashMap<>();
            idToPhoneme = new HashMap<>();
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
                    idToPhoneme.put(id, symbol);
                });
            });
        }
    }

}