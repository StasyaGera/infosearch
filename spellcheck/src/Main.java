import java.util.*;

import static java.util.Objects.hash;

public class Main {
    static class Edit {
        enum Action {
            DELETE,
            INSERT,
            REPLACE,
            MATCH;

            double prob = 0.0;
        }

        Action action;
        Character prev, from, to;

        Edit(Action action, Character prev, Character from, Character to) {
            this.action = action;
            this.prev = prev;
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return action.toString() + " " + prev + from + " -> " + prev + to;
        }

        @Override
        public int hashCode() {
            return hash(this.action, this.prev, this.from, this.to);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Edit) {
                Edit other = (Edit) obj;
                boolean p, f, t;
                if (this.prev == null) {
                    p = other.prev == null;
                } else {
                    p = this.prev.equals(other.prev);
                }
                if (this.from == null) {
                    f = other.from == null;
                } else {
                    f = this.from.equals(other.from);
                }
                if (this.to == null) {
                    t = other.to == null;
                } else {
                    t = this.to.equals(other.to);
                }
                return this.action == other.action && p && f && t;
            }
            return false;
        }
    }

    private final double alpha = 0.4, beta = 0.3, gamma = 0.3;
    private Trie trie = new Trie();
    private Map<String, Double> languageModel = new HashMap<>();
    private Map<Edit, Double> errorModel = new HashMap<>();

    private List<Edit> edPrescription(String src, String dst) {
        int n = src.length() + 1;
        int m = dst.length() + 1;
        Integer[][] dp = new Integer[n][m];
        for (int i = 0; i < n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j < m; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i < n; i++) {
            for (int j = 1; j < m; j++) {
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + (src.charAt(i - 1) != dst.charAt(j - 1) ? 1 : 0));
            }
        }

        List<Edit> prescription = new ArrayList<>();
        int i = n - 1, j = m - 1;
        while (i > 0 && j > 0) {
            int del = dp[i - 1][j], ins = dp[i][j - 1], repl = dp[i - 1][j - 1];
            Character prev = i == 1 ? null : src.charAt(i - 2);
            if (src.charAt(i - 1) == dst.charAt(j - 1)) {
                prescription.add(new Edit(Edit.Action.MATCH, prev, src.charAt(i - 1), dst.charAt(j - 1)));
                i--;
                j--;
            } else if (del < ins && del < repl) {
                prescription.add(new Edit(Edit.Action.DELETE, prev, src.charAt(i - 1), null));
                i--;
            } else if (ins < del && ins < repl) {
                prescription.add(new Edit(Edit.Action.INSERT, prev, null, dst.charAt(j - 1)));
                j--;
            } else {
                prescription.add(new Edit(Edit.Action.REPLACE, prev, src.charAt(i - 1), dst.charAt(j - 1)));
                i--;
                j--;
            }
        }
        Collections.reverse(prescription);
        return prescription;
    }

    void countLangModel(String filename) {
        final long[] total = {0};
        CSVReader.read(filename, parsed -> {
            total[0] += Integer.parseInt(parsed[1]);
            languageModel.put("^" + parsed[0] + "$", Double.valueOf(parsed[1]));
        });
        languageModel.replaceAll((word, cnt) -> total[0] == 0 ? 0 : cnt / total[0]);
    }

    void countErrModel(String filename) {
        final long[] total = {0};
        CSVReader.read(filename, parsed -> {
            List<Edit> prescription = edPrescription(parsed[0], parsed[1]);
            total[0] += Integer.parseInt(parsed[2]) * prescription.size();
            for (Edit edit: prescription) {
                edit.action.prob += Integer.parseInt(parsed[2]);
                errorModel.compute(edit, (e, cnt) -> (cnt == null) ?
                        Double.valueOf(parsed[2]) :
                        cnt + Double.valueOf(parsed[2]));

                if (edit.prev != null) {
                    Edit nulled = new Edit(edit.action, null, edit.from, edit.to);
                    errorModel.compute(nulled, (e, cnt) -> (cnt == null) ?
                            Double.valueOf(parsed[2]) :
                            cnt + Double.valueOf(parsed[2]));
                }
            }
        });
        errorModel.replaceAll((edit, cnt) -> total[0] == 0 ? 0 : cnt / total[0]);
        for (Edit.Action action: Edit.Action.values()) {
            action.prob = total[0] == 0 ? 0 : action.prob / total[0];
        }
    }

    class Path implements Comparable<Path> {
        Path(int pos, Trie.Node node, double histProb, double prob, int edits) {
            this.pos = pos;
            this.edits = edits;
            this.node = node;
            this.histProb = histProb;
            this.prob = prob;
        }

        int pos, edits;
        Trie.Node node;
        double histProb, prob;

        @Override
        public String toString() {
            return "pos: " + pos + " edits: " + edits;
        }

        @Override
        public int compareTo(Path o) {
            return Double.compare(o.prob, this.prob);
        }
    }

    private List<Edit> getEdits(Path pi, String word, double perc) {
        List<Edit> t = new ArrayList<>();
        double minP = perc * pi.node.maxP;
        Character prev = pi.pos == 0 ? null : word.charAt(pi.pos - 1);
        for (Map.Entry<Character, Trie.Node> child: pi.node.children.entrySet()) {
            Character c = child.getKey();
            if (c == word.charAt(pi.pos)) {
                t.add(new Edit(Edit.Action.MATCH, prev, c, word.charAt(pi.pos)));
            }
            if (child.getValue().maxP < minP) {
                continue;
            }
            t.add(new Edit(Edit.Action.INSERT, prev, null, c));
            if (c != word.charAt(pi.pos)) {
                t.add(new Edit(Edit.Action.REPLACE, prev, word.charAt(pi.pos), c));
            }
        }
        t.add(new Edit(Edit.Action.DELETE, prev, word.charAt(pi.pos), null));
        return t;
    }

    private List<Edit> getExtensions(Path pi, double perc, String word) {
        List<Edit> t = new ArrayList<>();
        double minP = perc * pi.node.maxP;
        Character prev = pi.pos == 0 ? null : word.charAt(pi.pos - 1);
        for (Map.Entry<Character, Trie.Node> child: pi.node.children.entrySet()) {
            if (child.getValue().maxP >= minP) {
                t.add(new Edit(Edit.Action.INSERT, prev, null, child.getKey()));
            }
        }
        return t;
    }

    private double editProb(Edit edit) {
        double third = errorModel.getOrDefault(edit, 0.0);
        edit.prev = null;
        double second = errorModel.getOrDefault(edit, 0.0);
        return alpha * edit.action.prob + beta * second + gamma * third;
    }

    private Path updatePath(Path curr, Edit edit, String word) {
        int i = curr.pos;
        Trie.Node n = curr.node;
        switch (edit.action) {
            case MATCH:
                i = curr.pos + 1;
                n = curr.node.children.get(word.charAt(curr.pos));
                break;
            case DELETE:
                i = curr.pos + 1;
                n = curr.node;
                break;
            case REPLACE:
                i = curr.pos + 1;
                n = curr.node.children.get(edit.to);
                break;
            case INSERT:
                i = curr.pos;
                n = curr.node.children.get(edit.to);
                break;
        }
        double historyProb = curr.histProb * editProb(edit);
        double prob = curr.prob * (n.maxP / curr.node.maxP) * historyProb;
        return new Path(i, n, historyProb, prob, curr.edits + (edit.action == Edit.Action.MATCH ? 0 : 1));
    }

    List<String> getSuggestions(String word, int k, double perc, int top, double editRate) {
        List<String> suggestions = new ArrayList<>();
        PriorityQueue<Path> prioQueue = new PriorityQueue<>();
        List<Path> newPaths = new ArrayList<>();
        prioQueue.add(new Path(0, trie.root, 1.0, 1.0, 0));
        while (!prioQueue.isEmpty()) {
            Path curr = prioQueue.poll();
            if (prioQueue.size() > 3_000_000) {
                suggestions.add("^" + word);
                return suggestions;
            }
            if (curr.pos < word.length()) {
                newPaths.clear();
                for (Edit edit : getEdits(curr, word, perc)) {
                    Path candidate = updatePath(curr, edit, word);
                    if (candidate.edits * 1.0 / word.length() > editRate) {
                        continue;
                    }
                    if (newPaths.size() < top) {
                        newPaths.add(candidate);
                    } else {
                        for (int j = 0; j < newPaths.size(); j++) {
                            if (candidate.prob > newPaths.get(j).prob) {
                                newPaths.set(j, candidate);
                                break;
                            }
                        }
                    }
                }
                prioQueue.addAll(newPaths);
            } else {
                if (curr.node.symbol == '$') {
                    suggestions.add(curr.node.word);
                    if (suggestions.size() >= k) {
                        return suggestions;
                    }
                } else {
                    newPaths.clear();
                    for (Edit edit : getExtensions(curr, perc, word)) {
                       Path candidate = updatePath(curr, edit, word);
                        if (candidate.edits * 1.0 / word.length() > editRate) {
                            continue;
                        }
                        if (newPaths.size() < top) {
                            newPaths.add(candidate);
                        } else {
                            for (int j = 0; j < newPaths.size(); j++) {
                                if (candidate.prob > newPaths.get(j).prob) {
                                    newPaths.set(j, candidate);
                                    break;
                                }
                            }
                        }
                    }
                    prioQueue.addAll(newPaths);
                }
            }
        }
        return suggestions;
    }

    String getBest(List<String> suggestions, String word) {
        String best = null;
        double maxP = 0.0;
        for (String suggestion: suggestions) {
            List<Edit> prescription = edPrescription(word, suggestion);
            double p = 1.0;
            for (Edit edit: prescription) {
                p *= editProb(edit);
            }
            p *= languageModel.get(suggestion);
            if (p > maxP) {
                maxP = p;
                best = suggestion;
            }
        }
        return best;
    }

    public static void main(String[] args) {
        Main m = new Main();
        m.countLangModel("words.freq.csv");
        m.countErrModel("public.freq.csv");
        for (Map.Entry<String, Double> e : m.languageModel.entrySet()) {
            m.trie.addWord(e.getKey(), e.getValue());
        }

//        String query = "РОЧТА";
//        String query = "АРИГАММИ";
//        List<String> suggestions = m.getSuggestions(query + "$", 10, 0.05, 15, 0.4);
//        System.out.println(suggestions);
//        String best = m.getBest(suggestions, query + "$");
//        System.out.println("best: " + best + " substr:" + best.substring(1, best.length() - 1));

        CSVReader.write("my_submission.csv", "Id,Expected", false);
        CSVReader.read("words.freq.csv", parsed -> {
            String query = parsed[0] + "$";
            List<String> suggestions = m.getSuggestions(query, 10, 0.05, 10, 0.4);
            String best = m.getBest(suggestions, query);
            if (best == null) {
                CSVReader.write("my_submission.csv", parsed[0]+ "," + parsed[0], true);
            } else {
                CSVReader.write("my_submission.csv", parsed[0] + "," + best.substring(1, best.length() - 1), true);
            }
        });
    }
}
