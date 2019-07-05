import java.util.HashMap;
import java.util.Map;

public class Trie {
    public Node root = new Node('^', 0.0);

    public class Node {
        Node(char symbol, double maxP) {
            this.symbol = symbol;
            this.maxP = maxP;
        }
        char symbol;
        double maxP;
        Map<Character, Node> children = new HashMap<>();
        String word = null;
    }

    void addWord(String word, double p) {
        Trie.Node curr = root;
        for (int i = 1; i < word.length(); i++) {
            if (curr.maxP < p) {
                curr.maxP = p;
            }
            if (curr.children.containsKey(word.charAt(i))) {
                curr = curr.children.get(word.charAt(i));
            } else {
                Trie.Node child = new Trie.Node(word.charAt(i), p);
                curr.children.put(word.charAt(i), child);
                curr = child;
            }
        }
        curr.word = word;
    }
}
