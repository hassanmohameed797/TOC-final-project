import java.util.*;

public class RegexToDFA {

    private static final char CONCAT = '.';

    private static abstract class SyntaxNode {
        boolean nullable;
        Set<Integer> firstpos = new HashSet<>();
        Set<Integer> lastpos = new HashSet<>();
    }

    private static class LeafNode extends SyntaxNode {
        LeafNode(int pos) {
            this.nullable = false;
            this.firstpos.add(pos);
            this.lastpos.add(pos);
        }
    }

    private static class ConcatNode extends SyntaxNode {
        ConcatNode(SyntaxNode left, SyntaxNode right, Map<Integer, Set<Integer>> followpos) {
            this.nullable = left.nullable && right.nullable;
            this.firstpos.addAll(left.firstpos);
            if (left.nullable) this.firstpos.addAll(right.firstpos);
            
            this.lastpos.addAll(right.lastpos);
            if (right.nullable) this.lastpos.addAll(left.lastpos);
            
            for (int i : left.lastpos) {
                followpos.computeIfAbsent(i, k -> new HashSet<>()).addAll(right.firstpos);
            }
        }
    }

    private static class UnionNode extends SyntaxNode {
        UnionNode(SyntaxNode left, SyntaxNode right) {
            this.nullable = left.nullable || right.nullable;
            this.firstpos.addAll(left.firstpos);
            this.firstpos.addAll(right.firstpos);
            this.lastpos.addAll(left.lastpos);
            this.lastpos.addAll(right.lastpos);
        }
    }

    private static class StarNode extends SyntaxNode {
        StarNode(SyntaxNode child, Map<Integer, Set<Integer>> followpos) {
            this.nullable = true;
            this.firstpos.addAll(child.firstpos);
            this.lastpos.addAll(child.lastpos);
            
            for (int i : child.lastpos) {
                followpos.computeIfAbsent(i, k -> new HashSet<>()).addAll(child.firstpos);
            }
        }
    }

    public static DFA buildFromRegex(String regex) {
        if (regex == null || regex.trim().isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        String normalized = regex.replaceAll("\\s+", "");
        if (normalized.isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        
        normalized = "(" + normalized + ")#";
        return buildDFAFromPostfix(toPostfix(addConcatOperator(normalized)));
    }

    private static String addConcatOperator(String regex) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < regex.length(); i++) {
            char current = regex.charAt(i);
            out.append(current);
            if (i + 1 < regex.length() && needsConcat(current, regex.charAt(i + 1))) out.append(CONCAT);
        }
        return out.toString();
    }

    private static boolean needsConcat(char left, char right) {
        return (isLiteral(left) || left == ')' || left == '*') && (isLiteral(right) || right == '(');
    }

    private static String toPostfix(String regex) {
        StringBuilder out = new StringBuilder();
        Deque<Character> ops = new ArrayDeque<>();
        for (char t : regex.toCharArray()) {
            if (isLiteral(t)) out.append(t);
            else if (t == '(') ops.push(t);
            else if (t == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') out.append(ops.pop());
                if (ops.isEmpty() || ops.pop() != '(') throw new IllegalArgumentException("Mismatched parentheses in regex.");
            } else if (isOperator(t)) {
                while (!ops.isEmpty() && ops.peek() != '(' && precedence(ops.peek()) >= precedence(t)) out.append(ops.pop());
                ops.push(t);
            } else {
                throw new IllegalArgumentException("Unsupported symbol in regex: " + t);
            }
        }
        while (!ops.isEmpty()) {
            if (ops.peek() == '(') throw new IllegalArgumentException("Mismatched parentheses in regex.");
            out.append(ops.pop());
        }
        return out.toString();
    }

    private static DFA buildDFAFromPostfix(String postfix) {
        Deque<SyntaxNode> st = new ArrayDeque<>();
        Map<Integer, Set<Integer>> followpos = new HashMap<>();
        Map<Integer, Character> posToSym = new HashMap<>();
        Set<Character> alpha = new HashSet<>();
        
        int pos = 1;
        int acceptPos = -1;
        
        for (char tok : postfix.toCharArray()) {
            if (isLiteral(tok)) {
                posToSym.put(pos, tok);
                if (tok == '#') acceptPos = pos;
                else alpha.add(tok);
                st.push(new LeafNode(pos));
                followpos.put(pos, new HashSet<>());
                pos++;
            } else if (tok == CONCAT) {
                if (st.size() < 2) throw new IllegalArgumentException("Invalid regex: malformed concatenation.");
                SyntaxNode right = st.pop();
                SyntaxNode left = st.pop();
                st.push(new ConcatNode(left, right, followpos));
            } else if (tok == '|' || tok == 'U') {
                if (st.size() < 2) throw new IllegalArgumentException("Invalid regex: malformed union.");
                SyntaxNode right = st.pop();
                SyntaxNode left = st.pop();
                st.push(new UnionNode(left, right));
            } else if (tok == '*') {
                if (st.isEmpty()) throw new IllegalArgumentException("Invalid regex: malformed Kleene star.");
                SyntaxNode child = st.pop();
                st.push(new StarNode(child, followpos));
            } else {
                throw new IllegalArgumentException("Unsupported regex token: " + tok);
            }
        }
        
        if (st.size() != 1) throw new IllegalArgumentException("Invalid regex: unresolved expression.");
        SyntaxNode root = st.pop();
        
        // DFA construction
        DFA dfa = new DFA();
        Map<Set<Integer>, State> dfaStates = new HashMap<>();
        Queue<Set<Integer>> q = new ArrayDeque<>();
        
        Set<Integer> startSet = root.firstpos;
        State s0 = new State(0, startSet.contains(acceptPos));
        dfaStates.put(startSet, s0);
        q.add(startSet);
        dfa.addState(s0);
        
        int id = 1;
        while (!q.isEmpty()) {
            Set<Integer> curSet = q.poll();
            State ds = dfaStates.get(curSet);
            
            for (char sym : alpha) {
                Set<Integer> nextSet = new HashSet<>();
                for (int p : curSet) {
                    if (posToSym.get(p) != null && posToSym.get(p) == sym) {
                        nextSet.addAll(followpos.get(p));
                    }
                }
                
                if (nextSet.isEmpty()) continue;
                
                State ns = dfaStates.get(nextSet);
                if (ns == null) {
                    ns = new State(id++, nextSet.contains(acceptPos));
                    dfaStates.put(nextSet, ns);
                    q.add(nextSet);
                    dfa.addState(ns);
                }
                ds.addTransition(sym, ns);
            }
        }
        
        return dfa;
    }

    private static boolean isOperator(char c) {
        return c == '|' || c == '*' || c == CONCAT || c == 'U';
    }

    private static int precedence(char operator) {
        if (operator == '*') return 3;
        if (operator == CONCAT) return 2;
        if (operator == '|' || operator == 'U') return 1;
        return -1;
    }

    private static boolean isLiteral(char c) {
        return (Character.isLetterOrDigit(c) && c != 'U') || c == '#';
    }

    public static List<String> describeDFA(DFA dfa) {
        List<String> lines = new ArrayList<>();
        for (State state : dfa.states) {
            lines.add("State q" + state.id + (state.isAccept ? " (accept)" : ""));
            for (Map.Entry<Character, State> entry : state.transitions.entrySet()) {
                lines.add("  --" + entry.getKey() + "--> q" + entry.getValue().id);
            }
        }
        return lines;
    }
}