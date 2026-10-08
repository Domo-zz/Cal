import java.awt.*;
import java.awt.event.KeyEvent;
import java.math.*;
import javax.swing.*;

public class Calculator extends JFrame {
    private static final int MAX_DIGITS = 16;
    private static final MathContext CALC_MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final MathContext DISPLAY_MC = new MathContext(MAX_DIGITS, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal OVERFLOW_LIMIT = new BigDecimal("1e10000");

    private final JTextField displayField;

    private String entry = "0";
    private BigDecimal exactValue = null;
    private BigDecimal accumulator = BigDecimal.ZERO;
    private String pendingOp = "";
    private String lastOp = "";
    private BigDecimal lastOperand = BigDecimal.ZERO;
    private boolean startNew = true;
    private boolean operandEntered = false;
    private boolean error = false;
    private String errorMessage = "";

    public Calculator(String title) {
        super(title);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(320, 480);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        displayField = new JTextField("0");
        displayField.setFont(new Font("Tahoma", Font.BOLD, 22));
        displayField.setHorizontalAlignment(JTextField.RIGHT);
        displayField.setEditable(false);
        displayField.setBackground(Color.WHITE);
        add(displayField, BorderLayout.NORTH);
        String[][] buttons = {
            {"%", "%"}, {"CE", "CE"}, {"C", "C"}, {"←", "BS"},
            {"1/x", "INV"}, {"x²", "SQR"}, {"√x", "SQRT"}, {"/", "/"},
            {"7", "7"}, {"8", "8"}, {"9", "9"}, {"*", "*"},
            {"4", "4"}, {"5", "5"}, {"6", "6"}, {"-", "-"},
            {"1", "1"}, {"2", "2"}, {"3", "3"}, {"+", "+"},
            {"+/-", "NEG"}, {"0", "0"}, {".", "."}, {"=", "="}
        };

        JPanel buttonPanel = new JPanel(new GridLayout(6, 4, 4, 4));
        for (String[] item : buttons) {
            String command = item[1];
            JButton button = new JButton(item[0]);
            button.setFont(new Font("Tahoma", Font.BOLD, 16));
            button.setFocusable(false);
            button.addActionListener(event -> handle(command));
            buttonPanel.add(button);
        }
        add(buttonPanel, BorderLayout.CENTER);

        installKeyboard();
        refresh();
    }

    private void installKeyboard() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (!isActive()) {
                return false;
            }
            if (event.getID() == KeyEvent.KEY_PRESSED) {
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_ENTER: handle("="); return true;
                    case KeyEvent.VK_BACK_SPACE: handle("BS"); return true;
                    case KeyEvent.VK_ESCAPE: handle("C"); return true;
                    case KeyEvent.VK_DELETE: handle("CE"); return true;
                    case KeyEvent.VK_F9: handle("NEG"); return true;
                    default: return false;
                }
            }
            if (event.getID() == KeyEvent.KEY_TYPED) {
                char c = event.getKeyChar();
                if (c >= '0' && c <= '9') {
                    handle(String.valueOf(c));
                    return true;
                }
                switch (c) {
                    case '.': case ',': handle("."); return true;
                    case '+': case '-': case '*': case '/': handle(String.valueOf(c)); return true;
                    case '=': handle("="); return true;
                    case '%': handle("%"); return true;
                    case '@': handle("SQRT"); return true;
                    case 'q': case 'Q': handle("SQR"); return true;
                    case 'r': case 'R': handle("INV"); return true;
                    default: return false;
                }
            }
            return false;
        });
    }

    private void handle(String command) {
        if (command.length() == 1 && Character.isDigit(command.charAt(0))) {
            inputDigit(command);
        } else {
            switch (command) {
                case ".": inputDecimal(); break;
                case "+": case "-": case "*": case "/": inputOperator(command); break;
                case "=": evaluate(); break;
                case "%": percent(); break;
                case "INV": case "SQR": case "SQRT": unary(command); break;
                case "NEG": negate(); break;
                case "BS": backspace(); break;
                case "CE": clearEntry(); break;
                case "C": clearAll(); break;
                default: break;
            }
        }
        refresh();
    }

    private void inputDigit(String digit) {
        if (error) {
            clearAll();
        }
        if (startNew) {
            beginEntry();
            setEntryText(digit);
        } else if (digitCount(entry) < MAX_DIGITS) {
            setEntryText(entry.equals("0") ? digit : entry + digit);
        }
    }

    private void inputDecimal() {
        if (error) {
            clearAll();
        }
        if (startNew) {
            beginEntry();
            setEntryText("0.");
        } else if (!entry.contains(".")) {
            setEntryText(entry + ".");
        }
    }

    private void beginEntry() {
        startNew = false;
        operandEntered = true;
    }

    private void inputOperator(String op) {
        if (error) {
            return;
        }
        BigDecimal current = currentValue();
        if (!pendingOp.isEmpty() && operandEntered) {
            BigDecimal result = calculate(accumulator, current, pendingOp);
            if (result == null) {
                return;
            }
            setEntryValue(result);
            if (error) {
                return;
            }
            accumulator = result;
        } else if (pendingOp.isEmpty()) {
            accumulator = current;
        }
        pendingOp = op;
        startNew = true;
        operandEntered = false;
    }

    private void evaluate() {
        if (error) {
            clearAll();
            return;
        }
        BigDecimal current = currentValue();
        BigDecimal left;
        BigDecimal right;
        String op;

        if (!pendingOp.isEmpty()) {
            left = accumulator;
            right = operandEntered ? current : accumulator;
            op = pendingOp;
            lastOp = op;
            lastOperand = right;
            pendingOp = "";
        } else if (!lastOp.isEmpty()) {
            left = current;
            right = lastOperand;
            op = lastOp;
        } else {
            finishEvaluation();
            return;
        }

        BigDecimal result = calculate(left, right, op);
        if (result == null) {
            return;
        }
        setEntryValue(result);
        if (error) {
            return;
        }
        accumulator = result;
        finishEvaluation();
    }

    private void finishEvaluation() {
        startNew = true;
        operandEntered = false;
    }
    private void percent() {
        if (error) {
            return;
        }
        BigDecimal current = currentValue();
        BigDecimal result;
        if (pendingOp.equals("+") || pendingOp.equals("-")) {
            result = accumulator.multiply(current, CALC_MC).divide(HUNDRED, CALC_MC);
        } else if (pendingOp.equals("*") || pendingOp.equals("/")) {
            result = current.divide(HUNDRED, CALC_MC);
        } else {
            result = BigDecimal.ZERO;
        }
        setEntryValue(result);
        if (error) {
            return;
        }
        startNew = true;
        operandEntered = true;
    }

    private void unary(String kind) {
        if (error) {
            return;
        }
        BigDecimal result = applyUnary(kind, currentValue());
        if (result == null) {
            return;
        }
        setEntryValue(result);
        if (error) {
            return;
        }
        startNew = true;
        operandEntered = true;
    }

    private void negate() {
        if (error) {
            return;
        }
        if (!startNew) {
            if (entry.startsWith("-")) {
                setEntryText(entry.substring(1));
            } else if (!entry.equals("0")) {
                setEntryText("-" + entry);
            }
        } else if (currentValue().signum() != 0) {
            unary("NEG");
        }
    }

    private void backspace() {
        if (error) {
            clearAll();
            return;
        }
        if (startNew) {
            return;
        }
        String text = entry.substring(0, entry.length() - 1);
        if (text.isEmpty() || text.equals("-") || text.equals("-0")) {
            text = "0";
        }
        setEntryText(text);
    }

    private void clearEntry() {
        if (error) {
            clearAll();
            return;
        }
        setEntryText("0");
        startNew = true;
        operandEntered = !pendingOp.isEmpty();
    }

    private void clearAll() {
        setEntryText("0");
        accumulator = BigDecimal.ZERO;
        pendingOp = "";
        lastOp = "";
        lastOperand = BigDecimal.ZERO;
        startNew = true;
        operandEntered = false;
        error = false;
        errorMessage = "";
    }

    private BigDecimal calculate(BigDecimal first, BigDecimal second, String op) {
        switch (op) {
            case "+":
                return first.add(second, CALC_MC);
            case "-":
                return first.subtract(second, CALC_MC);
            case "*":
                return first.multiply(second, CALC_MC);
            case "/":
                if (second.signum() == 0) {
                    showError(first.signum() == 0 ? "Result is undefined" : "Cannot divide by zero");
                    return null;
                }
                return first.divide(second, CALC_MC);
            default:
                return second;
        }
    }

    private BigDecimal applyUnary(String kind, BigDecimal value) {
        switch (kind) {
            case "INV":
                if (value.signum() == 0) {
                    showError("Cannot divide by zero");
                    return null;
                }
                return BigDecimal.ONE.divide(value, CALC_MC);
            case "SQR":
                return value.multiply(value, CALC_MC);
            case "SQRT":
                if (value.signum() < 0) {
                    showError("Invalid input");
                    return null;
                }
                return value.sqrt(CALC_MC);
            case "NEG":
                return value.negate();
            default:
                return value;
        }
    }

    private void showError(String message) {
        error = true;
        errorMessage = message;
        pendingOp = "";
        lastOp = "";
        startNew = true;
    }

    private BigDecimal currentValue() {
        return exactValue != null ? exactValue : new BigDecimal(entry);
    }

    private void setEntryText(String text) {
        entry = text;
        exactValue = null;
    }

    private void setEntryValue(BigDecimal value) {
        if (value.abs().compareTo(OVERFLOW_LIMIT) >= 0) {
            showError("Overflow");
            return;
        }
        entry = toEntryString(value);
        exactValue = value.round(CALC_MC);
    }

    private String toEntryString(BigDecimal value) {
        BigDecimal rounded = value.round(DISPLAY_MC).stripTrailingZeros();
        if (rounded.signum() == 0) {
            return "0";
        }
        int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent >= MAX_DIGITS || rounded.scale() > MAX_DIGITS) {
            String digits = rounded.unscaledValue().abs().toString();
            String mantissa = digits.charAt(0) + "." + digits.substring(1);
            return (rounded.signum() < 0 ? "-" : "") + mantissa
                    + "e" + (exponent >= 0 ? "+" : "-") + Math.abs(exponent);
        }
        return rounded.toPlainString();
    }

    private String formatDisplay(String text) {
        if (text.contains("e")) {
            return text;
        }
        boolean negative = text.startsWith("-");
        String body = negative ? text.substring(1) : text;
        int dot = body.indexOf('.');
        String integerPart = dot >= 0 ? body.substring(0, dot) : body;
        String rest = dot >= 0 ? body.substring(dot) : "";

        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < integerPart.length(); i++) {
            if (i > 0 && (integerPart.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(integerPart.charAt(i));
        }
        return (negative ? "-" : "") + grouped + rest;
    }

    private int digitCount(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                count++;
            }
        }
        return count;
    }

    private void refresh() {
        displayField.setText(error ? errorMessage : formatDisplay(entry));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Calculator calculator = new Calculator("CALCULATOR");
            calculator.setVisible(true);
        });
    }
}
