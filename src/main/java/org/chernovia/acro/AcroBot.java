package org.chernovia.acro;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chernovia.lib.zugserv.ZugManager;
import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AcroBot {

    static boolean DEBUG = false;

    static class PhrasalVerb {
        String verb;      // e.g. "look"
        String particle;  // e.g. "up"

        PhrasalVerb(String verb, String particle) {
            this.verb = verb;
            this.particle = particle;
        }

        String getInitials() {
            return ("" + verb.charAt(0) + particle.charAt(0)).toUpperCase();
        }

        String asString() {
            return verb + " " + particle;
        }

        String conjugate(VerbTense tense) {
            return conjugateVerb(verb, tense) + " " + particle;
        }
    }

    enum VerbTense {
        BASE, THIRD_PERSON, PAST, GERUND
    }

    static String[][] posPatterns = {
            // 3-letter acronyms (all single words)
            { "adj", "noun", "verb" },              // 1+1+1 = 3 letters

            // 4-letter acronyms
            { "noun", "phrasal_verb", "noun" },    // 1+2+1 = 4 letters
            { "adj", "phrasal_verb", "verb" },     // 1+2+1 = 4 letters
            { "noun", "verb", "noun", "noun" },    // 1+1+1+1 = 4 letters

            // 5-letter acronyms
            { "noun", "verb", "noun", "verb", "noun" },       // 1+1+1+1+1 = 5 letters
            { "adj", "noun", "phrasal_verb", "noun" },        // 1+1+2+1 = 5 letters
            { "noun", "phrasal_verb", "verb", "noun" },       // 1+2+1+1 = 5 letters

            // 6-letter acronyms
            { "noun", "verb", "noun", "verb", "adj", "noun" },    // 6 single words
            { "adj", "noun", "phrasal_verb", "verb", "noun" },    // 1+1+2+1+1=6 letters
            { "phrasal_verb", "noun", "verb", "noun", "verb" },   // 2+1+1+1+1=6 letters

            // 7-letter acronyms
            { "adj", "noun", "verb", "adj", "noun", "verb", "noun" },  // all single words
            { "noun", "phrasal_verb", "adv", "adj", "noun", "verb" }   // 1+2+1+1+1+1=7 letters
    };


    // Map of two-letter keys (like "LU") to list of phrasal verbs
    static Map<String, List<PhrasalVerb>> phrasalVerbsByInitials = new HashMap<>();

    private static Map<Character, Map<String, List<String>>> words;
    private static Map<Character, Map<String, List<String>>> corporaWords = new HashMap<>();

    private static final Set<String> IRREGULAR_PAST = Set.of("ran", "ate", "slept", "fell", "spoke");
    private static final Map<String, String> IRREGULAR_THIRD = Map.of(
            "have", "has",
            "do", "does",
            "go", "goes"
    );
    private static final Map<String, String> IRREGULAR_PAST_MAP = Map.of(
            "run", "ran",
            "eat", "ate",
            "sleep", "slept",
            "fall", "fell",
            "speak", "spoke"
    );

    private static String conjugateVerb(String verb, VerbTense tense) {
        switch (tense) {
            case THIRD_PERSON:
                if (IRREGULAR_THIRD.containsKey(verb)) return IRREGULAR_THIRD.get(verb);
                if (verb.endsWith("y") && !isVowel(verb.charAt(verb.length() - 2))) {
                    return verb.substring(0, verb.length() - 1) + "ies";
                } else if (verb.endsWith("s") || verb.endsWith("sh") || verb.endsWith("ch") || verb.endsWith("x") || verb.endsWith("z")) {
                    return verb + "es";
                } else return verb + "s";
            case PAST:
                return IRREGULAR_PAST_MAP.getOrDefault(verb,
                        verb.endsWith("e") ? verb + "d" : verb + "ed");
            case GERUND:
                return verb.endsWith("e") && !verb.endsWith("ee") ? verb.substring(0, verb.length() - 1) + "ing" : verb + "ing";
            case BASE:
            default:
                return verb;
        }
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) != -1;
    }

    public static void loadPhrasalVerbs(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> phrasalList = mapper.readValue(new File(path), List.class);

        phrasalVerbsByInitials.clear();

        for (Map<String, String> entry : phrasalList) {
            String verb = entry.get("verb");
            String particle = entry.get("particle");

            if (verb == null || particle == null) continue;

            PhrasalVerb pv = new PhrasalVerb(verb, particle);
            String initials = pv.getInitials();

            phrasalVerbsByInitials.computeIfAbsent(initials, k -> new ArrayList<>()).add(pv);
        }

        ZugManager.log(Level.INFO,"✅ Loaded " + phrasalList.size() + " phrasal verbs");
    }

    public static void loadCorporaWordList(String path, String jsonKey, String pos) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<String>> data = mapper.readValue(new File(path),
                new TypeReference<>() {});

        List<String> wordList = data.get(jsonKey);
        if (wordList == null) {
            ZugManager.log(Level.FINE,"❌ No key '" + jsonKey + "' in " + path);
            return;
        }

        for (String word : wordList) {
            if (word == null || word.isEmpty()) continue;
            char firstChar = Character.toUpperCase(word.charAt(0));
            corporaWords.computeIfAbsent(firstChar, k -> new HashMap<>())
                    .computeIfAbsent(pos, k -> new ArrayList<>())
                    .add(word.toLowerCase());
        }

        ZugManager.log(Level.INFO,"✅ Loaded " + wordList.size() + " " + pos + "s from " + path);
    }

    public static void loadWords(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // List<String> type
        JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        // Map<String, List<String>> type
        JavaType stringToListType = mapper.getTypeFactory().constructMapType(Map.class,
                mapper.getTypeFactory().constructType(String.class), listType);
        // Map<Character, Map<String, List<String>>> type
        JavaType charToMapType = mapper.getTypeFactory().constructMapType(Map.class,
                mapper.getTypeFactory().constructType(Character.class), stringToListType);
        words = mapper.readValue(new File(path), charToMapType);
    }

    public static String generateStructuredAcro(String letters) {
        List<String[]> matchingPatterns = getMatchingPatterns(letters.length());

        if (matchingPatterns.isEmpty()) {
            ZugManager.log(Level.INFO,"❌ No POS pattern for length " + letters.length());
            return fallbackAcro(letters);
        }

        String[] pattern = matchingPatterns.get(new Random().nextInt(matchingPatterns.size()));
        return generateStructuredAcro(letters, pattern);
    }

    public static String generateSafeStructuredAcro(String letters) {
        int maxAttempts = 5;

        for (int i = 0; i < maxAttempts; i++) {
            String[] pattern = getRandomPatternForLength(letters.length(),i < 2);
            if (pattern == null) break;

            String sentence = generateStructuredAcro(letters, pattern);

            boolean isValid = !sentence.contains("[") &&
                    sentence.split(" ").length >= letters.length(); // phrasal verbs may use fewer words

            if (isValid) return sentence;
        }

        // Final fallback
        return fallbackAcro(letters);
    }

    private static String[] getRandomPatternForLength(int length, boolean phrasal) {
        List<String[]> matches = Arrays.stream(posPatterns)
                .filter(p -> {
                    int cost = 0;
                    for (String pos : p) {
                        cost += "phrasal_verb".equals(pos) ? 2 : 1;
                    }
                    return cost == length && (!phrasal ||
                            Arrays.stream(p).noneMatch(p2 -> Objects.equals(p2, "phrasal_verb")));
                })
                .toList();

        if (matches.isEmpty()) return null;
        return matches.get(new Random().nextInt(matches.size()));
    }

    private static String fallbackAcro(String letters) {
        return Arrays.stream(letters.split(""))
                .map(c -> "[" + c + "]")
                .reduce("", (a, b) -> a + " " + b).trim();
    }

    private static int patternLetterCount(String[] pattern) {
        int count = 0;
        for (String pos : pattern) {
            count += "phrasal_verb".equals(pos) ? 2 : 1;
        }
        return count;
    }

    public static List<String[]> getMatchingPatterns(int acronymLength) {
        return Arrays.stream(posPatterns)
                .filter(p -> patternLetterCount(p) == acronymLength)
                .collect(Collectors.toList());
    }

    private static List<String> getPreferredCandidates(char c, String pos) {
        List<String> corpusList = corporaWords
                .getOrDefault(c, Map.of())
                .getOrDefault(pos, List.of());
        List<String> backupList = words
                .getOrDefault(c, Map.of())
                .getOrDefault(pos, List.of());

        if (!corpusList.isEmpty() && (backupList.isEmpty() || Math.random() < 0.8)) {
            return corpusList;
        } else {
            return backupList;
        }
    }

    public static String generateStructuredAcro(String letters, String[] posPattern) {
        StringBuilder sb = new StringBuilder();
        Random rand = new Random();
        int letterIndex = 0;

        for (int posIndex = 0; posIndex < posPattern.length; posIndex++) {
            if (letterIndex >= letters.length()) {
                ZugManager.log(Level.FINE,"⚠️ Ran out of letters at POS index " + posIndex);
                return fallbackAcro(letters);
            }

            String pos = posPattern[posIndex];

            if ("phrasal_verb".equals(pos)) {
                if (letterIndex + 1 >= letters.length()) {
                    ZugManager.log(Level.FINE,"⚠️ Not enough letters for phrasal verb at index " + letterIndex);
                    return fallbackAcro(letters);
                }

                char c1 = Character.toUpperCase(letters.charAt(letterIndex));
                char c2 = Character.toUpperCase(letters.charAt(letterIndex + 1));
                String initials = "" + c1 + c2;

                List<PhrasalVerb> phrasals = phrasalVerbsByInitials.getOrDefault(initials, List.of());

                if (!phrasals.isEmpty()) {
                    PhrasalVerb pv = phrasals.get(rand.nextInt(phrasals.size()));
                    VerbTense tense = VerbTense.values()[rand.nextInt(VerbTense.values().length)];
                    String phrase = pv.conjugate(tense);

                    sb.append(sb.isEmpty() ? capitalize(phrase) : phrase).append(" ");
                    letterIndex += 2;
                    continue;
                } else {
                    ZugManager.log(Level.FINE,"⚠️ No phrasal verb found for initials " + initials);
                    return fallbackAcro(letters);
                }
            }

            char c = Character.toUpperCase(letters.charAt(letterIndex));
            List<String> candidates = getPreferredCandidates(c, pos);

            // If no words for letter+POS, try any word for that letter
            if (candidates.isEmpty()) {
                ZugManager.log(Level.FINE,"⚠️ No [" + pos + "] for '" + c + "' — trying all POS for that letter");
                Map<String, List<String>> allForLetter = corporaWords.containsKey(c)
                        ? corporaWords.get(c)
                        : words.getOrDefault(c, Map.of());

                candidates = allForLetter.values().stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
            }

            // If still empty, pick any random word from anywhere
            if (candidates.isEmpty()) {
                ZugManager.log(Level.FINE,"❌ Still no candidates for '" + c + "' — trying global fallback pool");
                candidates = corporaWords.values().stream()
                        .flatMap(posMap -> posMap.values().stream())
                        .flatMap(List::stream)
                        .collect(Collectors.toList());

                if (candidates.isEmpty()) {
                    ZugManager.log(Level.FINE,"❌ No words at all — inserting placeholder");
                    sb.append("[").append(c).append("] ");
                    letterIndex++;
                    continue;
                }
            }

            // Choose a word
            String word = candidates.get(rand.nextInt(candidates.size()));

            // Conjugate verbs
            if ("verb".equals(pos)) {
                VerbTense tense = VerbTense.values()[rand.nextInt(VerbTense.values().length)];
                word = conjugateVerb(word, tense);
            }

            sb.append(sb.isEmpty() ? capitalize(word) : word).append(" ");
            letterIndex++;
        }

        // Final safety check
        if (letterIndex != letters.length()) {
            ZugManager.log(Level.FINE,"⚠️ Letter usage mismatch — used " + letterIndex + " of " + letters.length());
            return fallbackAcro(letters);
        }

        return sb.toString().trim() + randomPunctuation(rand);
    }

    private static String randomPunctuation(Random rand) {
        return switch (rand.nextInt(4)) {
            case 0 -> ".";
            case 1 -> "!";
            case 2 -> "?";
            case 3 -> "...";
            default -> "?!";
        };
    }

    private static String capitalize(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }

}
