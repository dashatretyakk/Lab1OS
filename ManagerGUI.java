import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.OutputStream;
import java.io.PrintStream;

public class ManagerGUI {
    private final JTextPane outputArea;
    private Manager manager;

    public ManagerGUI() {
        JFrame frame = new JFrame("Manager");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(null);

        outputArea = new JTextPane();
        frame.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();

        JButton startButton = new JButton("Почати обчислення");
        startButton.addActionListener(e -> {
            manager.reset();
            manager.startComputations();
            outputArea.setText("");
            appendColoredText("Починаємо обчислення...\n", Color.BLACK);
        });

        JButton cancelButton = new JButton("Форсоване скасування");
        cancelButton.addActionListener(e -> {
            manager.reset();
            manager.forceCancel();
            appendColoredText("Форсоване скасування...\n", Color.BLACK);
        });

        JButton statusButton = new JButton("Вивести поточний статус");
        statusButton.addActionListener(e -> {
            manager.pauseOutput();
            displayStatus();
        });

        buttonPanel.add(startButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(statusButton);

        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setVisible(true);

        PrintStream printStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                appendColoredText(String.valueOf((char) b), Color.BLACK);
            }
        });
        System.setOut(printStream);

        manager = new Manager();
        manager.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(String message) {
                SwingUtilities.invokeLater(() -> appendColoredText(message + "\n", Color.BLACK));
            }

            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> appendColoredText(error + "\n", Color.RED));
            }
        });

    }

    private void appendColoredText(String message, Color color) {
        StyledDocument doc = outputArea.getStyledDocument();
        Style style = outputArea.addStyle("ColoredStyle", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), message, style);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void displayStatus() {
        JFrame statusFrame = new JFrame("Статус");
        statusFrame.setSize(300, 200);
        statusFrame.setLayout(new BorderLayout());
        statusFrame.setLocationRelativeTo(null);

        JTextArea statusArea = new JTextArea();
        statusArea.append("Всього обчислень: " + manager.getTotalComputed() + "\n");
        statusArea.append("З них завершилося з помилкою: " + manager.getTotalFailed() + "\n");
        statusFrame.add(new JScrollPane(statusArea), BorderLayout.CENTER);
        statusFrame.setVisible(true);

        statusFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                manager.resumeOutput();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ManagerGUI::new);
    }
}
