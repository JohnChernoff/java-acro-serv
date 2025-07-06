package org.chernovia.acro;

public class AcroTopic {
    int id;
    String topic, category;
    int minLetters,maxLetters;

    public AcroTopic(int id, String topic, String category, int minLetters, int maxLetters) {
        this.id = id;
        this.topic = topic;
        this.category = category;
        this.minLetters = minLetters;
        this.maxLetters = maxLetters;
    }

    @Override
    public String toString() {
        return topic + " " + category + " " + minLetters + " " + maxLetters;
    }
}
