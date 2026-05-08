import java.util.*;

public class RegexToDFA {

    // concat
    private static final char CONCAT = '.';

    // base node class
    private static abstract class Node {
        boolean nullable;
        Set<Integer> firstpos = new HashSet<>();
        Set<Integer> lastpos = new HashSet<>();
    }

    // leaf node (single symbol)
    private static class Leaf extends Node {
        Leaf(int pos) {
            nullable = false;
            firstpos.add(pos);
            lastpos.add(pos);
        }
    }

    // concat node
    private static class Concat extends Node {
        Concat(Node left, Node right, Map<Integer, Set<Integer>> followpos) {
            nullable = left.nullable && right.nullable;

            firstpos.addAll(left.firstpos);
            if (left.nullable)
                firstpos.addAll(right.firstpos);

            lastpos.addAll(right.lastpos);
            if (right.nullable)
                lastpos.addAll(left.lastpos);

            // update followpos
            for (int i : left.lastpos) {
                if (!followpos.containsKey(i))
                    followpos.put(i, new HashSet<>());
                followpos.get(i).addAll(right.firstpos);
            }
        }
    }

    // union node
    private static class Union extends Node {
        Union(Node left, Node right) {
            nullable = left.nullable || right.nullable;
            firstpos.addAll(left.firstpos);
            firstpos.addAll(right.firstpos);
            lastpos.addAll(left.lastpos);
            lastpos.addAll(right.lastpos);
        }
    }

    // star node
    private static class Star extends Node {
        Star(Node child, Map<Integer, Set<Integer>> followpos) {
            nullable = true;
            firstpos.addAll(child.firstpos);
            lastpos.addAll(child.lastpos);

            for (int i : child.lastpos) {
                if (!followpos.containsKey(i))
                    followpos.put(i, new HashSet<>());
                followpos.get(i).addAll(child.firstpos);
            }
        }
    }

    // main build method
    public static DFA buildFromRegex(String regex) {
        if (regex == null || regex.trim().isEmpty()) {
            throw new IllegalArgumentException("Regex cannot be empty");
        }
        regex = regex.replaceAll("\\s+", "");
        regex = "(" + regex + ")#"; // add end marker

        String withConcat = addConcat(regex);
        String postfix = toPostfix(withConcat);

        return buildDFA(postfix);
    }

    // add explicit concat operator
    private static String addConcat(String regex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            sb.append(c);
            if (i + 1 < regex.length() && needsConcat(c, regex.charAt(i + 1))) {
                sb.append(CONCAT);
            }
        }
        return sb.toString();
    }

    private static boolean needsConcat(char a, char b) {
        return (isLiteral(a) || a == ')' || a == '*') && (isLiteral(b) || b == '(');
    }

    // convert to postfix using shunting yard
    private static String toPostfix(String regex) {
        StringBuilder out = new StringBuilder();
        Deque<Character> stack = new ArrayDeque<>();

        for (char c : regex.toCharArray()) {
            if (isLiteral(c)) {
                out.append(c);
            } else if (c == '(') {
                stack.push(c);
            } else if (c == ')') {
                while (!stack.isEmpty() && stack.peek() != '(') {
                    out.append(stack.pop());
                }
                stack.pop(); // remove '('
            } else if (isOperator(c)) {
                while (!stack.isEmpty() && stack.peek() != '(' && prec(stack.peek()) >= prec(c)) {
                    out.append(stack.pop());
                }
                stack.push(c);
            }
        }
        while (!stack.isEmpty()) {
            out.append(stack.pop());
        }
        return out.toString();
    }

    // build DFA from postfix
    private static DFA buildDFA(String postfix) {
        Deque<Node> stack = new ArrayDeque<>();
        Map<Integer, Set<Integer>> followpos = new HashMap<>();
        Map<Integer, Character> posToSym = new HashMap<>();
        Set<Character> alphabet = new HashSet<>();

        int pos = 1;
        int acceptPos = -1;

        for (char c : postfix.toCharArray()) {
            if (isLiteral(c)) {
                posToSym.put(pos, c);
                if (c == '#')
                    acceptPos = pos;
                else
                    alphabet.add(c);
                stack.push(new Leaf(pos));
                followpos.put(pos, new HashSet<>());
                pos++;
            } else if (c == CONCAT) {
                Node right = stack.pop();
                Node left = stack.pop();
                stack.push(new Concat(left, right, followpos));
            } else if (c == '|' || c == 'U') {
                Node right = stack.pop();
                Node left = stack.pop();
                stack.push(new Union(left, right));
            } else if (c == '*') {
                Node child = stack.pop();
                stack.push(new Star(child, followpos));
            }
        }

        Node root = stack.pop();

        // DFA construction
        DFA dfa = new DFA();
        Map<Set<Integer>, State> dfaStates = new HashMap<>();
        Queue<Set<Integer>> q = new ArrayDeque<>();

        Set<Integer> startSet = root.firstpos;
        State startState = new State(0, startSet.contains(acceptPos));
        dfaStates.put(startSet, startState);
        q.add(startSet);
        dfa.addState(startState);

        int id = 1;
        while (!q.isEmpty()) {
            Set<Integer> curSet = q.poll();
            State curState = dfaStates.get(curSet);

            for (char sym : alphabet) {
                Set<Integer> nextSet = new HashSet<>();
                for (int p : curSet) {
                    if (posToSym.get(p) == sym) {
                        nextSet.addAll(followpos.get(p));
                    }
                }
                if (nextSet.isEmpty())
                    continue;

                State nextState = dfaStates.get(nextSet);
                if (nextState == null) {
                    nextState = new State(id++, nextSet.contains(acceptPos));
                    dfaStates.put(nextSet, nextState);
                    q.add(nextSet);
                    dfa.addState(nextState);
                }
                curState.addTransition(sym, nextState);
            }
        }
        return dfa;
    }

    private static boolean isOperator(char c) {
        return c == '|' || c == '*' || c == CONCAT || c == 'U';
    }

    private static int prec(char c) {
        if (c == '*')
            return 3;
        if (c == CONCAT)
            return 2;
        if (c == '|' || c == 'U')
            return 1;
        return 0;
    }

    private static boolean isLiteral(char c) {
        return (Character.isLetterOrDigit(c) && c != 'U') || c == '#';
    }

    // describe DFA states
    public static List<String> describeDFA(DFA dfa) {
        List<String> lines = new ArrayList<>();
        for (State s : dfa.states) {
            lines.add("State q" + s.id + (s.isAccept ? " (accept)" : ""));
            for (Map.Entry<Character, State> e : s.transitions.entrySet()) {
                lines.add("  --" + e.getKey() + "--> q" + e.getValue().id);
            }
        }
        return lines;
    }
}