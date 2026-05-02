package org.example.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Диалоговое окно для выбора режима работы приложения
 */
public class ModeSelectionDialog extends JDialog {
    private int selectedMode = -1; // -1 = не выбрано, 0 = только чат, 1 = чат + VTube Studio

    public ModeSelectionDialog(Frame owner) {
        super(owner, "Выбор режима работы", true);
        initializeUI();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(500, 250);
        setLocationRelativeTo(null);
        setResizable(false);

        // Основная панель
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Заголовок
        JLabel titleLabel = new JLabel("🎭 AVATAR BRAIN - Выбор режима работы");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);

        mainPanel.add(Box.createVerticalStrut(20));

        // Описание режима 1
        JPanel mode1Panel = createModePanel(
            "💬 Режим 1: Чат только",
            "Обычный чат с Ollama LLM\nБез управления аватаром VTube Studio\nМинимальные требования к ресурсам",
            0
        );
        mainPanel.add(mode1Panel);

        mainPanel.add(Box.createVerticalStrut(15));

        // Описание режима 2
        JPanel mode2Panel = createModePanel(
            "🎭 Режим 2: Чат + VTube Studio",
            "Чат + управление Live2D аватаром\nАвтоматическая отправка эмоций\nДополнительное логирование VTube Studio",
            1
        );
        mainPanel.add(mode2Panel);

        mainPanel.add(Box.createVerticalStrut(25));

        // Кнопка выхода
        JButton exitButton = new JButton("❌ Выход");
        exitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        exitButton.addActionListener(e -> {
            selectedMode = -1;
            dispose();
        });
        mainPanel.add(exitButton);

        add(mainPanel);
    }

    private JPanel createModePanel(String title, String description, int modeId) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 2));
        panel.setBackground(new Color(245, 245, 245));

        // Текст
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(new Color(245, 245, 245));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 13));
        textPanel.add(titleLabel);

        JLabel descLabel = new JLabel("<html>" + description.replace("\n", "<br>") + "</html>");
        descLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        descLabel.setForeground(new Color(80, 80, 80));
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(descLabel);

        panel.add(textPanel, BorderLayout.CENTER);

        // Кнопка выбора
        JButton selectButton = new JButton("✓ Выбрать");
        selectButton.setPreferredSize(new Dimension(100, 40));
        selectButton.addActionListener(e -> {
            selectedMode = modeId;
            dispose();
        });
        panel.add(selectButton, BorderLayout.EAST);

        return panel;
    }

    /**
     * Показать диалог и получить выбранный режим
     * @return 0 = только чат, 1 = чат + VTube Studio, -1 = выход
     */
    public int showDialog() {
        setVisible(true);
        return selectedMode;
    }
}

