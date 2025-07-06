import org.chernovia.acro.AcroBot;
import org.chernovia.acro.AcroGame;
import org.chernovia.acro.AcroServ;

import java.util.concurrent.ThreadLocalRandom;

public class AcroTest {
    public static void main(String[] args) throws Exception {
        AcroServ.loadWords();
        AcroGame game = new AcroGame("Test Game",null,null, AcroServ.DEF_LETTER_FILE, 1,1,1,false,false);
        for (int i = 0; i < 1000; i++) {
            game.makeAcro(ThreadLocalRandom.current().nextInt(3,8));
            System.out.println(game.getCurrentAcro() + " -> " + AcroBot.generateSafeStructuredAcro(game.getCurrentAcro()));
        }
    }
}
