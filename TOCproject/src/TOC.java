import java.util.List;
import java.util.Scanner;

public class TOC {
    private static void validateRegex(String regex) {
        String trimmed = regex.trim();
        if (trimmed.equals("U") || trimmed.equals("|") || trimmed.equals("*") || trimmed.equals("+")) {
            throw new IllegalArgumentException("A single operator '" + trimmed + "' is not a valid regular expression.");
        }
        if (regex.contains("()")) {
            throw new IllegalArgumentException("Empty parentheses '()' are not allowed.");
        }
        if (regex.contains("((") || regex.contains("))")) {
            throw new IllegalArgumentException("Consecutive parentheses '((' or '))' are not allowed.");
        }
        if (regex.contains("**")) {
            throw new IllegalArgumentException("Consecutive stars '**' are not allowed.");
        }
        if (regex.contains("++")) {
            throw new IllegalArgumentException("Consecutive pluses '++' are not allowed.");
        }
        if (regex.contains("||") || regex.contains("UU") || regex.contains("|U") || regex.contains("U|")) {
            throw new IllegalArgumentException("Consecutive unions are not allowed.");
        }

        int openParen = 0;
        for (char c : regex.toCharArray()) {
            if (c == '(') openParen++;
            else if (c == ')') openParen--;
            if (openParen < 0) {
                throw new IllegalArgumentException("Unmatched closing parenthesis ')'.");
            }
        }
        if (openParen != 0) {
            throw new IllegalArgumentException("Unmatched opening parenthesis '('.");
        }
        
        for (char c : regex.replaceAll("\\s+", "").toCharArray()) {
            if (c != 'a' && c != 'b' && c != 'U' && c != '|' && c != '*' && c != '+' && c != '(' && c != ')') {
                throw new IllegalArgumentException("Invalid character '" + c + "'. Only 'a', 'b', and operators (| U * + ( )) are allowed.");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("Enter regular expression (or type 'exit' to quit): ");
            String regex = sc.nextLine();

            if (regex.equalsIgnoreCase("exit")) {
                System.out.println("Program ended.");
                break;
            }

            DFA dfa;
            try {
                validateRegex(regex);
                dfa = RegexToDFA.buildFromRegex(regex);
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid regular expression: " + e.getMessage());
                continue;
            }

            if (dfa == null) {
                System.out.println("Failed to build DFA.");
                continue;
            }

            System.out.println("Use '(a|b)*abb' to match strings ending with 'abb'.");
            System.out.println("Use 'U' for union (e.g., aUb is same as a|b).");
            System.out.println("Generated DFA:");
            List<String> dfaLines = RegexToDFA.describeDFA(dfa);
            for (String line : dfaLines) {
                System.out.println(line);
            }

            while (true) {
                System.out.print(
                        "Enter a test string, or enter a new regex (include |, U, *, +, or parentheses) to create new dfa or type 'exit' to quit: ");
                String input = sc.nextLine();

                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("Program ended.");
                    sc.close();
                    return;
                }

                // If input is regex rebuild a new dfa
                if (input.indexOf('|') >= 0 || input.indexOf('U') >= 0 || input.indexOf('*') >= 0
                        || input.indexOf('+') >= 0 || input.indexOf('(') >= 0 || input.indexOf(')') >= 0) {
                    try {
                        validateRegex(input);
                        DFA newDfa = RegexToDFA.buildFromRegex(input);
                        if (newDfa != null) {
                            dfa = newDfa;
                            System.out.println("Replaced current DFA with new regex: " + input);
                            System.out.println("Generated DFA:");
                            for (String line : RegexToDFA.describeDFA(dfa))
                                System.out.println(line);
                        }
                    } catch (IllegalArgumentException e) {
                        System.out.println("Invalid regular expression: " + e.getMessage());
                    }
                    continue;
                }

                boolean validString = true;
                String testInput = input.replaceAll("\\s+", "");
                for (char c : testInput.toCharArray()) {
                    if (c != 'a' && c != 'b') {
                        System.out.println("Invalid test string: Only 'a' and 'b' are allowed.");
                        validString = false;
                        break;
                    }
                }
                
                if (!validString) {
                    continue;
                }

                if (dfa.simulate(testInput)) {
                    System.out.println(input + " -> Accepted");
                } else {
                    System.out.println(input + " -> Rejected");
                }
            }
        }

        sc.close();
    }
}