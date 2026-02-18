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
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.ObjectInputFilter.Config;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
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
import java.util.Iterator;

import fzn.test;

public class NLP_v5 {
    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(args[1]);
        final int NUM_ITERATIONS = Integer.parseInt(args[0]);
        final String llm_name = args.length > 2 ? args[2] : "zephyr";
        final String language = args.length > 3 ? args[3] : "eng"; // eng ou fr

        try {
            // On force System.out à utiliser l'encodage UTF-8 vers la sortie standard
            // (console)
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));

            // On fait pareil pour les erreurs si besoin
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, "UTF-8"));

            System.out.println("Test d'accents : é, à, è, ô, î, ù.");
            System.out.println("L'accélération fonctionne !");

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        try {

            long startTime = System.currentTimeMillis();

            List<Logging> logs = new ArrayList<>();

            List<String> lines = Collections.emptyList();
            try {
                lines = Files.readAllLines(
                        Paths.get("./src/main/java/minicpbp/examples/data/LLM/" + llm_name + "/tokenizer_dict.txt"),
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
            }

            int sentence_end_index = -1;
            int token_size = Integer.parseInt(lines.get(lines.size() - 1).split(":")[0]);
            if (llm_name.equals("Mistral")) {
                sentence_end_index = 29491;
            }
            String[] corrected_lines = new String[token_size];
            Arrays.fill(corrected_lines, "");
            for (int i = 0; i < lines.size() - 1; i++) {
                String[] line = lines.get(i).split("::");
                if (line.length > 1) {
                    if (llm_name.equals("QWEN2.5")) {
                        corrected_lines[Integer.parseInt(line[0])] = line[1].replace("Ġ", " ");
                    } else if (llm_name.equals("Mistral")) {
                        corrected_lines[Integer.parseInt(line[0])] = line[1].replace("▁", " ");
                    } else {
                        corrected_lines[Integer.parseInt(line[0])] = line[1];
                    }
                    if (line[1].equals(".") && sentence_end_index == -1 && !llm_name.equals("Mistral")) {
                        sentence_end_index = i;
                    }
                }
            }

            final List<String> tokens_list = Arrays.asList(corrected_lines);
            ArrayList<String> words = new ArrayList<>();
            Map<List<Integer>, List<Integer>> corpusDomainsSet = new HashMap<>();
            Map<Integer, List<Integer>> corpusDomainToIndex = new HashMap<>();
            ObjectMapper objectMapper = new ObjectMapper();
            int k = 0;
            // Matching: premiere boite rose
            try {
                String jsonContent;
                if (language.equals("eng")) {
                    System.out
                            .println("Chargement du corpus tokenized words anglais...");
                    jsonContent = new String(Files.readAllBytes(Paths.get(
                            "./src/main/java/minicpbp/examples/data/LLM/" + llm_name + "/corpus_tokenized_words.json")),
                            StandardCharsets.UTF_8);
                } else if (language.equals("fr")) {
                    System.out
                            .println("Chargement du corpus tokenized words français...");
                    jsonContent = new String(Files.readAllBytes(Paths.get(
                            "./src/main/java/minicpbp/examples/data/LLM/" + llm_name
                                    + "/corpus_tokenized_words_French.json")),
                            StandardCharsets.UTF_8);
                } else {
                    throw new Exception("Language not supported: " + language);
                }

                final List<List<Integer>> parsedCorpusDomains = objectMapper.readValue(jsonContent,
                        new TypeReference<List<List<Integer>>>() {
                        });
                for (int i = 0; i < parsedCorpusDomains.size(); i++) {
                    List<Integer> sublist = parsedCorpusDomains.get(i);
                    String word_string = sublist.stream().map(n -> tokens_list.get(n)).collect(Collectors.joining(""));

                    if (words.contains(word_string)) {
                        continue;
                    }
                    words.add(word_string);
                    for (int j = 0; j <= sublist.size() - 1; j++) {
                        List<Integer> prefix = sublist.subList(0, j + 1);
                        if (llm_name.equals("Mistral") && prefix.equals(List.of(29473))) { // pas mettre d'espace seul
                                                                                           // dans le corpus domain
                            continue;
                        }
                        if (!corpusDomainsSet.containsKey(prefix))
                            corpusDomainsSet.put(prefix, new ArrayList<>(List.of(k)));
                        else
                            corpusDomainsSet.get(prefix).add(k);
                    }
                    corpusDomainToIndex.put(k, sublist);
                    k++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            words.add(".");
            corpusDomainsSet.put(List.of(sentence_end_index),
                    new ArrayList<>(Collections.singletonList(words.size() - 1)));
            corpusDomainToIndex.put(words.size() - 1, List.of(sentence_end_index));

            // int endSentenceToken=-1;
            if (llm_name.equals("Mistral")) {
                words.add(",");
                corpusDomainsSet.put(List.of(29493),
                        new ArrayList<>(Collections.singletonList(words.size() - 1)));
                corpusDomainToIndex.put(words.size() - 1, List.of(29493));

                // endSentenceToken = 2;
                // words.add("</s>");
                // corpusDomainsSet.put(List.of(endSentenceToken),
                // new ArrayList<>(Collections.singletonList(words.size() - 1)));
                // corpusDomainToIndex.put(words.size() - 1, List.of(endSentenceToken));
            }

            List<Integer> corpusDomains = new ArrayList<>();
            for (int idx = 0; idx < words.size(); idx++) {
                corpusDomains.add(idx);
            }

            System.out.println("corpusDomains size: " + corpusDomains.size());
            
            final int final_sentence_end = llm_name.equals("Mistral")? corpusDomains.get(corpusDomains.size() - 2) : corpusDomains.get(corpusDomains.size() - 1);

            // ===== Charger les mots non tokenizer =====
            ArrayList<String> wordsList = new ArrayList<>();
            try {
                String jsonContent;
                if (language.equals("eng")) {
                    System.out
                            .println("Chargement du corpus words anglais...");
                    jsonContent = new String(Files.readAllBytes(Paths.get(
                            "./src/main/java/minicpbp/examples/data/LLM/" + llm_name + "/corpus_words.json")),
                            StandardCharsets.UTF_8);
                } else if (language.equals("fr")) {
                    System.out
                            .println("Chargement du corpus words français...");
                    jsonContent = new String(Files.readAllBytes(Paths.get(
                            "./src/main/java/minicpbp/examples/data/LLM/" + llm_name
                                    + "/corpus_words_French.json")),
                            StandardCharsets.UTF_8);
                } else {
                    throw new Exception("Language not supported: " + language);
                }
                final List<String> parsedCorpusWord = objectMapper.readValue(jsonContent,
                        new TypeReference<List<String>>() {
                        });
                for (int i = 0; i < parsedCorpusWord.size(); i++) {
                    wordsList.add(parsedCorpusWord.get(i));
                    // if (Character.isLowerCase(parsedCorpusWord.get(i).charAt(0))) {

                    //     wordsList.add(parsedCorpusWord.get(i).substring(0, 1).toUpperCase()
                    //             + parsedCorpusWord.get(i).substring(1));
                    // }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            wordsList.add(".");
            if (llm_name.equals("Mistral")) {
                wordsList.add(",");
            }

            // ===== Charger NSyll du CSV ELP =====
            System.out.println("Chargement des données NSyll et IPA du CSV ELP...");
            Map<String, Integer> wordToNSyll = new HashMap<>();
            Map<String, String> wordToIPA = new HashMap<>();
            String wordHeader;
            String nsyllHeader;
            String ipaHeader;
            try {
                List<String> csvLines;
                if (language.equals("eng")) {
                    csvLines = Files.readAllLines(
                            Paths.get("./src/main/java/minicpbp/examples/data/Phoneme/ELP_with_IPA.csv"),
                            StandardCharsets.UTF_8);
                    wordHeader = "Word";
                    nsyllHeader = "NSyll";
                    ipaHeader = "Word_IPA";
                } else if (language.equals("fr")) {
                    csvLines = Files.readAllLines(
                            Paths.get("./src/main/java/minicpbp/examples/data/Phoneme/eqol_with_ipa.csv"),
                            StandardCharsets.UTF_8);
                    wordHeader = "ortho";
                    nsyllHeader = "syllabes";
                    ipaHeader = "phono_IPA";
                } else {
                    throw new Exception("Language not supported: " + language);
                }
                if (csvLines.size() > 0) {
                    // Parser le header pour trouver l'index des colonnes
                    String[] header = csvLines.get(0).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    int nsyllIndex = -1;
                    int wordIndex = -1;
                    int ipaIndex = -1;

                    for (int i = 0; i < header.length; i++) {
                        if (header[i].trim().equals(nsyllHeader)) {
                            nsyllIndex = i;
                        } else if (header[i].trim().equals(wordHeader)) {
                            wordIndex = i;
                        } else if (header[i].trim().equals(ipaHeader)) {
                            ipaIndex = i;
                        }
                    }

                    System.out.println("  • NSyll column index: " + nsyllIndex + ", Word column index: " + wordIndex
                            + ", IPA column index: " + ipaIndex);

                    for (int i = 1; i < csvLines.size(); i++) {
                        try {
                            String[] row = csvLines.get(i).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                            if (row.length > Math.max(Math.max(nsyllIndex, wordIndex), ipaIndex)) {
                                String word = row[wordIndex].replaceAll("\"", "").trim();
                                String nsyllStr = row[nsyllIndex].replaceAll("\"", "").replace(".0",
                                        "").trim();
                                String ipa = row[ipaIndex].replaceAll("\"", "").trim();

                                if (!word.isEmpty() && !nsyllStr.isEmpty() && !nsyllStr.equals("#")) {
                                    try {
                                        int nsyll = Integer.parseInt(nsyllStr);
                                        wordToNSyll.put(word.toLowerCase(), nsyll);
                                        wordToIPA.put(word.toLowerCase(), ipa);
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
                System.out.println("  • " + wordToNSyll.size() + " mots chargés avec NSyll et IPA");
            } catch (Exception e) {
                System.err.println("Erreur lors du chargement du CSV ELP : " + e.getMessage());
                e.printStackTrace();
            }
            // ===== Créer mapping NSyll pour chaque indice de corpus domain =====
            // int[] nsyllPerIndex = new int[corpusDomains.size()];
            // String[] ipaPerIndex = new String[corpusDomains.size()];
            // Arrays.fill(nsyllPerIndex, -1); // -1 = NSyll inconnu
            // int unknownCount = 0;
            // for (int idx = 0; idx < corpusDomains.size(); idx++) {
            // try {

            // int vocabId = corpusDomains.get(idx);
            // String word = wordsList.get(vocabId).toLowerCase().trim();

            // if (wordToNSyll.containsKey(word)) {
            // nsyllPerIndex[idx] = wordToNSyll.get(word);
            // ipaPerIndex[idx] = wordToIPA.get(word);
            // } else {
            // unknownCount++;
            // ipaPerIndex[idx] = "";
            // System.out.println("NSyll/IPA inconnu pour le mot: '" + word + "'");
            // }
            // } catch (Exception e) {
            // e.printStackTrace();
            // }
            // }

            // System.out.println(" • Mapping NSyll et IPA créé pour corpus domain");
            // System.out.println(" • Mots avec NSyll/IPA inconnu: " + unknownCount + " / "
            // + corpusDomains.size());

            // ===== Initialiser PhonemeConstraints =====
            PhonemeConstraints phonemeConstraints = new PhonemeConstraints(
                    "./src/main/java/minicpbp/examples/data/Phoneme/phoneme_map.json");

            // ===== Créer le mapping phonemeIds pour chaque indice de corpus domain =====
            // int[][] phonemeIdsPerIndex = new int[corpusDomains.size()][];
            // for (int idx = 0; idx < corpusDomains.size(); idx++) {
            // String ipa = ipaPerIndex[idx];
            // if (ipa!= null && !ipa.isEmpty()) {
            // // Parser la chaîne IPA et extraire les phonèmes
            // List<Integer> phonemeIds = phonemeConstraints.parseIPAToPhonemeIds(ipa);
            // phonemeIdsPerIndex[idx] =
            // phonemeIds.stream().mapToInt(Integer::intValue).toArray();
            // } else {
            // phonemeIdsPerIndex[idx] = new int[0];
            // }
            // }

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
            // phonemeRanges.add(new PhonemeRange(b_Ids, 3, 2000));

            final int MIN_NUMBER_WORD = 5;
            final int MAX_NUMBER_WORD = 8;

            final int MAX_NUMBER_PHRASE = 4;

            final boolean PRINT_TRACE = true;
            final int NUM_PB = 3;
            final double[] wArray = { 1.6 }; // 0.8 pour liste de mot
            // Pourquoi un nbr max de token?
            final int SENTENCE_MAX_NUMBER_TOKENS = MAX_NUMBER_WORD;
            final int ORACLE_TOP_K = 500;

            final int MAX_NSYLL = 2;
            final int MIN_NSYLL = 2;
            // final int NUM_ITERATIONS = 8;

            char letter = 'f';

            int[] f_u_Ids = { 6, 41 }; // IDs pour 'f' et 'u'
            phonemeRanges.add(new PhonemeRange(f_u_Ids, MAX_NUMBER_WORD - 2,
                    MAX_NUMBER_WORD * 3));

            double initTime = (System.currentTimeMillis() - startTime) / 1000.0;
            System.out.println("Initialization time (s): " + initTime);

            HttpClient client = HttpClient.newHttpClient();

            // String[] tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS+1];

            for (int z = 0; z < NUM_ITERATIONS; z += 1) {
                System.out.println("=== Itération " + z + " avec z/2 = " + z / 2 + " ===");
                double w = wArray[z / (NUM_ITERATIONS / wArray.length)];
                System.out.println("Poids w pour cette itération: " + w);

                // tokens_used = new String[SENTENCE_MAX_NUMBER_TOKENS];
                String SystemPrompt = "";
                String UserPrompt = "";
                String phoneme_list = "";

                for (int phonemeId : phonemeRanges.get(0).phonemeIds) {
                    phoneme_list += phonemeConstraints.idToPhoneme.get(phonemeId) + ", ";
                }
                if (language.equals("eng")) {

                    System.out.println("Génération de texte en anglais.");
                    SystemPrompt = "Generate only what is asked from you without introduction, explication, emojis nor conclusion.";
                    UserPrompt = "Give me a short sentence with a MAXIMUM of 8 words. It must have one word that starts with the letter "
                            + letter + ".";
                    // remove with no link betwween them et expliquer pk j pdis qu'une phrase c un
                    // sujet et verbe
                } else if (language.equals("fr")) {
                    System.out.println("Génération de texte en français.");
                    SystemPrompt = "Génères UNIQUEMENT ce qui est demandé sans introduction, sans explication, sans conclusion, sans emojis et en français.";
                    UserPrompt = "Donne moi une courte phrase de MAXIMUM 8 mots. Elle doit contenir 1 mot commençant par "
                            + letter + ".";

                } else {
                    throw new Exception("Language not supported: " + language);
                }
                System.out.println("User Prompt: " + UserPrompt);
                String instruction = "";
                if (llm_name.equals("zephyr")) {
                    // Format spécifique à Zephyr / Mistral
                    instruction = "<|system|>\n" + SystemPrompt + "</s>\n<|user|>\n" + UserPrompt
                            + "</s>\n<|assistant|>\n";
                } else if (llm_name.equals("QWEN2.5")) {
                    // Construction du prompt complet format ChatML
                    // Note : Les \n sont obligatoires pour Qwen
                    instruction = "<|im_start|>system\n" + SystemPrompt + "<|im_end|>\n" +
                            "<|im_start|>user\n" + UserPrompt + "<|im_end|>\n" +
                            "<|im_start|>assistant\n";
                } else if (llm_name.equals("Mistral")) {
                    // Mistral n'a pas de balise <|system|> dédiée dans le format brut.
                    // La convention est de mettre l'instruction système au début du bloc [INST].
                    instruction = "<s>[INST] " + SystemPrompt + "\n\n" + UserPrompt + " [/INST] ";
                } else {
                    throw new Exception("LLM not supported: " + llm_name);
                }

                int num_phrase = 0;
                String current_sentence = "";
                String solution = "";

                while (num_phrase < MAX_NUMBER_PHRASE) {

                    Solver cp = makeSolver();

                    StateManager sm = cp.getStateManager();

                    // // ======= Scénario 1 =======
                    // // ===== CRÉER VARIABLES PHONÉMIQUES GLOBALES =====
                    // IntVar[][] phonemeCountsPerGroupPerPos = new
                    // IntVar[phonemeRanges.size()][MAX_NUMBER_WORD];
                    // IntVar[] totalPhonemePerGroup = new IntVar[phonemeRanges.size()];

                    // for (int g = 0; g < phonemeRanges.size(); g++) {
                    // PhonemeRange range = phonemeRanges.get(g);
                    // totalPhonemePerGroup[g] = makeIntVar(cp, range.minCount,
                    // range.maxCount);

                    // for (int pos = 0; pos < MAX_NUMBER_WORD; pos++) {
                    // phonemeCountsPerGroupPerPos[g][pos] = makeIntVar(cp, 0, 2000);
                    // }
                    // cp.post(sum(phonemeCountsPerGroupPerPos[g], totalPhonemePerGroup[g]));
                    // }

                    // for (int j = 0; j < MAX_NUMBER_WORD; j++) {
                    // for (int g = 0; g < phonemeRanges.size(); g++) {
                    // PhonemeRange range = phonemeRanges.get(g);
                    // int[] phonemeCountsForThisGroup = new int[corpusDomains.size()];
                    // Arrays.fill(phonemeCountsForThisGroup, 0);

                    // for (int idx = 0; idx < corpusDomains.size(); idx++) {
                    // int groupCount = 0;
                    // // Compter les phonèmes du groupe présents dans les IPA de ce mot
                    // for (int phonemeId : range.phonemeIds) {
                    // for (int phonemeIdInWord : phonemeIdsPerIndex[idx]) {
                    // if (phonemeId == phonemeIdInWord) {
                    // groupCount++;
                    // }
                    // }
                    // }
                    // phonemeCountsForThisGroup[idx] = groupCount;
                    // }
                    // cp.post(element(phonemeCountsForThisGroup, word_index[j],
                    // phonemeCountsPerGroupPerPos[g][j]));
                    // }
                    // }

                    // // Contraintes Syllabique
                    // // ===== Variable NSyll pour chaque position =====
                    // IntVar[] nsyllPerPos = new IntVar[MAX_NUMBER_WORD];
                    // for (int j = 0; j < MAX_NUMBER_WORD; j++) {
                    // nsyllPerPos[j] = makeIntVar(cp, MIN_NSYLL, MAX_NSYLL);
                    // }
                    // // // // ===== CONTRAINTE ÉLÉMENT : lier word_index à nsyllPerPos =====
                    // for (int j = 0; j < MAX_NUMBER_WORD; j++) {
                    // cp.post(element(nsyllPerIndex, word_index[j],
                    // nsyllPerPos[j]));
                    // }

                    // cp.post(allDifferentBinary(word_index));

                    // ========= Scénario 2 ============
                    // verifier quelle mot sont dans word mais pas dans wordslist
                    // for (int i = 0; i < words.size(); i++) {
                    // if (!wordsList.contains(words.get(i).trim())) {
                    // System.out.println(
                    // "Le mot '" + words.get(i) + "' est dans 'words' mais pas dans 'wordsList'");
                    // }
                    // }
                    int position = 0;
                    // Créer un tableau d'IntVar au lieu de BoolVar pour element()
                    IntVar[] isWordStartWith = new IntVar[corpusDomains.size()];
                    for (int i1 = 0; i1 < corpusDomains.size(); i1++) {
                        String word = wordsList.get(corpusDomains.get(i1)).trim();
                        // Vérifier que la position existe dans le mot
                        if (word.length() > position) {
                            if (word.charAt(position) == letter
                                    || word.charAt(position) == Character.toUpperCase(letter)) {
                                isWordStartWith[i1] = makeIntVar(cp, 1, 1); // 1 si commence par 'f'
                            } else {
                                isWordStartWith[i1] = makeIntVar(cp, 0, 0); // 0 sinon
                            }
                        } else {
                            isWordStartWith[i1] = makeIntVar(cp, 0, 0); // 0 si le mot est trop court
                        }
                    }
                    String selectedWord = "";
                    if (language.equals("eng")) {

                        String[] commonWords = {
                                "Iài",
                                "you",
                                "he",
                                "she",
                                "it",
                                "we",
                                "they",
                                "the",
                                "a",
                                "an",
                                "this",
                                "that",
                                "these",
                                "those",
                                "there" };
                        selectedWord = "" + commonWords[new Random().nextInt(commonWords.length)];

                    } else if (language.equals("fr")) {
                        String[] commonWords = {
                                "je",
                                "tu",
                                "il",
                                "elle",
                                "on",
                                "nous",
                                "vous",
                                "ils",
                                "elles",
                                "le",
                                "la",
                                "un",
                                "une",
                                "ce",
                                "cette",
                                "cet",
                                "ces" };

                        selectedWord = "" + commonWords[new Random().nextInt(commonWords.length)];

                    } else {

                        throw new Exception("Language not supported: " + language);
                    }
                    current_sentence = selectedWord;

                    String tokenizedStringSentence;
                    HttpRequest request1 = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/tokenize"))
                            .POST(HttpRequest.BodyPublishers.ofString(current_sentence))
                            .build();
                    String response1 = client.sendAsync(request1, BodyHandlers.ofString())
                            .thenApply((HttpResponse<String> r) -> r.body()).join();
                    int[] split_response1 = Arrays.stream(response1.substring(1, response1.length() - 2).split(","))
                            .mapToInt(Integer::parseInt).toArray();

                    tokenizedStringSentence = Arrays.stream(split_response1)
                            .mapToObj(i -> tokens_list.get(i))
                            .collect(Collectors.joining(""));
                    System.out.println("Initial tokenized sentence: " + tokenizedStringSentence);

                    int num_tok = current_sentence.split(" ").length;

                    // =========Génération de mots============
                    while (num_tok < SENTENCE_MAX_NUMBER_TOKENS + 1) {

                        int i = num_tok;
                        System.out.println(num_tok);
                        System.out.println(current_sentence);
                        List<IntVar> word_index_list = new ArrayList<>();
                        for (int j = 0; j < MIN_NUMBER_WORD; j++) {
                            IntVar var = makeIntVar(cp, 0, corpusDomains.size() - 1);
                            var.remove(final_sentence_end);
                            word_index_list.add(var);
                        }

                        if (i >= MIN_NUMBER_WORD) {
                            for (int j = MIN_NUMBER_WORD; j < i + 1; j++) {
                                System.out.println("Adding new word_index at position " + j);
                                IntVar newVar = makeIntVar(cp, 0, corpusDomains.size() - 1);
                                word_index_list.add(newVar);
                            }
                        }
                        System.out.println("Word_index_list size: " + word_index_list.size());
                        final IntVar[] word_index = word_index_list.toArray(new IntVar[0]);

                        IntVar[] isWordIndexStartWith = new IntVar[word_index.length];
                        for (int j = 0; j < isWordIndexStartWith.length; j++) {
                            isWordIndexStartWith[j] = makeIntVar(cp, 0, 1);
                            cp.post(element(isWordStartWith, word_index[j], isWordIndexStartWith[j]));
                        }
                        int[] valueNeeded = { 1 }; // au moins un mot commençant par 'f'
                        cp.post(among(isWordIndexStartWith, valueNeeded, 1, 2));

                        int constraintSize = cp.getConstraints().size();

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/token"))
                                .POST(HttpRequest.BodyPublishers.ofString(instruction + current_sentence))
                                .build();
                        String response = client.sendAsync(request, BodyHandlers.ofString())
                                .thenApply(HttpResponse::body)
                                .join();

                        int[] tokensNew = new int[corpusDomains.size()];
                        double[] scoresNew = new double[corpusDomains.size()];

                        int[] tokensContinue = new int[corpusDomains.size()];
                        double[] scoresContinue = new double[corpusDomains.size()];

                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode jsonNode = mapper.readTree(response);
                        ArrayNode tupleList = (ArrayNode) jsonNode.get("prob");

                        List<Integer> last_word = mapper.convertValue(jsonNode.get("tokenized_last_word"),
                                new TypeReference<List<Integer>>() {
                                });
                        System.out.println("Last word tokenized: " + last_word);

                        // Séparation des nouveaux tokens et de la continuation
                        int max_token_new = -1;
                        double max_score_new = 0;
                        double total_score_new = 0;
                        double total_tokens_score_new = 0;

                        int max_token_continue = -1;
                        double max_score_continue = 0;
                        double total_score_continue = 0;
                        double total_tokens_score_continue = 0;

                        List<Pair<Integer, Double>> tokenScoreListNew = new ArrayList<>();
                        List<Pair<List<Integer>, Double>> tokenScoreListContinue = new ArrayList<>();
                        List<Pair<Integer, Double>> tokenLLM = new ArrayList<>();
                        for (JsonNode tuple : tupleList) {
                            try {
                                int token = (tuple.get(0)).asInt();
                                double score = (tuple.get(1)).asDouble();
                                tokenLLM.add(Pair.of(token, score));
                                List<Integer> combinedList = new ArrayList<>(last_word);
                                combinedList.add(token);
                                if (!corpusDomainsSet.containsKey(List.of(token))
                                        && !corpusDomainsSet.containsKey(combinedList))
                                    continue;
                                if (score < 0)
                                    continue;
                                if (corpusDomainsSet.containsKey(combinedList)) {
                                    tokenScoreListContinue.add(Pair.of(combinedList, score));
                                } else {
                                    tokenScoreListNew.add(Pair.of(token, score));
                                }
                            } catch (Exception e) {
                                if (PRINT_TRACE) {
                                    System.err.println(tuple);
                                    System.err.println(e);
                                }
                            }
                        }

                        tokenScoreListNew.sort((a, b) -> Double.compare(
                                b.second, a.second));
                        tokenScoreListContinue.sort((a, b) -> Double.compare(
                                b.second, a.second));
                        tokenLLM.sort((a, b) -> Double.compare(
                                b.second, a.second));
                        for (int t = 0; t < Math.min(5, tokenLLM.size()); t++) {
                            Pair<Integer, Double> pair = tokenLLM.get(t);
                            System.out
                                    .println(
                                            "Top " + (t + 1) + " LLM: token=" + tokens_list.get(pair.first) + ", index="
                                                    + pair.first + ", score=" + pair.second);
                        }
                        System.out.println("----------------------------------------------");
                        for (int t = 0; t < Math.min(5, tokenScoreListNew.size()); t++) {
                            Pair<Integer, Double> pair = tokenScoreListNew.get(t);
                            System.out.println(
                                    "Top " + (t + 1) + " new: token=" + tokens_list.get(pair.first) + ", index="
                                            + pair.first + ", score=" + pair.second);
                        }
                        System.out.println("----------------------------------------------");
                        for (int t = 0; t < Math.min(5, tokenScoreListContinue.size()); t++) {
                            Pair<List<Integer>, Double> pair = tokenScoreListContinue.get(t);
                            String tokenStr = pair.first.stream().map(idx -> tokens_list.get(idx))
                                    .collect(Collectors.joining(""));
                            System.out
                                    .println("Top " + (t + 1) + " continue: token=" + tokenStr + ", score="
                                            + pair.second);
                        }
                        System.out.println("----------------------------------------------");
                        // Si phrase finis
                        if (tokenScoreListNew.get(0).first == sentence_end_index && (tokenScoreListContinue.isEmpty()
                                || tokenScoreListNew.get(0).second > tokenScoreListContinue.get(0).second)) {
                            if (i >= MIN_NUMBER_WORD) {
                                System.out.println("sentence end reached");
                                try {

                                    String[] split_word = tokenizedStringSentence.trim().split(" ");
                                    sm.withNewState(() -> {

                                        assert split_word.length == i;
                                        for (int j = 0; j < i; j++) {
                                            word_index[j].assign(words.indexOf(" " + split_word[j].replace(",", "")));
                                        }
                                        // erreur ici word_index[i] pas encore créer au pire pas besoin d'assigner de
                                        // point et juste le add a current sentence a la fin en gros commenter la ligne
                                        // en bas
                                        word_index[i].assign(final_sentence_end);
                                        cp.fixPoint();

                                    });
                                    current_sentence += words.get(corpusDomains.get(final_sentence_end));

                                    // List<Integer> list_sub = new ArrayList<>(
                                    //         corpusDomainToIndex.get(final_sentence_end));
                                    // list_sub.removeAll(last_word);
                                    // tokens_used[num_tok] = tokens_list.get(list_sub.get(0));

                                    while (cp.getConstraints().getStack().size() > constraintSize) {
                                        int size = cp.getConstraints().getStack().size();
                                        cp.getConstraints().getStack().remove(size - 1);
                                    }
                                    break;

                                } catch (Exception e) {

                                    sm.restoreState();
                                    while (cp.getConstraints().getStack().size() > constraintSize) {
                                        int size = cp.getConstraints().getStack().size();
                                        cp.getConstraints().getStack().remove(size - 1);
                                    }

                                    tokenScoreListNew.remove(0);
                                    System.out.println("Not able to end sentence yet, continuing");
                                    e.printStackTrace();
                                    System.out.println(e instanceof InconsistencyException);
                                }
                            } else {
                                System.out.println(
                                        "Sentence end token is the most probable but minimum number of words not reached, continuing");
                                tokenScoreListNew.remove(0);
                            }
                        } else {
                            Iterator<Pair<Integer, Double>> iterator = tokenScoreListNew.iterator();

                            while (iterator.hasNext()) {
                                Pair<Integer, Double> item = iterator.next();

                                if (item.first == sentence_end_index) {
                                    iterator.remove();
                                    break;
                                }
                            }
                        }

                        int limitNew = Math.min(ORACLE_TOP_K, tokenScoreListNew.size());
                        int limitContinue = Math.min(ORACLE_TOP_K, tokenScoreListContinue.size());

                        for (int l = 0; l < limitNew; l++) {
                            int token = tokenScoreListNew.get(l).first;
                            double score = tokenScoreListNew.get(l).second;
                            total_tokens_score_new += score;
                            int[] token_indexs = corpusDomainsSet.get(List.of(token)).stream()
                                    .mapToInt(Integer::intValue)
                                    .toArray();

                            for (int token_index : token_indexs) {
                                tokensNew[token_index] = token_index;
                                scoresNew[token_index] = score;
                                total_score_new += score;
                            }
                            if (PRINT_TRACE) {
                                if (score > max_score_new) {
                                    System.out.println("New max score found: " + score);
                                    System.out.println("Token Strong: " + tokens_list.get(token));
                                    System.out.println("Token: " + token);
                                    System.out.println("Corpus indexs: " + Arrays.toString(token_indexs));
                                    // for (int idx : token_indexs) {
                                    // System.out.println("Corresponding word: " + words.get(idx));
                                    // }
                                    max_score_new = score;
                                    max_token_new = token_indexs[0];
                                }
                            }
                        }
                        // Normalization des scores
                        for (int j = 0; j < tokensNew.length; j++) {
                            double score = scoresNew[j];
                            if (score > 0) {
                                scoresNew[j] /= total_score_new;
                            } else if (score < 0) {
                                throw new RuntimeException("Score is negative or zero");
                            }
                        }
                        // System.out.println("total_score_new: " + total_score_new);
                        // System.out.println("total_tokens_score_new: " + total_tokens_score_new);
                        max_score_new /= total_score_new;

                        for (int l = 0; l < limitContinue; l++) {
                            List<Integer> tokens = tokenScoreListContinue.get(l).first;
                            double score = tokenScoreListContinue.get(l).second;
                            total_tokens_score_continue += score;
                            int[] token_indexs = corpusDomainsSet.get(tokens).stream().mapToInt(Integer::intValue)
                                    .toArray();
                            for (int token_index : token_indexs) {
                                tokensContinue[token_index] = token_index;
                                scoresContinue[token_index] = score;
                                total_score_continue += score;
                            }
                            if (PRINT_TRACE) {
                                if (score > max_score_continue) {
                                    max_score_continue = score;
                                    max_token_continue = token_indexs[0];
                                }
                            }
                        }

                        // Normalization des scores
                        // System.out.println("Total_score_continue: " + total_score_continue);
                        // System.out.println("total_tokens_score_continue: " +
                        // total_tokens_score_continue);
                        for (int j = 0; j < tokensContinue.length; j++) {
                            double score = scoresContinue[j];
                            if (score > 0) {
                                scoresContinue[j] /= total_score_continue;
                            } else if (score < 0) {
                                throw new RuntimeException("Score is negative or zero");
                            }
                        }
                        max_score_continue /= total_score_continue;

                        Map<Integer, Double> marginalsMap = new HashMap<>();
                        double total_score = total_score_new + total_score_continue;
                        double total_tokens_score = total_tokens_score_new + total_tokens_score_continue;
                        // System.out.println("total_score: " + total_score);
                        double ratio_continue = total_tokens_score_continue / total_tokens_score;
                        System.out.println("ratio_continue: " + ratio_continue);
                        double ratio = total_tokens_score_new / total_tokens_score;

                        // CPBP pour les tokens de continuation
                        if (total_score_continue > 0) {

                            // Save pour revenir en arriere et apres faire le CPBP des nouveaux tokens

                            // assign the words in the current sentence except the last one
                            System.out.println("----------------------------------------------");
                            System.out.println("Processing continuing tokens");

                            String[] split_word = tokenizedStringSentence.trim().split(" ");

                            sm.withNewState(() -> {
                                boolean skip = false;
                                for (int j = 0; j < split_word.length - 1; j++) {
                                    try {
                                        // Assignation des mots terminé donc pas le dernier
                                        word_index[j].assign(words.indexOf(" " + split_word[j].replace(",", "")));
                                    } catch (InconsistencyException e) {
                                        System.out
                                                .println("Inconsistency detected with continue tokens, state restored");
                                        skip = true;
                                        break;
                                    }
                                }
                                if (!skip) {
                                    // create and post oracle constraint with continue tokens and scores

                                    // le domaine de word index en haut est de 0 a corpusDomains.size() -1 donc sur
                                    // les mots mais la il est sur les tokens
                                    // Donc ici on a des marginale au niveau des tokens a cause de l'oracle
                                    Constraint c = Factory.oracle(word_index[i - 1], tokensContinue, scoresContinue);
                                    c.setWeight(w);
                                    cp.post(c);

                                    try {
                                        cp.fixPoint();
                                        if (PRINT_TRACE) {
                                            TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                                            for (int j = 0; j < word_index[i].size(); j++) {
                                                bestTokens.put(word_index[i - 1].marginal(j), j);
                                            }
                                            for (int j = 0; j < 5; j++) {
                                                if (bestTokens.isEmpty()) {
                                                    break;
                                                }
                                                double prob = bestTokens.lastKey();
                                                int token = bestTokens.remove(prob);
                                                System.out
                                                        .println(
                                                                "CP model, before BP (max token, 'the word', its probability) "
                                                                        + token + ", '" + words.get(token) + "', "
                                                                        + prob);
                                            }
                                        }
                                        cp.vanillaBP(NUM_PB);
                                        if (PRINT_TRACE) {
                                            TreeMap<Double, Integer> bestTokens = new TreeMap<Double, Integer>();
                                            for (int j = 0; j < word_index[i].size(); j++) {
                                                bestTokens.put(word_index[i - 1].marginal(j), j);
                                            }
                                            for (int j = 0; j < 5; j++) {
                                                if (bestTokens.isEmpty()) {
                                                    break;
                                                }
                                                double prob = bestTokens.lastKey();
                                                int token = bestTokens.remove(prob);
                                                System.out.println(
                                                        "after BP (max token, 'the word', its probability) " + token
                                                                + ", '"
                                                                + words.get(token) + "', " + prob);
                                            }
                                        }

                                    } catch (InconsistencyException e) {
                                        System.out.println("Sentence so far: " + Arrays.toString(split_word));
                                        System.out
                                                .println("Inconsistency detected with continue tokens, state restored");
                                        throw e;
                                    }
                                    // et a partir d'ici on a des marginales sur les tokens moyenné
                                    Map<Integer, List<Double>> tempMap = new HashMap<>();
                                    while (word_index[i - 1].maxMarginal() != 0.0) {
                                        List<Integer> word_indexes = new ArrayList<>(
                                                corpusDomainToIndex.get(word_index[i - 1].valueWithMaxMarginal()));
                                        int index;
                                        word_indexes.removeAll(last_word);
                                        if (word_indexes.isEmpty())
                                            index = last_word.get(last_word.size() - 1);// TODO: verify if this is ok
                                        else
                                            index = word_indexes.get(0);
                                        if (!tempMap.containsKey(index)) {
                                            tempMap.put(index, new ArrayList<>());
                                        }
                                        tempMap.get(index).add(word_index[i - 1].maxMarginal() * ratio_continue);
                                        word_index[i - 1].remove(word_index[i - 1].valueWithMaxMarginal());
                                    }
                                    for (Entry<Integer, List<Double>> entry : tempMap.entrySet()) {// TODO : Consider if
                                                                                                   // max
                                                                                                   // would
                                                                                                   // be more
                                                                                                   // interesting
                                                                                                   // than
                                                                                                   // average
                                        double marginalAverage = 0.0;
                                        for (double val : entry.getValue()) {
                                            marginalAverage += val;
                                        }
                                        marginalAverage /= entry.getValue().size();
                                        marginalsMap.put(entry.getKey(), marginalAverage);
                                    }

                                }
                            });

                            while (cp.getConstraints().getStack().size() > constraintSize) {
                                int size = cp.getConstraints().getStack().size();
                                cp.getConstraints().getStack().remove(size - 1);
                            }
                        } else {
                            System.out.println("No continuing tokens to process");
                        }

                        String last_word_string = last_word.stream()
                                .map(idx -> tokens_list.get(idx))
                                .collect(Collectors.joining("")).replace(".", "").replace(",", "");
                        System.out.println("Last word string: '" + last_word_string + "'");
                        System.out.println("Words contains last word: " + words.contains(last_word_string));

                        if (words.contains(last_word_string) && total_score_new > 0) {

                            // CPBP pour les nouveaux tokens
                            System.out.println("----------------------------------------------");
                            System.out.println("Processing new tokens");
                            String[] split_word = tokenizedStringSentence.trim().split(" ");
                            boolean[] isError = { false };
                            final int maxTokenNewFinal = max_token_new;
                            final double maxScoreNewFinal = max_score_new;
                            sm.withNewState(() -> {

                                boolean skip = false;
                                for (int j = 0; j < split_word.length; j++) {
                                    try {
                                        // Assignation de tous les mots deja dans la phrase
                                        word_index[j].assign(words.indexOf(" " + split_word[j].replace(",", "")));
                                    } catch (InconsistencyException e) {
                                        System.out.println("Sentence so far: " + Arrays.toString(split_word));
                                        System.out.println(
                                                "Inconsistency caused by : " + split_word[j] + ", index: " + j);
                                        System.out
                                                .println("Words contains word : " + words.indexOf(" " + split_word[j]));
                                        System.out.println("Inconsistency detected with new tokens, state restored");
                                        for (int jj = 0; jj < j; jj++) {
                                            System.out.println(word_index[jj].getName() + word_index[jj].toString());
                                        }
                                        skip = true;
                                        break;
                                    }
                                }
                                if (!skip) {
                                    if (PRINT_TRACE)
                                        System.out.println("token " + i);

                                    Constraint c = Factory.oracle(word_index[i], tokensNew, scoresNew);

                                    c.setWeight(w);
                                    if (PRINT_TRACE)
                                        System.out.println("oracle's weight set to " + w);
                                    cp.post(c);
                                    if (PRINT_TRACE)
                                        System.out.println("GPT, before BP (max token, 'the word', its probability) "
                                                + maxTokenNewFinal + ", '" + words.get(maxTokenNewFinal) + "', "
                                                + maxScoreNewFinal);
                                    // if (PRINT_TRACE) {
                                    // double[] temp = scoresNew.clone();
                                    // Arrays.sort(temp);
                                    // for (int n = 1; n <= 1; n++) {
                                    // for (int m = 0; m < temp.length; m++) {
                                    // if (temp[temp.length - n] == scoresNew[m]) {
                                    // System.out.println("GPT, before BP (max token, 'the word', its probability) "
                                    // + m + ", '" + words.get(m) + "', " + scoresNew[m]);
                                    // }
                                    // }
                                    // }
                                    // }

                                    try {
                                        cp.fixPoint();
                                    } catch (InconsistencyException e) {
                                        if (PRINT_TRACE) {
                                            System.out.println("INCONSISTENCY!");
                                            for (int j = 0; j < word_index.length; j++) {
                                                System.out.println(word_index[j].getName() + word_index[j].toString());
                                            }
                                        }
                                        isError[0] = true;
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
                                            System.out
                                                    .println(
                                                            "CP model, before BP (max token, 'the word', its probability) "
                                                                    + token + ", '" + words.get(token) + "', " + prob);
                                        }
                                    }

                                    if (PRINT_TRACE)
                                        System.out
                                                .println("CP model, before BP (max token, 'the word', its probability) "
                                                        + word_index[i].valueWithMaxMarginal() + ", '"
                                                        + words.get(word_index[i].valueWithMaxMarginal()) + "', "
                                                        + word_index[i].maxMarginal());
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
                                            System.out.println(
                                                    "after BP (max token, 'the word', its probability) " + token + ", '"
                                                            + words.get(token) + "', " + prob);
                                        }
                                    }

                                    if (word_index[i].maxMarginal() == 0.0) {
                                        System.out.println("No valid tokens found");
                                        isError[0] = true;
                                    }

                                    // Meme chose qu'en haut accumuler marginale plus moyenner
                                    Map<Integer, List<Double>> tempMap = new HashMap<>();
                                    while (word_index[i].maxMarginal() != 0.0) {
                                        double marginal = word_index[i].maxMarginal() * ratio;
                                        List<Integer> word_indexes = new ArrayList<>(
                                                corpusDomainToIndex.get(word_index[i].valueWithMaxMarginal()));
                                        int index = word_indexes.get(0);
                                        if (!tempMap.containsKey(index)) {
                                            tempMap.put(index, new ArrayList<>());
                                        }
                                        tempMap.get(index).add(marginal);
                                        word_index[i].remove(word_index[i].valueWithMaxMarginal());
                                    }

                                    for (Entry<Integer, List<Double>> entry : tempMap.entrySet()) {// TODO : Consider if
                                                                                                   // max
                                                                                                   // would
                                                                                                   // be more
                                                                                                   // interesting
                                                                                                   // than
                                                                                                   // average
                                        double marginalAverage = 0.0;
                                        for (double val : entry.getValue()) {
                                            marginalAverage += val;
                                        }
                                        marginalAverage /= entry.getValue().size();
                                        marginalsMap.put(entry.getKey(), marginalAverage);
                                    }
                                }
                            });
                            if (isError[0]) {
                                current_sentence += " ERROR";
                                while (cp.getConstraints().getStack().size() > constraintSize) {
                                    int size = cp.getConstraints().getStack().size();
                                    cp.getConstraints().getStack().remove(size - 1);
                                }
                                break;
                            } else {

                                while (cp.getConstraints().getStack().size() > constraintSize) {
                                    int size = cp.getConstraints().getStack().size();
                                    cp.getConstraints().getStack().remove(size - 1);
                                }
                            }

                        }
                        int chosen = -1;

                        Random random = new Random();
                        double cumulativeProbability = 0.0;

                        double sum = marginalsMap.values().stream().mapToDouble(Double::doubleValue).sum();
                        double randomValue = random.nextDouble() * sum;
                        System.out.println("Sum of marginals before normalization: " + sum);
                        if (sum == 0.0) {
                            System.out.println("All marginals are zero, inconsistency detected");
                            current_sentence += " ERROR";
                            break;
                        }
                        for (Map.Entry<Integer, Double> entry : marginalsMap.entrySet()) {
                            entry.setValue(entry.getValue() / sum);
                        }
                        // print le top 5 des marginales
                        if (PRINT_TRACE) {
                            Map<Double, Integer> sortedMarginals = new TreeMap<>(Collections.reverseOrder());
                            for (Map.Entry<Integer, Double> entry : marginalsMap.entrySet()) {
                                sortedMarginals.put(entry.getValue(), entry.getKey());
                            }
                            System.out.println("----------------------------------------------");
                            System.out.println("Top 5 marginals:");
                            int count = 0;
                            for (Map.Entry<Double, Integer> entry : sortedMarginals.entrySet()) {
                                System.out.println(
                                        "Token: " + entry.getValue() + ", '" + tokens_list.get(entry.getValue())
                                                + "', Marginal: " + entry.getKey());
                                count++;
                                if (count >= 5) {
                                    break;
                                }
                            }
                            System.out.println("----------------------------------------------");
                        }
                        for (Map.Entry<Integer, Double> entry : marginalsMap.entrySet()) {
                            cumulativeProbability += entry.getValue();
                            if (randomValue <= cumulativeProbability) {
                                chosen = entry.getKey();
                                if (chosen == -1)
                                    continue;
                                break;
                            }
                        }
                        if (chosen == -1) {
                            throw new Exception("No token chosen, inconsistency detected");
                        }
                        // Numtok ne dvrait il pas etre numword? puisque ca ++ seulement si ya un espace
                        if (tokens_list.get(chosen).startsWith(" ")) {
                            num_tok += 1;
                        }
                        if (i > MAX_NUMBER_WORD) {

                            // Enlever les ancienne variables ajouté au solveur au debut de la génération
                            for (int j = 0; j < word_index.length + isWordIndexStartWith.length; j++) {
                                cp.getVariables().pop();
                            }
                            // Vider aussi les contraintes ajouter au début de la boucle de génération
                            while (!cp.getConstraints().getStack().isEmpty()) {
                                cp.getConstraints().pop();
                            }
                            break;
                        } else {
                            System.out.println(
                                    "chosen: " + chosen + ", '" + tokens_list.get(chosen) + "', "
                                            + marginalsMap.get(chosen));
                            tokenizedStringSentence += tokens_list.get(chosen);

                            String idToDecode = "{\"id\": " + chosen + "}";
                            HttpRequest decodeRequest = HttpRequest.newBuilder()
                                    .uri(URI.create("http://localhost:" + port + "/decode_id"))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(idToDecode))
                                    .build();
                            String decodedResponse = client.sendAsync(decodeRequest, BodyHandlers.ofString())
                                    .thenApply(HttpResponse::body)
                                    .join();

                            JsonNode jsonNodeDecode = mapper.readTree(decodedResponse);
                            ArrayNode DecodedToken = (ArrayNode) jsonNodeDecode.get("token");
                            String decodedTokenStr = DecodedToken.get(0).asText();
                            System.out.println("Decoded token: " + decodedTokenStr + "");
                            if (llm_name.equals("Mistral") && !decodedTokenStr.startsWith(" ")
                                    && tokens_list.get(chosen).startsWith(" ")) {
                                decodedTokenStr = " " + decodedTokenStr;
                            }
                            current_sentence += decodedTokenStr;
                            // tokens_used[num_tok] += ", " + tokens_list.get(chosen);

                            if (PRINT_TRACE) {
                                System.out.println("sentence so far: " + current_sentence);
                                System.out.println("index chosen: " + chosen);
                            }

                            // Enlever les ancienne variables ajouté au solveur au debut de la génération
                            for (int j = 0; j < word_index.length + isWordIndexStartWith.length; j++) {
                                cp.getVariables().pop();
                            }
                            // Vider aussi les contraintes ajouter au début de la boucle de génération
                            while (!cp.getConstraints().getStack().isEmpty()) {
                                cp.getConstraints().pop();
                            }
                            // MPuisque choosen est ajouté a la phrase, la phrase est envoye au llm et tout
                            // les word index sont réassignés a chaque fois ca marche
                        }

                    }
                    if (!current_sentence.trim().endsWith("ERROR") && !current_sentence.trim().endsWith(".")) {
                        current_sentence = current_sentence.trim() + ".";
                    }
                    current_sentence=(current_sentence.charAt(0)+"").toUpperCase() + current_sentence.substring(1); 
                    num_phrase++;
                    solution += current_sentence + "\n";

                }

                double perplexityScore = 0;

                System.out.println("solution : " + solution);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/tokenize"))
                        .POST(HttpRequest.BodyPublishers.ofString(solution))
                        .build();
                String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body)
                        .join();
                int[] split_response = Arrays.stream(response.substring(1, response.length() - 2).split(","))
                        .mapToInt(Integer::parseInt).toArray();
                int[] tokens = Arrays.copyOfRange(split_response, 1, split_response.length);

                logs.add(new Logging(solution, perplexityScore, tokens));

            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("port", port);
            result.put("num_iterations", NUM_ITERATIONS);
            result.put("num_pb", NUM_PB);
            result.put("weight", wArray);
            result.put("llm_name", llm_name);
            result.put("logs", logs);
            result.put("date", java.time.LocalDateTime.now().toString());
            String OUTPUT_DIR = "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_Orthophonie_NLP_v5_" + llm_name + System.currentTimeMillis()
                    + ".json";
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get(outputFileName).toFile(), result);
        } catch (

        Exception e) {
            e.printStackTrace();
            // Write error to output file
            String OUTPUT_DIR = args.length > 3 ? args[2] : "./outputs";
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            String outputFileName = OUTPUT_DIR + "/result_Orthophonie_NLP_v5_" + System.currentTimeMillis()

                    + "_error.json";
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("status", "error");
            errorResult.put("error_message", e.getMessage());
            errorResult.put("exception", e.toString());
            errorResult.put("date", java.time.LocalDateTime.now().toString());
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
        // public String[] tokens_used;

        public Logging() {
        }

        public Logging(String sentence, double perplexityScore, int[] tokens/* , String[] tokens_used */) {
            this.sentence = sentence;
            this.perplexity = perplexityScore;
            this.tokens = tokens;
            // this.tokens_used = tokens_used;
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

        public List<Integer> parseIPAToPhonemeIds(String ipa) {
            List<Integer> result = new ArrayList<>();
            // L'IPA a les phonèmes séparés par des espaces
            String[] phonemes = ipa.split(" ");

            for (String phonemeWithModifiers : phonemes) {
                if (phonemeWithModifiers.isEmpty()) {
                    continue;
                }

                // Extraire le phonème de base (premier caractère) et ignorer les modificateurs
                // Par exemple: "bˤ" -> on prend "b"
                String basePhoneme = String.valueOf(phonemeWithModifiers.charAt(0));

                if (phonemeToId.containsKey(basePhoneme)) {
                    result.add(phonemeToId.get(basePhoneme));
                } else {
                    // Si le phonème simple n'existe pas, essayer avec le phonème complet
                    if (phonemeToId.containsKey(phonemeWithModifiers)) {
                        result.add(phonemeToId.get(phonemeWithModifiers));
                    }
                    // Sinon, ignorer ce phonème
                    System.out.println("Phonème non trouvé: '" + phonemeWithModifiers + "'");
                }
            }

            return result;
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
