package org.example.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.AvatarBrain;
import org.example.vts.animations.AnimationRegistry;
import org.example.vts.context.VTSContext;
import org.example.vts.expressions.ExpressionRegistry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JavaFX GUI приложение для Avatar Brain
 * Полно��ункциональный чат с управлением VTube Studio
 */
public class AvatarBrainGUI extends Application {
    private ProgressIndicator loadingIndicator; // Ты добавил его в метод, он должен быть в полях
    private ComboBox<String> manualEmotionComboBox;
    private AvatarBrain brain;
    private VTubeStudioLogger vtubeLogger;

    private TextArea chatArea;
    private TextField inputField;
    private ComboBox<String> senderComboBox;
    private TextField customSenderField;
    private Label vtubeStatusLabel;
    private Label ollamaStatusLabel;
    private Label llmModelStatusLabel;
    private Label qdrantStatusLabel;
    private Button connectVtubeButton;
    private Button startOllamaButton;
    private Button loadLLMModelButton;
    private Button startQdrantButton;
    private ExpressionRegistry expressionRegistry;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    private volatile boolean isProcessing = false;
    private volatile boolean verboseLogging = false;



    /**
     * Инициализация системы логирования
     */
    private void initializeLogging() {
        // 1. Получаем корневой логгер
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");

        // 2. Усмиряем системные логгеры (HttpClient и RMI), которые спамят в консоль
        // Устанавливаем им уровень WARNING, чтобы видеть только ошибки
        java.util.logging.Logger.getLogger("jdk.internal.net.http").setLevel(java.util.logging.Level.WARNING);
        java.util.logging.Logger.getLogger("sun.net").setLevel(java.util.logging.Level.WARNING);
        java.util.logging.Logger.getLogger("sun.rmi").setLevel(java.util.logging.Level.WARNING);
        java.util.logging.Logger.getLogger("java.rmi").setLevel(java.util.logging.Level.WARNING);
        java.util.logging.Logger.getLogger("javax.management").setLevel(java.util.logging.Level.WARNING);

        // Удаляем стандартные обработчики (чтобы не дублировать в консоль IDE)
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // 3. Добавляем наш "умный" обработчик для GUI
        rootLogger.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                String loggerName = record.getLoggerName();

                // ЧЕРНЫЙ СПИСОК: Если лог отсюда — игнорируем полностью
                if (loggerName != null && (
                        loggerName.startsWith("jdk.internal.net.http") ||
                                loggerName.startsWith("sun.net") ||
                                loggerName.startsWith("sun.rmi") ||
                                loggerName.startsWith("java.rmi") ||
                                loggerName.startsWith("javax.management") ||
                                loggerName.contains("SelectorManager") // Тот самый спам из твоего лога
                )) {
                    return;
                }

                // Выводим только если включен Verbose или если это важное сообщение (INFO и выше)
                boolean isImportant = record.getLevel().intValue() >= java.util.logging.Level.INFO.intValue();

                if (verboseLogging || isImportant) {
                    String message = formatLogRecord(record);
                    Platform.runLater(() -> appendChat(message + "\n"));
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}

            private String formatLogRecord(java.util.logging.LogRecord record) {
                String level = record.getLevel().getName();
                String loggerName = record.getLoggerName();
                String msg = record.getMessage();

                // Красивый префикс для VTube Studio
                if (loggerName != null && loggerName.contains("vts")) {
                    return "🎭 [VTS] " + msg;
                }
                // Префикс для Ollama/LLM
                if (msg.contains("Ollama") || msg.contains("LLM")) {
                    return "🦙 [LLM] " + msg;
                }

                return "🔍 [" + level + "] " + msg;
            }
        });
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            initializeLogging();

            primaryStage.setTitle("🧠 AVATAR BRAIN v1.2 - Нина");
            primaryStage.setWidth(900);
            primaryStage.setHeight(750);

            VBox root = new VBox();
            root.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11;");

            root.getChildren().add(createMenuBar());

            BorderPane content = new BorderPane();
            content.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11;");

            content.setTop(createTopPanel());
            content.setCenter(createCenterPanel());
            content.setBottom(createBottomPanel());

            VBox.setVgrow(content, Priority.ALWAYS);
            root.getChildren().add(content);

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(e -> shutdown());
            primaryStage.show();

            initializeBrainAsync();
            checkOllamaStatus();
            checkLLMModelStatus();
            checkQdrantStatus();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Ошибка инициализации: " + e.getMessage());
        }
    }

    /**
     * Создание меню с командами
     */
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setStyle("-fx-font-size: 11;");

        // Меню "Файл"
        Menu fileMenu = new Menu("📁 Файл");

        MenuItem exitItem = new MenuItem("❌ Выход");
        exitItem.setOnAction(e -> shutdown());

        fileMenu.getItems().add(exitItem);

        // Меню "Память"
        Menu memoryMenu = new Menu("💾 Память");

        Menu animationMenu = new Menu("🎬 Анимации");

        for (var entry : AnimationRegistry.getAll().entrySet()) {
            String name = entry.getKey();

            MenuItem item = new MenuItem(name);
            item.setOnAction(e -> triggerAnimationFromUI(name));

            animationMenu.getItems().add(item);
        }

        MenuItem statusItem = new MenuItem("📊 Статус памяти");
        statusItem.setOnAction(e -> showMemoryStatus());

        MenuItem searchItem = new MenuItem("🔍 Поиск в памяти");
        searchItem.setOnAction(e -> showSearchDialog());

        MenuItem exportItem = new MenuItem("💾 Экспортировать БД");
        exportItem.setOnAction(e -> exportMemoryDatabase());

        MenuItem importItem = new MenuItem("📂 Импортировать БД");
        importItem.setOnAction(e -> importMemoryDatabase());

        MenuItem clearItem = new MenuItem("🗑️ Очистить память");
        clearItem.setStyle("-fx-text-fill: #ff4444;");
        clearItem.setOnAction(e -> clearMemory());

        memoryMenu.getItems().addAll(statusItem, new SeparatorMenuItem(), searchItem, new SeparatorMenuItem(),
                exportItem, importItem, new SeparatorMenuItem(), clearItem);

        // Меню "Справка"
        Menu helpMenu = new Menu("❓ Справка");

        MenuItem helpItem = new MenuItem("📖 Доступные команды");
        helpItem.setOnAction(e -> showHelp());

        MenuItem aboutItem = new MenuItem("ℹ️ О программе");
        aboutItem.setOnAction(e -> showAbout());

        helpMenu.getItems().addAll(helpItem, new SeparatorMenuItem(), aboutItem);

        menuBar.getMenus().addAll(fileMenu, memoryMenu, animationMenu, helpMenu);
        return menuBar;
    }

    /**
     * Верхняя панель с заголовком и статусом VTube Studio
     */
    /**
     * Верхняя панель с заголовком и статусом VTube Studio + Выбор эмоций
     */
    private VBox createTopPanel() {
        VBox topPanel = new VBox(8);
        topPanel.setPadding(new Insets(12));
        topPanel.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #444444; -fx-border-width: 0 0 2 0;");

        // Заголовок
        Label titleLabel = new Label("🧠 AVATAR BRAIN v1.2 - Нина (Tsundere)");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Общая панель статусов
        HBox statusesPanel = new HBox(10);
        statusesPanel.setAlignment(Pos.TOP_LEFT);
        statusesPanel.setPadding(new Insets(10));
        statusesPanel.setStyle("-fx-border-color: #444444; -fx-border-width: 1; -fx-background-color: #1e1e1e; -fx-border-radius: 5;");

        // --- 1. Блок Ollama LLM ---
        VBox ollamaBox = new VBox(5);
        ollamaBox.setStyle("-fx-border-color: #333333; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #0d0d0d; -fx-border-radius: 3;");
        ollamaBox.setPrefWidth(220);

        Label ollamaTitle = new Label("🦙 Ollama LLM");
        ollamaTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaaaaa;");

        // Статус контейнера (уже был)
        this.ollamaStatusLabel = new Label("● Контейнер: Проверка...");
        this.ollamaStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #ffaa00;");

        // !!! ВАЖНО: Инициализируем те самые поля, на которых было падение !!!
        this.llmModelStatusLabel = new Label("● Модель: Ожидание...");
        this.llmModelStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #888888;");

        this.loadLLMModelButton = new Button("📥 Загрузить");
        this.loadLLMModelButton.setStyle("-fx-font-size: 10;");
        this.loadLLMModelButton.setOnAction(e -> checkLLMModelStatus());

        this.startOllamaButton = new Button("▶️ Запустить");
        this.startOllamaButton.setStyle(
                "-fx-font-size: 11; " +
                        "-fx-padding: 6 10; " +
                        "-fx-background-color: #0d47a1; " +
                        "-fx-text-fill: #ffffff;"
        );

        this.startOllamaButton.setOnAction(e -> startOllamaDocker());

        ollamaBox.getChildren().addAll(
                ollamaTitle,
                ollamaStatusLabel,
                startOllamaButton,
                new Separator(), // Визуальное разделение для модели
                llmModelStatusLabel,
                loadLLMModelButton
        );

        // --- 2. Блок VTube Studio ---
        VBox vtubeBox = new VBox(5);
        vtubeBox.setStyle("-fx-border-color: #333333; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #0d0d0d; -fx-border-radius: 3;");
        vtubeBox.setPrefWidth(200);

        Label vtubeTitle = new Label("🎭 VTube Studio");
        vtubeTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaaaaa;");

        this.vtubeStatusLabel = new Label("● VTube: Отключено");
        this.vtubeStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #ff4444;");

        this.connectVtubeButton = new Button("🔗 Связь");
        this.connectVtubeButton.setStyle("-fx-font-size: 10;");
        updateVtubeStatus(false);
        this.connectVtubeButton.setOnAction(e -> toggleVtubeConnection());

        if (loadingIndicator == null) {
            loadingIndicator = new ProgressIndicator();
            loadingIndicator.setPrefSize(15, 15);
            loadingIndicator.setVisible(false);
        }

        HBox vtsActionBox = new HBox(5, connectVtubeButton, loadingIndicator);
        vtsActionBox.setAlignment(Pos.CENTER_LEFT);
        vtubeBox.getChildren().addAll(vtubeTitle, vtubeStatusLabel, vtsActionBox);

        // --- 3. Блок Ручных Эмоций ---
        VBox manualControlBox = new VBox(5);
        manualControlBox.setStyle("-fx-border-color: #333333; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #0d0d0d; -fx-border-radius: 3;");
        manualControlBox.setPrefWidth(180);

        Label manualTitle = new Label("✨ Эмоции (Manual)");
        manualTitle.setStyle("-fx-font-size: 11; -fx-text-fill: #aaaaaa; -fx-font-weight: bold;");

        // Поле manualEmotionComboBox должно быть инициализировано ДО вызова initManualEmotionControl
        if (this.manualEmotionComboBox == null) {
            this.manualEmotionComboBox = new ComboBox<>();
        }
        this.manualEmotionComboBox.setStyle("-fx-font-size: 10;");
        this.manualEmotionComboBox.setPrefWidth(160);

        manualControlBox.getChildren().addAll(manualTitle, manualEmotionComboBox);

        // --- 4. Блок Базы Данных (Qdrant) ---
        VBox qdrantBox = new VBox(5);
        qdrantBox.setStyle("-fx-border-color: #333333; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #0d0d0d; -fx-border-radius: 3;");
        qdrantBox.setPrefWidth(180);

        Label qdrantTitle = new Label("📊 Qdrant DB");
        qdrantTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaaaaa;");

        this.qdrantStatusLabel = new Label("● База: Ожидание...");
        this.qdrantStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #ffaa00;");

        this.startQdrantButton = new Button("▶️ База Данных");
        this.startQdrantButton.setStyle(
                "-fx-font-size: 10; " +
                        "-fx-padding: 6; " +
                        "-fx-background-color: #0d47a1; " +
                        "-fx-text-fill: #ffffff;"
        );
        this.startQdrantButton.setOnAction(e -> startQdrantDocker());

        qdrantBox.getChildren().addAll(qdrantTitle, qdrantStatusLabel, startQdrantButton);

        // Сборка
        statusesPanel.getChildren().addAll(ollamaBox, vtubeBox, manualControlBox, qdrantBox);
        topPanel.getChildren().addAll(titleLabel, statusesPanel);
        this.expressionRegistry = new ExpressionRegistry(VTSContext.getClient());
        initManualEmotionControl();

        return topPanel;
    }

    private void initManualEmotionControl() {
        manualEmotionComboBox.getItems().clear();

        // просто имена из registry
        for (var exp : expressionRegistry.getAll().values()) {
            manualEmotionComboBox.getItems().add(exp.getName());
        }

        manualEmotionComboBox.setOnAction(event -> {
            String selected = manualEmotionComboBox.getValue();

            if (selected != null) {
                brain.triggerManualExpression(selected);
                appendChat("🎭 [GUI] Активирована эмоция: " + selected + "\n");
            }
        });
    }
    /**
     * Центральная область - чат
     */
    private VBox createCenterPanel() {
        VBox centerPanel = new VBox();
        centerPanel.setPadding(new Insets(10));
        centerPanel.setStyle("-fx-background-color: #0d0d0d;");

        chatArea = new TextArea();
        chatArea.setWrapText(true);
        chatArea.setEditable(false);
        chatArea.setStyle(
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 11; " +
            "-fx-control-inner-background: #0d0d0d; " +
            "-fx-text-fill: #00ff00; " +
            "-fx-padding: 10;" +
            "-fx-border-color: #333333; " +
            "-fx-border-width: 1;"
        );

        VBox.setVgrow(chatArea, Priority.ALWAYS);
        centerPanel.getChildren().add(chatArea);


        return centerPanel;
    }

    /**
     * Нижняя панель - ввод текста
     */
    private VBox createBottomPanel() {
        VBox bottomPanel = new VBox(8);
        bottomPanel.setPadding(new Insets(10));
        bottomPanel.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #444444; -fx-border-width: 2 0 0 0;");

        // Выбор отправителя (с предустановками и ручным вводом)
        HBox senderBox = new HBox(8);
        senderBox.setAlignment(Pos.CENTER_LEFT);
        senderBox.setPadding(new Insets(5));

        Label senderLabel = new Label("👥 Отправитель:");
        senderLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #aaaaaa; -fx-font-weight: bold;");

        // ComboBox с предустановками
        senderComboBox = new ComboBox<>();
        senderComboBox.getItems().addAll("👤 Пользователь", "🎭 Нина", "💬 Друг", "🤖 Бот", "👨 Другой персонаж", "✍️ Вручную");
        senderComboBox.setValue("👤 Пользователь");
        senderComboBox.setStyle(
            "-fx-font-size: 11; " +
            "-fx-padding: 8; " +
            "-fx-control-inner-background: #0d0d0d; " +
            "-fx-text-fill: #00ff00; " +
            "-fx-border-color: #333333;"
        );
        senderComboBox.setPrefWidth(180);

        // Текстовое поле для ручного ввода имени
        customSenderField = new TextField();
        customSenderField.setPromptText("Введите своё имя...");
        customSenderField.setStyle(
            "-fx-font-size: 11; " +
            "-fx-padding: 8; " +
            "-fx-control-inner-background: #0d0d0d; " +
            "-fx-text-fill: #ffaa00; " +
            "-fx-border-color: #ff9800; " +
            "-fx-border-width: 1;"
        );
        customSenderField.setPrefWidth(150);
        customSenderField.setVisible(false);

        // Слушатель для ComboBox - показываем/скрываем текстовое поле
        senderComboBox.setOnAction(e -> {
            String selected = senderComboBox.getValue();
            if (selected != null && selected.contains("✍️")) {
                customSenderField.setVisible(true);
                customSenderField.requestFocus();
            } else {
                customSenderField.setVisible(false);
                customSenderField.clear();
            }
        });

        senderBox.getChildren().addAll(senderLabel, senderComboBox, customSenderField);

        // Поле ввода с фоном
        HBox inputBox = new HBox(8);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(10));
        inputBox.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #444444; -fx-border-width: 1; -fx-border-radius: 3;");

        Label inputLabel = new Label("💭 Сообщение:");
        inputLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #aaaaaa; -fx-font-weight: bold;");

        inputField = new TextField();
        inputField.setPromptText("Введите сообщение и нажмите Enter...");
        inputField.setStyle(
            "-fx-font-size: 11; " +
            "-fx-padding: 10; " +
            "-fx-control-inner-background: #0d0d0d; " +
            "-fx-text-fill: #00ff00; " +
            "-fx-border-color: transparent;"
        );
        inputField.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) {
                sendMessage();
            }
        });
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendButton = new Button("📤 Отправить");
        sendButton.setPrefWidth(120);
        sendButton.setPrefHeight(40);
        sendButton.setStyle(
            "-fx-font-size: 11; " +
            "-fx-padding: 10; " +
            "-fx-background-color: #1b5e20; " +
            "-fx-text-fill: #ffffff; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand;"
        );
        sendButton.setOnAction(e -> sendMessage());

        inputBox.getChildren().addAll(inputLabel, inputField, sendButton);
        bottomPanel.getChildren().addAll(senderBox, inputBox);

        return bottomPanel;
    }

    /**
     * Инициализация мозга в фоновом потоке
     */
    private void initializeBrainAsync() {
        new Thread(() -> {
            try {
                appendChat("⏳ Инициализация системы...\n");
                if (verboseLogging) {
                    appendChat("🔍 [VERBOSE] Создание экземпляра AvatarBrain\n");
                }
                brain = new AvatarBrain();

                // Памяти
                try {
                    if (verboseLogging) {
                        appendChat("🔍 [VERBOSE] Инициализация системы памяти (Qdrant)\n");
                    }
                    brain.initializeMemory();
                    appendChat("✅ Система памяти инициализирована\n");
                    if (verboseLogging) {
                        appendChat("🔍 [VERBOSE] Система памяти готова\n");
                    }
                } catch (Exception e) {
                    appendChat("⚠️  Память недоступна (опционально)\n");
                    if (verboseLogging) {
                        appendChat("🔍 [VERBOSE] Ошибка инициализации памяти: " + e.getMessage() + "\n");
                    }
                }

                appendChat("✅ Ollama подключена\n");

                // Автоматическое подключение VTube Studio
                appendChat("⏳ Подключение к VTube Studio...\n");
                if (verboseLogging) {
                    appendChat("🔍 [VERBOSE] Попытка подключения к VTube Studio (ws://localhost:8001)\n");
                }

                if (brain.initializeVTubeStudio()) {
                    appendChat("✅ VTube Studio подключена!\n");
                    if (verboseLogging) {
                        appendChat("🔍 [VERBOSE] WebSocket соединение установлено\n");
                        appendChat("🔍 [VERBOSE] Аутентификация успешна\n");
                    }
                    updateVtubeStatus(true);

                    // Инициализируем логирование
                    String logPath = "logs/vtube_studio_" + System.currentTimeMillis() + ".log";
                    vtubeLogger = new VTubeStudioLogger(logPath);
                    vtubeLogger.logConnection("✅ Автоматическое подключение при старте");
                    if (verboseLogging) {
                        appendChat("🔍 [VERBOSE] Логирование VTube Studio активировано: " + logPath + "\n");
                    }
                } else {
                    appendChat("⚠️  VTube Studio недоступна (подключите вручную)\n");
                    if (verboseLogging) {
                        appendChat("🔍 [VERBOSE] Ошибка подключения к VTube Studio\n");
                    }
                    updateVtubeStatus(false);
                }

                if (verboseLogging) {
                    appendChat("🔍 [VERBOSE] Инициализация завершена. Приложение готово к работе\n");
                }

                // Активируем ввод
                Platform.runLater(() -> inputField.requestFocus());

            } catch (Exception e) {
                appendChat("❌ КРИТИЧЕСКАЯ ОШИБКА: " + e.getMessage() + "\n");
                if (verboseLogging) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    appendChat("🔍 [VERBOSE] Stack trace:\n");
                    appendChat("    " + sw.toString().replace("\n", "\n    ") + "\n");
                }
                e.printStackTrace();
            }
        }).start();
    }


    private void sendMessage() {
        if (isProcessing || brain == null) {
            return;
        }

        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        inputField.clear();
        isProcessing = true;

        // Получаем отправителя
        String sender = senderComboBox.getValue();
        String senderName;
        String senderEmoji;
        String displaySender;

        if (sender != null && sender.contains("✍️")) {
            String customName = customSenderField.getText().trim();
            senderName = customName.isEmpty() ? "Кастомный персонаж" : customName;
            senderEmoji = "✨";
            displaySender = senderEmoji + " " + senderName;
        } else {
            senderName = extractSenderName(sender);
            senderEmoji = extractSenderEmoji(sender);
            displaySender = sender;
        }

        appendChat("[" + timeFormat.format(new Date()) + "] " + displaySender + ": " + message + "\n");

        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                String response = brain.think(message);
                long duration = System.currentTimeMillis() - startTime;

                Platform.runLater(() -> {
                    appendChat("[" + timeFormat.format(new Date()) + "] 🎭 Нина: " + response + "\n");
                    appendChat("⏱️  Время ответа: " + duration + "ms\n\n");

                    // 👉 ОБРАБОТКА ЭМОЦИИ (НОВАЯ ЛОГИКА)
                    if (brain.isVTubeStudioEnabled() && vtubeLogger != null) {

                        vtubeLogger.logInfo(senderName + ": " + message);
                        vtubeLogger.logInfo("Нина: " + response);

                        String emotion = brain.getLauncher()
                                .getExpressionHandler()
                                .extractExpression(response);

                        if (emotion != null && !emotion.isEmpty()) {

                            vtubeLogger.logEmotionSent(emotion, "auto");
                            appendChat("🎭 Эмоция: " + emotion + "\n\n");

                            // 🔥 ВАЖНО: через MovementManager
                            brain.triggerManualExpression(emotion);

                        }
                    }

                    isProcessing = false;
                    inputField.requestFocus();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendChat("❌ ОШИБКА: " + e.getMessage() + "\n\n");
                    isProcessing = false;
                    inputField.requestFocus();
                });
            }
        }).start();
    }

    private String extractSenderName(String sender) {
        // Убираем эмодзи и берем только текст
        return sender.replaceAll("[👤🎭💬🤖👨]\\s*", "").trim();
    }



    /**
     * Извлечение эмодзи отправителя
     */
    private String extractSenderEmoji(String sender) {
        if (sender.contains("👤")) return "👤";
        if (sender.contains("🎭")) return "🎭";
        if (sender.contains("💬")) return "💬";
        if (sender.contains("🤖")) return "🤖";
        if (sender.contains("👨")) return "👨";
        return "👤";
    }

    /**
     * Запуск Docker контейнера с Ollama
     */
    private void startOllamaDocker() {
        appendChat("\n⏳ Проверка Docker...\n");
        startOllamaButton.setDisable(true);

        new Thread(() -> {
            try {
                // Проверяем доступность Ollama
                if (isOllamaRunning()) {
                    updateOllamaStatus(true);
                    appendChat("✅ Ollama уже запущена!\n\n");
                    Platform.runLater(() -> {
                        startOllamaButton.setText("✓ Ollama работает");
                        startOllamaButton.setStyle("-fx-font-size: 10; -fx-padding: 6; -fx-background-color: #1b5e20; -fx-text-fill: #00ff00;");
                        startOllamaButton.setDisable(false);
                    });
                    return;
                }

                // Проверяем Docker
                if (!isDockerAvailable()) {
                    appendChat("❌ Docker не установлен или не запущен\n");
                    appendChat("⚠️  Установите Docker Desktop: https://www.docker.com/products/docker-desktop\n\n");
                    Platform.runLater(() -> startOllamaButton.setDisable(false));
                    return;
                }

                appendChat("✅ Docker доступен\n");
                appendChat("⏳ Запуск контейнера Ollama (может занять время)...\n");

                // Запускаем контейнер Ollama
                startOllamaContainer();

                appendChat("⏳ Ожидание готовности Ollama...\n");

                // Ждём пока Ollama запустится (максимум 2 минуты)
                int attempts = 0;
                while (attempts < 120 && !isOllamaRunning()) {
                    Thread.sleep(1000);
                    attempts++;
                    if (attempts % 10 == 0) {
                        appendChat("⏳ Ожидание... (" + attempts + "сек)\n");
                    }
                }

                if (isOllamaRunning()) {
                    appendChat("⏳ Загрузка модели saiga (может занять минуту)...\n");

                    // Загружаем модель saiga
                    loadOllamaModel("saiga");

                    Thread.sleep(2000); // Даём время на инициализацию

                    Platform.runLater(() -> {
                        updateOllamaStatus(true);
                        appendChat("✅ Ollama успешно запущена!\n");
                        appendChat("✅ Модель saiga загружена\n");
                        appendChat("🎯 Контейнер доступен по адресу: http://localhost:11434\n\n");
                        startOllamaButton.setText("✓ Ollama работает");
                        startOllamaButton.setStyle("-fx-font-size: 10; -fx-padding: 6; -fx-background-color: #1b5e20; -fx-text-fill: #00ff00;");
                        startOllamaButton.setDisable(false);
                    });
                } else {
                    appendChat("❌ Ollama не запустилась за отведённое время\n");
                    appendChat("⚠️  Проверьте логи Docker контейнера\n\n");
                    Platform.runLater(() -> {
                        updateOllamaStatus(false);
                        startOllamaButton.setDisable(false);
                    });
                }

            } catch (Exception e) {
                appendChat("❌ Ошибка: " + e.getMessage() + "\n\n");
                Platform.runLater(() -> {
                    updateOllamaStatus(false);
                    startOllamaButton.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * Загрузка модели в Ollama
     */
    private void loadOllamaModel(String modelName) {
        try {
            String[] cmd;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                cmd = new String[]{"cmd", "/c", "docker exec qwen_server ollama pull " + modelName};
            } else {
                cmd = new String[]{"/bin/bash", "-c", "docker exec qwen_server ollama pull " + modelName};
            }

            Process process = Runtime.getRuntime().exec(cmd);
            boolean completed = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                appendChat("⚠️  Загрузка модели занимает слишком долго (timeout > 2 мин)\n");
            } else if (process.exitValue() == 0) {
                appendChat("✅ Модель " + modelName + " загружена\n");
            } else {
                appendChat("⚠️  Модель " + modelName + " может быть уже загружена\n");
            }
        } catch (Exception e) {
            appendChat("⚠️  Ошибка загрузки модели: " + e.getMessage() + "\n");
        }
    }

    /**
     * Проверка доступности Docker
     */
    private boolean isDockerAvailable() {
        try {
            String[] cmd;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                cmd = new String[]{"cmd", "/c", "docker --version"};
            } else {
                cmd = new String[]{"/bin/bash", "-c", "docker --version"};
            }

            Process process = Runtime.getRuntime().exec(cmd);
            boolean completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Запуск контейнеров через docker-compose
     */
    private void startOllamaContainer() {
        try {
            String[] cmd;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                cmd = new String[]{"cmd", "/c", "docker-compose up -d"};
            } else {
                cmd = new String[]{"/bin/bash", "-c", "cd " + System.getProperty("user.dir") + " && docker-compose up -d"};
            }

            appendChat("⏳ Запуск docker-compose для Ollama...\n");
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains("done") || line.contains("created") || line.contains("started")) {
                    appendChat("   ✓ " + line.trim() + "\n");
                }
            }

            boolean completed = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                appendChat("⚠️  docker-compose занял слишком долго\n");
            } else if (process.exitValue() == 0) {
                appendChat("✅ Контейнеры запущены\n");
            } else {
                appendChat("⚠️  Ошибка при запуске (код: " + process.exitValue() + ")\n");
            }

        } catch (Exception e) {
            appendChat("⚠️  Ошибка: " + e.getMessage() + "\n");
        }
    }

    /**
     * Проверка доступности Ollama
     */
    private boolean isOllamaRunning() {
        try {
            HttpClient client = java.net.http.HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:11434"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 404;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Обновление статуса Ollama в UI
     */
    private void updateOllamaStatus(boolean isRunning) {
        Platform.runLater(() -> {
            if (isRunning) {
                ollamaStatusLabel.setText("● Контейнер: ✅ Работает");
                ollamaStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #44ff44;");
            } else {
                ollamaStatusLabel.setText("● Контейнер: ❌ Остановлен");
                ollamaStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #ff4444;");
            }
        });
    }

    /**
     * Обновление статуса LLM модели в UI
     */
    private void updateLLMModelStatus(String modelName, boolean isLoaded) {
        Platform.runLater(() -> {
            if (isLoaded) {
                llmModelStatusLabel.setText("● Модель: ✅ " + modelName);
                llmModelStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #44ff44;");
                loadLLMModelButton.setText("✓ Загружена");
                loadLLMModelButton.setStyle("-fx-font-size: 10; -fx-padding: 6 10; -fx-background-color: #1b5e20; -fx-text-fill: #00ff00; -fx-border-radius: 2;");
                loadLLMModelButton.setDisable(true);
            } else {
                llmModelStatusLabel.setText("● Модель: ❌ " + modelName);
                llmModelStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #ff4444;");
                loadLLMModelButton.setText("📥 Загрузить");
                loadLLMModelButton.setStyle("-fx-font-size: 10; -fx-padding: 6 10; -fx-background-color: #ff6f00; -fx-text-fill: #ffffff; -fx-border-radius: 2;");
                loadLLMModelButton.setDisable(false);
            }
        });
    }

    /**
     * Загрузка LLM модели (saiga или альтернатива)
     */
    private void startLLMModel() {
        if (!isOllamaRunning()) {
            appendChat("❌ Ollama не работает!\n");
            appendChat("⚠️  Сначала запустите Ollama нажав '[▶️ Запустить]'\n\n");
            return;
        }

        loadLLMModelButton.setDisable(true);
        loadLLMModelButton.setText("⏳ Загрузка...");

        new Thread(() -> {
            try {
                appendChat("\n📥 === ЗАГРУЗКА LLM МОДЕЛИ SAIGA ===\n");
                appendChat("⏳ Проверка наличия модели в Ollama...\n");

                // Проверяем есть ли уже модель
                String[] listCmd;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    listCmd = new String[]{"cmd", "/c", "docker exec qwen_server ollama list"};
                } else {
                    listCmd = new String[]{"/bin/bash", "-c", "docker exec qwen_server ollama list"};
                }

                Process listProcess = Runtime.getRuntime().exec(listCmd);
                BufferedReader listReader = new BufferedReader(new InputStreamReader(listProcess.getInputStream()));
                String line;
                boolean modelExists = false;

                while ((line = listReader.readLine()) != null) {
                    if (line.contains("saiga")) {
                        modelExists = true;
                        break;
                    }
                }

                listProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

                if (modelExists) {
                    appendChat("✅ Модель saiga уже загружена!\n");
                    appendChat("🚀 Модель готова к использованию!\n\n");
                    updateLLMModelStatus("saiga", true);
                    Platform.runLater(() -> {
                        loadLLMModelButton.setDisable(false);
                        loadLLMModelButton.setText("▶️ Запустить модель");
                    });
                    return;
                }

                // Если нет, загружаем
                appendChat("⏳ Загрузка модели saiga...\n");
                appendChat("ℹ️  Это может занять 5-15 минут (зависит от интернета)\n");
                appendChat("⚠️  НЕ закрывайте приложение во время загрузки!\n\n");

                String[] pullCmd;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pullCmd = new String[]{"cmd", "/c", "docker exec qwen_server ollama pull saiga"};
                } else {
                    pullCmd = new String[]{"/bin/bash", "-c", "docker exec qwen_server ollama pull saiga"};
                }

                Process pullProcess = Runtime.getRuntime().exec(pullCmd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(pullProcess.getInputStream()));
                StringBuilder output = new StringBuilder();
                int lineCount = 0;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    lineCount++;

                    // Выводим прогресс
                    if (line.contains("pulling") || line.contains("downloading") ||
                        line.contains("verifying") || line.contains("writing")) {
                        appendChat("   📦 " + line.trim() + "\n");
                    }

                    // Даём системе время обновить UI
                    if (lineCount % 5 == 0) {
                        Thread.sleep(10);
                    }
                }

                boolean completed = pullProcess.waitFor(600, java.util.concurrent.TimeUnit.SECONDS); // 10 минут максимум

                if (!completed) {
                    pullProcess.destroyForcibly();
                    appendChat("⚠️  ТАЙМАУТ: Загрузка превысила 10 минут\n");
                    appendChat("💡 Это может означать:\n");
                    appendChat("   • Медленное интернет соединение\n");
                    appendChat("   • Проблемы с Docker\n");
                    appendChat("   • Недостаточно памяти\n\n");
                    updateLLMModelStatus("saiga", false);
                } else if (pullProcess.exitValue() == 0) {
                    appendChat("✅ Модель saiga успешно загружена!\n");
                    appendChat("🚀 Модель готова к использованию!\n\n");
                    updateLLMModelStatus("saiga", true);
                } else {
                    String fullOutput = output.toString();
                    if (fullOutput.toLowerCase().contains("already") ||
                        fullOutput.toLowerCase().contains("up to date")) {
                        appendChat("✅ Модель saiga уже загружена!\n");
                        appendChat("🚀 Модель готова к использованию!\n\n");
                        updateLLMModelStatus("saiga", true);
                    } else {
                        appendChat("❌ Ошибка загрузки модели (код: " + pullProcess.exitValue() + ")\n");
                        appendChat("📋 Попробуйте загрузить вручную:\n");
                        appendChat("   docker exec qwen_server ollama pull saiga\n\n");
                        updateLLMModelStatus("saiga", false);
                    }
                }

            } catch (Exception e) {
                appendChat("❌ Ошибка: " + e.getMessage() + "\n\n");
                updateLLMModelStatus("saiga", false);
            } finally {
                Platform.runLater(() -> {
                    loadLLMModelButton.setDisable(false);
                    loadLLMModelButton.setText("▶️ Запустить модель");
                });
            }
        }).start();
    }

    /**
     * Проверить статус загруженной модели
     */
    private void checkLLMModelStatus() {
        new Thread(() -> {
            try {
                // Пытаемся выполнить простую команду в модели
                String[] cmd;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    cmd = new String[]{"cmd", "/c", "docker exec qwen_server ollama list"};
                } else {
                    cmd = new String[]{"/bin/bash", "-c", "docker exec qwen_server ollama list"};
                }

                Process process = Runtime.getRuntime().exec(cmd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean saiageFound = false;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("saiga") || line.contains("Saiga")) {
                        saiageFound = true;
                        break;
                    }
                }

                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

                if (saiageFound) {
                    updateLLMModelStatus("saiga", true);
                } else {
                    updateLLMModelStatus("saiga", false);
                }
            } catch (Exception e) {
                updateLLMModelStatus("saiga", false);
            }
        }).start();
    }

    /**
     * Проверка статуса Ollama при инициализации
     */
    private void checkOllamaStatus() {
        new Thread(() -> {
            if (isOllamaRunning()) {
                updateOllamaStatus(true);
                Platform.runLater(() -> {
                    startOllamaButton.setText("✓ Ollama работает");
                    startOllamaButton.setStyle("-fx-font-size: 10; -fx-padding: 6; -fx-background-color: #1b5e20; -fx-text-fill: #00ff00;");
                });
            } else {
                updateOllamaStatus(false);
            }
        }).start();
    }

    /**
     * Запуск Qdrant Vector Database
     */
    private void startQdrantDocker() {
        startQdrantButton.setDisable(true);
        startQdrantButton.setText("⏳ Запуск...");

        new Thread(() -> {
            try {
                // Проверяем доступность Qdrant
                if (isQdrantRunning()) {
                    updateQdrantStatus(true);
                    appendChat("✅ Qdrant уже запущена!\n\n");
                    Platform.runLater(() -> {
                        startQdrantButton.setText("✓ Qdrant работает");
                        startQdrantButton.setStyle("-fx-font-size: 10; -fx-padding: 6; -fx-background-color: #1b5e20; -fx-text-fill: #00ff00;");
                        startQdrantButton.setDisable(false);
                    });
                    return;
                }

                // Проверяем Docker
                if (!isDockerAvailable()) {
                    appendChat("❌ Docker не установлен или не запущен\n");
                    appendChat("⚠️  Установите Docker Desktop: https://www.docker.com/products/docker-desktop\n\n");
                    Platform.runLater(() -> startQdrantButton.setDisable(false));
                    return;
                }

                appendChat("✅ Docker доступен\n");
                appendChat("⏳ Запуск контейнера Qdrant (может занять время)...\n");

                // Запускаем контейнер Qdrant
                startQdrantContainer();

                appendChat("⏳ Ожидание инициализации Qdrant (~30-60 сек)...\n");

                // Ждём пока Qdrant запустится (максимум 90 секунд)
                int attempts = 0;
                int maxAttempts = 90;
                boolean ready = false;

                while (attempts < maxAttempts) {
                    Thread.sleep(500); // Проверяем чаще (каждые 0.5 сек)
                    attempts++;

                    if (isQdrantRunning()) {
                        ready = true;
                        break;
                    }

                    // Выводим статус каждые 5 секунд
                    if (attempts % 10 == 0) {
                        appendChat("   ⏳ Инициализация... " + attempts + "сек\n");
                    }
                }

                if (ready) {
                    Platform.runLater(() -> {
                        updateQdrantStatus(true);
                        appendChat("✅ Qdrant успешно запущена!\n");
                        appendChat("🎯 Контейнер доступен по адресу: http://localhost:6333\n");
                        appendChat("📡 REST API: http://localhost:6333/api/v1\n\n");
                        startQdrantButton.setText("✓ Qdrant работает");
                        startQdrantButton.setStyle("-fx-font-size: 10; -fx-padding: 6; -fx-background-color: #1b5e20; -fx-text-fill: #00ff00;");
                        startQdrantButton.setDisable(false);
                    });
                } else {
                    appendChat("⚠️  Qdrant не запустилась за отведённое время (" + attempts + "сек)\n");
                    appendChat("🔍 Диагностика:\n");
                    appendChat("   1. Проверьте логи: docker-compose logs qdrant\n");
                    appendChat("   2. Проверьте порт: netstat -ano | findstr 6333\n");
                    appendChat("   3. Перезагрузитесь и попробуйте снова\n");
                    appendChat("   4. Проверьте доступ памяти и диска\n\n");
                    Platform.runLater(() -> {
                        updateQdrantStatus(false);
                        startQdrantButton.setDisable(false);
                    });
                }

            } catch (Exception e) {
                appendChat("❌ Ошибка: " + e.getMessage() + "\n\n");
                Platform.runLater(() -> {
                    updateQdrantStatus(false);
                    startQdrantButton.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * Запуск контейнера Qdrant
     */
    private void startQdrantContainer() {
        try {
            // Сначала проверяем, существует ли уже контейнер
            String[] checkExistsCmd;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                checkExistsCmd = new String[]{"cmd", "/c", "docker ps -a --filter name=qdrant-avatar"};
            } else {
                checkExistsCmd = new String[]{"/bin/bash", "-c", "docker ps -a --filter name=qdrant-avatar"};
            }

            Process checkExistsProcess = Runtime.getRuntime().exec(checkExistsCmd);
            boolean checkCompleted = checkExistsProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (checkCompleted && checkExistsProcess.exitValue() == 0) {
                // Если контейнер существует, удаляем его
                String[] rmCmd;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    rmCmd = new String[]{"cmd", "/c", "docker rm -f qdrant-avatar"};
                } else {
                    rmCmd = new String[]{"/bin/bash", "-c", "docker rm -f qdrant-avatar"};
                }

                Process rmProcess = Runtime.getRuntime().exec(rmCmd);
                rmProcess.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                appendChat("⏳ Старый контейнер удалён\n");
            }

            // Пытаемся запустить контейнер Qdrant с правильной конфигурацией
            String[] cmd;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows - правильный синтаксис
                cmd = new String[]{"cmd", "/c", "docker run -d --name qdrant-avatar -p 6333:6333 -p 6334:6334 " +
                    "-v qdrant_storage:/qdrant/storage qdrant/qdrant"};
            } else {
                // Linux/Mac
                cmd = new String[]{"/bin/bash", "-c", "docker run -d --name qdrant-avatar -p 6333:6333 -p 6334:6334 " +
                    "-v qdrant_storage:/qdrant/storage qdrant/qdrant"};
            }

            appendChat("⏳ Запуск нового контейнера Qdrant...\n");
            Process process = Runtime.getRuntime().exec(cmd);
            boolean completed = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                appendChat("⚠️  Запуск контейнера занял слишком долго\n");
            } else if (process.exitValue() == 0) {
                appendChat("✅ Контейнер Qdrant запущен\n");
            } else {
                appendChat("⚠️  Ошибка запуска контейнера (код: " + process.exitValue() + ")\n");
            }

        } catch (Exception e) {
            appendChat("⚠️  Ошибка запуска контейнера: " + e.getMessage() + "\n");
        }
    }

    /**
     * Проверка доступности Qdrant
     */
    private boolean isQdrantRunning() {
        // Сначала проверим, запущен ли контейнер через Docker
        try {
            String[] cmd;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                cmd = new String[]{"cmd", "/c", "docker inspect --format='{{.State.Running}}' qdrant-avatar"};
            } else {
                cmd = new String[]{"/bin/bash", "-c", "docker inspect --format='{{.State.Running}}' qdrant-avatar"};
            }

            Process process = Runtime.getRuntime().exec(cmd);
            boolean completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
            } else if (process.exitValue() == 0) {
                // Контейнер существует и запущен
                // Теперь проверим REST API доступность
                try {
                    HttpClient client = java.net.http.HttpClient.newHttpClient();

                    // Пытаемся несколько эндпоинтов
                    String[] endpoints = {
                        "http://localhost:6333/health",
                        "http://localhost:6333/api/v1/status",
                        "http://localhost:6333/"
                    };

                    for (String endpoint : endpoints) {
                        try {
                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(endpoint))
                                    .timeout(java.time.Duration.ofSeconds(1))
                                    .GET()
                                    .build();

                            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() >= 200 && response.statusCode() < 500) {
                                return true;
                            }
                        } catch (Exception ignored) {
                            // Пробуем следующий
                        }
                    }
                } catch (Exception e) {
                    // API не доступен, но контейнер запущен
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Обновление статуса Qdrant в UI
     */
    private void updateQdrantStatus(boolean isRunning) {
        Platform.runLater(() -> {
            if (isRunning) {
                qdrantStatusLabel.setText("● Qdrant: ✅ Работает");
                qdrantStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #44ff44;");
            } else {
                qdrantStatusLabel.setText("● Qdrant: ❌ Не подключена");
                qdrantStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #ff4444;");
            }
        });
    }

    /**
     * Проверка статуса Qdrant при инициализации
     */
    private void checkQdrantStatus() {
        new Thread(() -> {
            if (isQdrantRunning()) {
                updateQdrantStatus(true);
                Platform.runLater(() -> {
                    startQdrantButton.setText("✓ Qdrant работает");
                    startQdrantButton.setStyle("-fx-font-size: 10; -fx-padding: 6; -fx-background-color: #1b5e20; -fx-text-fill: #00ff00;");
                });
            } else {
                updateQdrantStatus(false);
            }
        }).start();
    }

    private void toggleVtubeConnection() {
        if (brain == null) {
            appendChat("❌ Система еще не инициализирована\n\n");
            return;
        }

        if (brain.isVTubeStudioEnabled()) {
            // Отключаем
            brain.disconnectVTubeStudio();
            updateVtubeStatus(false);
            appendChat("[" + timeFormat.format(new Date()) + "] ❌ VTube Studio отключена\n\n");

            if (vtubeLogger != null) {
                vtubeLogger.logConnection("❌ Отключено");
                vtubeLogger.close();
                vtubeLogger = null;
            }

            connectVtubeButton.setText("🔗 Связь"); // Возвращаем текст
        } else {
            // Подключаем
            if (loadingIndicator != null) loadingIndicator.setVisible(true);
            connectVtubeButton.setDisable(true);
            appendChat("[" + timeFormat.format(new Date()) + "] ⏳ Подключение VTube Studio...\n");

            new Thread(() -> {
                try {
                    // Вызываем инициализацию в Brain
                    boolean success = brain.initializeVTubeStudio();

                    Platform.runLater(() -> {
                        if (success) {
                            String logPath = "logs/vtube_studio_" + System.currentTimeMillis() + ".log";
                            vtubeLogger = new VTubeStudioLogger(logPath);
                            vtubeLogger.logConnection("✅ Подключено");

                            updateVtubeStatus(true);
                            appendChat("[" + timeFormat.format(new Date()) + "] ✅ VTube Studio подключена успешно!\n");
                            appendChat("🎭 Теперь эмоции будут отправляться автоматически\n\n");

                            connectVtubeButton.setText("🔌 Отключить"); // Меняем текст при успехе
                        } else {
                            appendChat("[" + timeFormat.format(new Date()) + "] ❌ Не удалось подключить VTube Studio\n");
                            appendChat("⚠️  Проверьте: запущен ли плагин в VTS (порт 8001)?\n\n");
                        }

                        if (loadingIndicator != null) loadingIndicator.setVisible(false);
                        connectVtubeButton.setDisable(false);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        appendChat("[" + timeFormat.format(new Date()) + "] ❌ Ошибка: " + e.getMessage() + "\n\n");
                        if (loadingIndicator != null) loadingIndicator.setVisible(false);
                        connectVtubeButton.setDisable(false);
                    });
                }
            }).start();
        }
    }

    /**
     * Обновление статуса VTube Studio в UI
     */
    private void updateVtubeStatus(boolean isConnected) {
        Platform.runLater(() -> {
            if (isConnected) {
                vtubeStatusLabel.setText("● VTube Studio: Подключено ✓");
                vtubeStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #44ff44; -fx-font-weight: bold;");
                connectVtubeButton.setText("🔗 Отключить VTube Studio");
                connectVtubeButton.setStyle(
                    "-fx-font-size: 11; " +
                    "-fx-padding: 10; " +
                    "-fx-background-color: #d32f2f; " +
                    "-fx-text-fill: #ffffff; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: bold;"
                );
            } else {
                vtubeStatusLabel.setText("● VTube Studio: Отключено");
                vtubeStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #ff4444; -fx-font-weight: bold;");
                connectVtubeButton.setText("🔗 Подключить VTube Studio");
                connectVtubeButton.setStyle(
                    "-fx-font-size: 11; " +
                    "-fx-padding: 10; " +
                    "-fx-background-color: #0d47a1; " +
                    "-fx-text-fill: #ffffff; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: bold;"
                );
            }
        });
    }

    /**
     * Добавление текста в чат
     */
    private void appendChat(String text) {
        Platform.runLater(() -> {
            chatArea.appendText(text);
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * Показать ошибку
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Показать статус памяти
     */
    private void showMemoryStatus() {
        if (brain == null) {
            appendChat("❌ Система ещё не инициализирована\n\n");
            return;
        }

        if (!brain.isMemoryRunning()) {
            appendChat("❌ Система памяти не активна\n");
            appendChat("⚠️  Возможные причины:\n");
            appendChat("   • Qdrant не запущена\n");
            appendChat("   • Сбой подключения при инициализации\n");
            appendChat("   • Демон памяти был остановлен\n");
            appendChat("📌 Решение: перезагрузите приложение\n\n");
            return;
        }

        try {
            var numParams = brain.getNumericParameters();
            appendChat("\n📊 === СТАТУС ПАМЯТИ ===\n");
            appendChat("Параметров в памяти: " + numParams.size() + "\n");

            if (!numParams.isEmpty()) {
                appendChat("\nПараметры:\n");
                numParams.values().forEach(param ->
                    appendChat("  • " + param.getParameterName() + ": " + param.getValue() + " " + param.getUnit() + "\n")
                );
            }
            appendChat("\n");
        } catch (Exception e) {
            appendChat("❌ Ошибка получения статуса: " + e.getMessage() + "\n\n");
        }
    }

    /**
     * Показать диалог поиска в памяти
     */
    private void showSearchDialog() {
        if (brain == null) {
            appendChat("❌ Система ещё не инициализирована\n\n");
            return;
        }

        if (!brain.isMemoryRunning()) {
            appendChat("❌ Система памяти не активна\n");
            appendChat("⚠️  Запустите Qdrant перед поиском:\n");
            appendChat("   • Нажмите кнопку '[▶️ Запустить Qdrant]' в верхней панели\n");
            appendChat("   • ИЛИ выполните: docker-compose up -d qdrant\n\n");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("🔍 Поиск в памяти");
        dialog.setHeaderText("Введите запрос для поиска:");
        dialog.setContentText("Запрос:");

        var result = dialog.showAndWait();
        result.ifPresent(query -> {
            appendChat("\n🔍 === РЕЗУЛЬТАТЫ ПОИСКА ===\n");
            appendChat("Запрос: " + query + "\n");

            try {
                var memories = brain.searchMemory(query);
                appendChat("Найдено воспоминаний: " + memories.size() + "\n\n");

                if (memories.isEmpty()) {
                    appendChat("Ничего не найдено\n\n");
                } else {
                    for (int i = 0; i < memories.size(); i++) {
                        var memory = memories.get(i);
                        String preview = memory.getContent().substring(0, Math.min(150, memory.getContent().length()));
                        appendChat((i + 1) + ". " + preview + "...\n");
                    }
                    appendChat("\n");
                }
            } catch (Exception e) {
                appendChat("❌ Ошибка при поиске: " + e.getMessage() + "\n\n");
            }
        });
    }

    /**
     * Очистить память с подтверждением
     */
    private void clearMemory() {
        if (!brain.isMemoryRunning()) {
            appendChat("❌ Система памяти не активна\n\n");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.WARNING);
        confirmDialog.setTitle("⚠️ Внимание!");
        confirmDialog.setHeaderText("ОЧИСТКА ПАМЯТИ");
        confirmDialog.setContentText("Вы уверены что хотите ПОЛНОСТЬЮ очистить всю память?\n\nЭто действие необратимо!");

        ButtonType deleteButton = new ButtonType("🗑️ Удалить всё", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButton = new ButtonType("❌ Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

        confirmDialog.getButtonTypes().setAll(deleteButton, cancelButton);

        var result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == deleteButton) {
            try {
                brain.clearAllMemories();
                appendChat("[" + timeFormat.format(new Date()) + "] 🗑️ Память полностью очищена\n\n");
            } catch (Exception e) {
                appendChat("❌ Ошибка при очистке памяти: " + e.getMessage() + "\n\n");
            }
        }
    }

    /**
     * Экспортировать базу данных в JSON файл
     */
    private void exportMemoryDatabase() {
        if (!brain.isMemoryRunning()) {
            appendChat("❌ Система памяти не активна\n\n");
            return;
        }

        // Проверяем доступность Qdrant
        if (!isQdrantRunning()) {
            appendChat("❌ Qdrant не доступна!\n");
            appendChat("⚠️  Запустите Qdrant перед экспортом:\n");
            appendChat("   • Нажмите кнопку '[▶️ Запустить Qdrant]' в верхней панели\n");
            appendChat("   • ИЛИ выполните: docker-compose up -d qdrant\n\n");
            return;
        }

        new Thread(() -> {
            try {
                appendChat("\n💾 === ЭКСПОРТ БАЗЫ ДАННЫХ ===\n");
                appendChat("⏳ Подготовка экспорта...\n");

                // Импортируем класс в этом методе чтобы избежать циклических зависимостей
                org.example.memory.QdrantBackupManager backupManager =
                    new org.example.memory.QdrantBackupManager("http://localhost:6333");

                String backupFile = backupManager.exportDatabase("memories");

                appendChat("✅ Экспорт завершён!\n");
                appendChat("📁 Файл сохранён: " + backupFile + "\n");

                long size = backupManager.getCollectionSize("memories");
                appendChat("📊 Количество записей: " + size + "\n\n");

            } catch (Exception e) {
                appendChat("❌ Ошибка при экспорте: " + e.getMessage() + "\n\n");
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Импортировать базу данных из JSON файла
     */
    private void importMemoryDatabase() {
        if (!brain.isMemoryRunning()) {
            appendChat("❌ Система памяти не активна\n\n");
            return;
        }

        // Открываем диалог выбора файла
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("📂 Выберите файл бэкапа для импорта");

        // Проверяем и создаём папку backups если её нет
        java.io.File backupsDir = new java.io.File("backups");
        if (!backupsDir.exists()) {
            backupsDir.mkdirs();
            appendChat("⚠️  Папка backups создана. Поместите JSON фай��ы туда.\n\n");
            return;
        }

        fileChooser.setInitialDirectory(backupsDir);

        javafx.stage.FileChooser.ExtensionFilter jsonFilter =
            new javafx.stage.FileChooser.ExtensionFilter("JSON файлы (*.json)", "*.json");
        javafx.stage.FileChooser.ExtensionFilter allFilter =
            new javafx.stage.FileChooser.ExtensionFilter("Все файлы (*.*)", "*.*");

        fileChooser.getExtensionFilters().addAll(jsonFilter, allFilter);

        javafx.stage.Stage stage = (javafx.stage.Stage) chatArea.getScene().getWindow();
        java.io.File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile == null) {
            return; // Пользователь отменил
        }

        // Подтверждение импорта
        Alert confirmDialog = new Alert(Alert.AlertType.WARNING);
        confirmDialog.setTitle("⚠️ Подтверждение импорта");
        confirmDialog.setHeaderText("ИМПОРТ БАЗЫ ДАННЫХ");
        confirmDialog.setContentText(
            "Вы собираетесь импортировать данные из файла:\n" +
            selectedFile.getName() + "\n\n" +
            "Это добавит новые записи в текущую базу данных.\n" +
            "Если нужно заменить базу, очистите память сначала.");

        ButtonType importButton = new ButtonType("📥 Импортировать", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("❌ Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

        confirmDialog.getButtonTypes().setAll(importButton, cancelButton);

        var result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == importButton) {
            // Выполняем импорт в фоновом потоке
            new Thread(() -> {
                try {
                    appendChat("\n📂 === ИМПОРТ БАЗЫ ДАННЫХ ===\n");
                    appendChat("⏳ Чтение файла: " + selectedFile.getName() + "\n");

                    org.example.memory.QdrantBackupManager backupManager =
                        new org.example.memory.QdrantBackupManager("http://localhost:6333");

                    appendChat("⏳ Импорт данных в Qdrant...\n");
                    backupManager.importDatabase(selectedFile.getAbsolutePath(), "memories");

                    appendChat("✅ Импорт завершён!\n");

                    long size = backupManager.getCollectionSize("memories");
                    appendChat("📊 Всего записей в памяти: " + size + "\n\n");

                } catch (Exception e) {
                    appendChat("❌ Ошибка при импорте: " + e.getMessage() + "\n\n");
                    e.printStackTrace();
                }
            }).start();
        }
    }

    /**
     * Показать справку
     */
    private void showHelp() {
        String helpText = """
            
            📖 === ДОСТУПНЫЕ КОМАНДЫ ===
            
            💾 Память:
              • Статус памяти - Показать информацию о памяти
              • Поиск в памяти - Найти информацию в памяти
              • Очистить память - Удалить всю память (с подтверждением)
            
            🎭 VTube Studio:
              • Кнопка "Подключить VTube Studio" в верхней панели
              • Автоматическая отправка эмоций в Live2D
            
            💬 Чат:
              • Просто напишите сообщение и нажмите Enter
              • Поддерживаемые эмоции в ответах:
                [smile], [angry], [blush], [sad], [surprised],
                [scared], [curious], [embarrassed], [neutral]
            
            📊 Информация:
              • Время ответа показывается после каждого ответа
              • Эмоции показываются если они были обнаружены
            
            """;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("📖 Справка");
        alert.setHeaderText("ДОСТУПНЫЕ КОМАНДЫ");
        alert.setContentText(helpText);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    /**
     * Показать информацию о программе
     */
    private void showAbout() {
        String aboutText = """
            🧠 AVATAR BRAIN v1.2
            
            Полнофункциональное приложение для общения с AI аватаром Ниной
            
            🎯 Особенности:
              • Интеграция с Ollama LLM
              • Управление VTube Studio Live2D моделью
              • Система памяти (Qdrant)
              • Автоматическое распознавание эмоций
              • Полнофункциональный GUI чат
            
            🔧 Технологии:
              • Java 16+
              • JavaFX 21
              • Ollama API
              • VTube Studio WebSocket API
              • Qdrant Vector DB
            
            👤 Персонаж: Нина (Tsundere)
            
            © 2026 Human Emulation System
            """;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("ℹ️ О программе");
        alert.setHeaderText("AVATAR BRAIN");
        alert.setContentText(aboutText);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    /**
     * Показать список доступных моделей Ollama
     */

    /**
     * Завершение работы
     */
    private void shutdown() {
        try {
            if (brain != null) {
                brain.shutdown();
            }
            if (vtubeLogger != null) {
                vtubeLogger.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }



    public static void main(String[] args) {
        launch(args);
    }

    private void triggerAnimationFromUI(String anim) {
        if (brain == null) {
            appendChat("❌ Brain не инициализирован\n\n");
            return;
        }

        if (!brain.isVTubeStudioEnabled()) {
            appendChat("❌ VTube Studio не подключена\n\n");
            return;
        }

        try {
            appendChat("🎬 [GUI] Запуск анимации: " + anim + "\n");

            brain.getLauncher()
                    .triggerAnimation(anim);

        } catch (Exception e) {
            appendChat("❌ Ошибка анимации: " + e.getMessage() + "\n\n");
        }
    }
}

