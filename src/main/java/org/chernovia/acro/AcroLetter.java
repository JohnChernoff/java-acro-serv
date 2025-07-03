package org.chernovia.acro;

import org.chernovia.lib.zugserv.ZugManager;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class AcroLetter {
    static String LETTEXT = ".ltr";
    int prob; String c;
    public AcroLetter(String s, int p) { c = s; prob = p; }

    public static List<AcroLetter> loadABC(String ABCFILE) {
        List<AcroLetter> alphabet = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader("res/" + ABCFILE + LETTEXT))) {
            while(in.ready()) {
                StringTokenizer S = new StringTokenizer(in.readLine());
                if (S.countTokens() == 2) alphabet.add(new AcroLetter(S.nextToken(), Integer.parseInt(S.nextToken())));
            }
        }
        catch (IOException e) {
            ZugManager.log("Error loading " + ABCFILE + ": " + e.getMessage()); return null;
        }
        return alphabet;
    }
}
