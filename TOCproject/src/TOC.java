import java.util.List;
import java.util.Scanner;

public class TOC {
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
                System.out.print("Enter a test string, or enter a new regex (include |, U, *, or parentheses) to create new dfa or type 'exit' to quit: ");
                String input = sc.nextLine();

                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("Program ended.");
                    sc.close();
                    return;
                }

                // If input is regex rebuild a new dfa
                if (input.indexOf('|') >= 0 || input.indexOf('U') >= 0 || input.indexOf('*') >= 0 || input.indexOf('(') >= 0 || input.indexOf(')') >= 0) {
                    try {
                        DFA newDfa = RegexToDFA.buildFromRegex(input);
                        if (newDfa != null) {
                            dfa = newDfa;
                            System.out.println("Replaced current DFA with new regex: " + input);
                            System.out.println("Generated DFA:");
                            for (String line : RegexToDFA.describeDFA(dfa)) System.out.println(line);
                        }
                    } catch (IllegalArgumentException e) {
                        System.out.println("Invalid regular expression: " + e.getMessage());
                    }
                    continue;
                }

                if (dfa.simulate(input)) {
                    System.out.println(input + " -> Accepted");
                } else {
                    System.out.println(input + " -> Rejected");
                }
            }
        }

        sc.close();
    }
}