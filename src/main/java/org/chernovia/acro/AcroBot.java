package org.chernovia.acro;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class AcroBot {
    static String[][] posPatterns = {
            // 3-letter
            { "noun", "verb", "noun" },
            { "adj", "noun", "verb" },
            // 4-letter
            { "adj", "noun", "verb", "noun" },
            { "noun", "verb", "adj", "noun" },
            // 5-letter
            { "noun", "verb", "noun", "verb", "noun" },
            { "adj", "noun", "verb", "adv", "noun" },
            // 6-letter (already there)
            { "noun", "verb", "noun", "verb", "adj", "noun" },
            { "adj", "noun", "verb", "adv", "noun", "verb" },
            { "noun", "verb", "adj", "noun", "verb", "noun" },
            // 7-letter
            { "adj", "noun", "verb", "adj", "noun", "verb", "noun" },
            { "noun", "verb", "adv", "adj", "noun", "verb", "noun" }
    };

    private static Map<Character, Map<String, List<String>>> words;
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
        List<String[]> matchingPatterns = Arrays.stream(posPatterns)
                .filter(p -> p.length == letters.length())
                .toList();

        if (matchingPatterns.isEmpty()) {
            System.err.println("❌ No POS pattern for length " + letters.length());
            return fallbackAcro(letters);
        }

        String[] pattern = matchingPatterns.get(new Random().nextInt(matchingPatterns.size()));
        return generateStructuredAcro(letters, pattern);
    }

    private static String fallbackAcro(String letters) {
        return Arrays.stream(letters.split(""))
                .map(c -> "[" + c + "]")
                .reduce("", (a, b) -> a + " " + b).trim();
    }

    public static String generateStructuredAcro(String letters, String[] posPattern) {
        StringBuilder sb = new StringBuilder();
        Random rand = new Random();

        for (int i = 0; i < letters.length(); i++) {
            char c = Character.toUpperCase(letters.charAt(i));
            String pos = posPattern[i];

            // Step 1: try exact POS match
            List<String> candidates = words
                    .getOrDefault(c, Map.of())
                    .getOrDefault(pos, List.of());

            // Step 2: fallback to any word for that letter
            if (candidates.isEmpty()) {
                Map<String, List<String>> allForLetter = words.getOrDefault(c, Map.of());
                candidates = allForLetter.values().stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toCollection(ArrayList::new));
            }

            // Step 3: use placeholder if still nothing
            String word;
            if (!candidates.isEmpty()) {
                word = candidates.get(rand.nextInt(candidates.size()));
            } else {
                word = "[" + c + "]";
            }

            sb.append(i == 0 ? capitalize(word) : word).append(" ");
        }

        return sb.toString().trim() + ".";
    }


    private static String capitalize(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }
}
