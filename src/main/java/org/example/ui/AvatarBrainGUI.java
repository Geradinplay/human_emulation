package org.example.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.AvatarBrain;
import org.example.vts.animations.AnimationRegistry;
import org.example.vts.client.VTubeStudioClient;
import org.example.vts.context.VTSContext;
import org.example.vts.expressions.ExpressionRegistry;

import java.awt.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JavaFX GUI приложение для Avatar Brain
 * Полнофункциональный чат с управлением VTube Studio
 */
public class AvatarBrainGUI extends Application {
    private final DockerService dockerService = new DockerService();
    private ProgressIndicator loadingIndicator;
    private ComboBox<String> manualEmotionComboBox;
    private AvatarBrain brain;
    private VTubeStudioLogger vtubeLogger;

    private TextArea chatArea;
    private TextField inputField;
    private ComboBox<String> senderComboBox;
    private TextField customSenderField;
    private Label vtubeStatusLabel;
    private Label ollamaStatusLabel;
    private Label qdrantStatusLabel;
    private Button connectVtubeButton;
    private Button startOllamaButton;
    private Button startQdrantButton;
    private ExpressionRegistry expressionRegistry;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    private volatile boolean isProcessing = false;

    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Отключаем стандартный заголовок окна
            primaryStage.initStyle(StageStyle.UNDECORATED);

            customSenderField = new TextField();
            customSenderField.setVisible(false);

            inputField = new TextField();
            chatArea = new TextArea();

            // Теперь собираем UI
            primaryStage.setTitle("🧠 NeuralSoul v1.2");
            primaryStage.setWidth(1300);
            primaryStage.setHeight(900);

            BorderPane mainLayout = new BorderPane();
            mainLayout.setStyle("-fx-background-color: #0b0e14;");

            // --- Кастомная верхняя панель ---
            HBox customTitleBar = createCustomTitleBar(primaryStage);

            VBox centerLayout = new VBox(0);
            VBox top = createTopPanel();
            VBox center = createCenterPanel();
            VBox bottom = createBottomPanel();

            VBox.setVgrow(center, Priority.ALWAYS);
            VBox.setVgrow(centerLayout, Priority.ALWAYS);

            // Добавляем кастомную панель сверху
            centerLayout.getChildren().addAll(customTitleBar, top, center, bottom);

            mainLayout.setCenter(centerLayout);
            mainLayout.setRight(createRightSidebar());

            primaryStage.setScene(new Scene(mainLayout));
            primaryStage.show();

            initializeBrainAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Кастомная верхняя панель с кнопками управления окном
     */
    private HBox createCustomTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.setStyle("-fx-background-color: #181c2a; -fx-padding: 0 0 0 0; -fx-border-color: #252a3d; -fx-border-width: 0 0 2 0;");
        titleBar.setPrefHeight(36);
        titleBar.setMinHeight(36);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        // Иконка приложения (по желанию)
        Label icon = new Label("");
        icon.setStyle("-fx-font-size: 18; -fx-padding: 0 8 0 12;");

        // Название с изображением
        HBox title = createNeuralSoulLabel("NeuralSoul v2.1", "15", 40);
        title.setStyle("-fx-padding: 0 0 0 0;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Кнопка свернуть
        Button minBtn = new Button("—");
        minBtn.setOnAction(e -> stage.setIconified(true));
        minBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 16; -fx-padding: 0 12 0 12;");
        minBtn.setOnMouseEntered(e -> minBtn.setStyle("-fx-background-color: #23273a; -fx-text-fill: #ffffff; -fx-font-size: 16; -fx-padding: 0 12 0 12;"));
        minBtn.setOnMouseExited(e -> minBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 16; -fx-padding: 0 12 0 12;"));

        // Кнопка развернуть/восстановить
        Button maxBtn = new Button("❐");
        maxBtn.setOnAction(e -> {
            stage.setMaximized(!stage.isMaximized());
        });
        maxBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 14; -fx-padding: 0 12 0 12;");
        maxBtn.setOnMouseEntered(e -> maxBtn.setStyle("-fx-background-color: #23273a; -fx-text-fill: #ffffff; -fx-font-size: 14; -fx-padding: 0 12 0 12;"));
        maxBtn.setOnMouseExited(e -> maxBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 14; -fx-padding: 0 12 0 12;"));

        // Кнопка закрыть
        Button closeBtn = new Button("✕");
        closeBtn.setOnAction(e -> stage.close());
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5555; -fx-font-size: 16; -fx-padding: 0 16 0 12;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: #3d1414; -fx-text-fill: #ff5555; -fx-font-size: 16; -fx-padding: 0 16 0 12;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5555; -fx-font-size: 16; -fx-padding: 0 16 0 12;"));

        // Drag'n'drop окна по кастомной панели
        titleBar.setOnMousePressed((MouseEvent event) -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged((MouseEvent event) -> {
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        titleBar.getChildren().addAll(icon, title, spacer, minBtn, maxBtn, closeBtn);
        return titleBar;
    }

    private VBox createRightSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(300);
        sidebar.setStyle("-fx-background-color: #12151f; -fx-border-color: #252a3d; -fx-border-width: 0 0 0 2;");

        Label title = new Label("УПРАВЛЕНИЕ");
        title.setStyle("-fx-text-fill: #5c6bc0; -fx-font-weight: bold; -fx-font-size: 14;");

        // Блок Анимаций со скроллом
        VBox animButtons = new VBox(5);
        for (var entry : AnimationRegistry.getAll().entrySet()) {
            Button btn = new Button(entry.getKey());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: #1c2230; -fx-text-fill: #cccccc; -fx-cursor: hand;");
            btn.setOnAction(e -> triggerAnimationFromUI(entry.getKey()));
            animButtons.getChildren().add(btn);
        }
        ScrollPane animScroll = new ScrollPane(animButtons);
        animScroll.setFitToWidth(true);
        animScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");


        // Блок Эмоций
        VBox emotionSection = new VBox(10);
        Label emoLabel = new Label("✨ Эмоции (Manual)");
        emoLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-weight: bold;");
        manualEmotionComboBox = new ComboBox<>(); // Поле уже объявлено в классе
        manualEmotionComboBox.setMaxWidth(Double.MAX_VALUE);
        manualEmotionComboBox.setPromptText("Выберите эмоцию...");
        manualEmotionComboBox.setStyle("-fx-base: #1c2230;");
        emotionSection.getChildren().addAll(emoLabel, manualEmotionComboBox);

        // --- Системные кнопки (внизу сайдбара) ---
        VBox sysButtons = new VBox(10);
        sysButtons.setPadding(new Insets(10, 0, 0, 0));

        // Кнопка "О программе"
        Button aboutBtn = new Button("О программе");
        aboutBtn.setMaxWidth(Double.MAX_VALUE);
        aboutBtn.setStyle("-fx-background-color: #1c2230; -fx-text-fill: #cccccc; -fx-padding: 8; -fx-cursor: hand;");
        aboutBtn.setOnAction(e -> showAbout());

        // Кнопка "Очистить память"
        Button clearBtn = new Button("Очистить память");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        // Красный стиль для опасного действия
        clearBtn.setStyle("-fx-background-color: #3d1414; -fx-text-fill: #ff5555; -fx-padding: 8; -fx-cursor: hand; -fx-border-color: #552222;");
        clearBtn.setOnAction(e -> clearMemory());

        Button qdrantDashboard = new Button("Dashboard Qdrant");
        qdrantDashboard.setMaxWidth(Double.MAX_VALUE);
        // Красный стиль для опасного действия
        qdrantDashboard.setStyle("-fx-background-color: #1c2230; -fx-text-fill: #cccccc; -fx-padding: 8; -fx-cursor: hand;");
        qdrantDashboard.setOnAction(e -> {
            try {
                // Qdrant dashboard URL
                Desktop.getDesktop().browse(new URI("http://localhost:6333/dashboard"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        sysButtons.getChildren().addAll(aboutBtn, qdrantDashboard, clearBtn);
        Label animLabel = new Label("🎬 Анимации");
        animLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-weight: bold;");

        // Пустой VBox для занятия пространства между эмоциями и кнопками
        VBox spacer = new VBox();

        // Финальная сборка сайдбара
        sidebar.getChildren().addAll(
                title,
                new Separator(),
                animLabel,
                animScroll,
                new Separator(),
                emotionSection,
                spacer,  // Занимает пространство в середине
                sysButtons
        );

        // Пустой VBox растягивается и прижимает кнопки вниз
        VBox.setVgrow(spacer, Priority.ALWAYS);

        return sidebar;
    }

    private VBox createOllamaStatusBox() {
        VBox ollamaBox = new VBox(8);
        ollamaBox.setStyle("-fx-border-color: #333333; -fx-padding: 10; -fx-background-color: #0d0d0d; -fx-border-radius: 3;");
        ollamaBox.setPrefWidth(220);

        Label title = new Label("🦙 Ollama LLM");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaaaaa;");

        // Инициализируем только те метки, которые реально будем использовать
        this.ollamaStatusLabel = new Label("● Контейнер: Ожидание...");
        this.ollamaStatusLabel.setStyle("-fx-text-fill: #ffaa00;");

        this.startOllamaButton = new Button("Запустить систему");
        this.startOllamaButton.setMaxWidth(Double.MAX_VALUE);
        this.startOllamaButton.setOnAction(e -> startOllamaDocker());


        ollamaBox.getChildren().addAll(
                title,
                ollamaStatusLabel,
                startOllamaButton
        );

        return ollamaBox;
    }

    private void startOllamaDocker() {

        appendChat("⏳ Запуск Ollama...\n");

        new Thread(() -> {

            try {

                // START ONLY OLLAMA
                dockerService.upQwen(".");

                appendChat("✅ Команда отправлена. Жду порт 11434...\n");

                int attempts = 0;

                while (attempts < 60) {

                    if (dockerService.isServiceReady("127.0.0.1", 11434)) {

                        Platform.runLater(() -> {

                            ollamaStatusLabel.setText("● Ollama: ✅ Online");

                            ollamaStatusLabel.setStyle(
                                    "-fx-text-fill: #44ff44;"
                            );

                            appendChat("✅ Ollama готова.\n");
                        });

                        return;
                    }

                    Thread.sleep(1000);

                    attempts++;
                }

                appendChat("❌ Ollama не ответила на порту 11434.\n");

            } catch (Exception e) {

                Platform.runLater(() ->
                        appendChat("❌ Ошибка Docker: "
                                + e.getMessage() + "\n")
                );
            }

        }).start();
    }
    private void executeCommand(String cmd) throws Exception {
        String[] command = getCommand(cmd);
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }

    private String[] getCommand(String cmd) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        return isWindows ? new String[]{"cmd", "/c", cmd} : new String[]{"/bin/bash", "-c", cmd};
    }

    private void toggleVtubeConnection() {
        if (brain == null) {
            appendChat("❌ Система еще не инициализирована\n");
            return;
        }

        if (brain.isVTubeStudioEnabled()) {
            // Отключаем соединение
            brain.disconnectVTubeStudio();
            updateVtubeStatus(false);
            appendChat("[" + timeFormat.format(new Date()) + "] ❌ VTube Studio отключена\n");

            if (vtubeLogger != null) {
                vtubeLogger.logConnection("❌ Отключено");
                vtubeLogger.close();
                vtubeLogger = null;
            }

            connectVtubeButton.setText("🔗 Подключить");
        } else {
            // Процесс подключения
            if (loadingIndicator != null) loadingIndicator.setVisible(true);
            connectVtubeButton.setDisable(true);
            appendChat("[" + timeFormat.format(new Date()) + "] ⏳ Подключение VTube Studio...\n");

            new Thread(() -> {
                try {
                    // Пытаемся авторизоваться в VTS
                    boolean success = brain.initializeVTubeStudio();

                    Platform.runLater(() -> {
                        if (success) {
                            // --- ОБНОВЛЯЕМ реестр на расширенный с BlushExpression ---
                            this.expressionRegistry = new ExpressionRegistry(VTSContext.getClient());

                            String logPath = "logs/vtube_studio_" + System.currentTimeMillis() + ".log";
                            vtubeLogger = new VTubeStudioLogger(logPath);
                            vtubeLogger.logConnection("✅ Подключено");

                            updateVtubeStatus(true);
                            appendChat("[" + timeFormat.format(new Date()) + "] ✅ VTube Studio подключена успешно!\n");

                            // Обновляем ComboBox с новым реестром (добавляем Blush)
                            initManualEmotionControl();
                            appendChat("✨ Добавлена эмоция: blush\n");

                            connectVtubeButton.setText("🔌 Отключить");
                        } else {
                            appendChat("[" + timeFormat.format(new Date()) + "] ❌ Не удалось подключить VTube Studio\n");
                            appendChat("⚠️ Проверьте: запущен ли плагин в VTS (порт 8001)?\n");
                        }

                        if (loadingIndicator != null) loadingIndicator.setVisible(false);
                        connectVtubeButton.setDisable(false);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        appendChat("[" + timeFormat.format(new Date()) + "] ❌ Ошибка: " + e.getMessage() + "\n");
                        if (loadingIndicator != null) loadingIndicator.setVisible(false);
                        connectVtubeButton.setDisable(false);
                    });
                }
            }).start();
        }
    }

    private VBox createTopPanel() {
        VBox topPanel = new VBox(10);
        topPanel.setPadding(new Insets(15));
        topPanel.setStyle("-fx-background-color: #12151f;");


        HBox statusesRow = new HBox(15);

        // ВЫЗЫВАЕМ ТВОИ МЕТОДЫ СТАТУСОВ ЗДЕСЬ:
        statusesRow.getChildren().addAll(
                createOllamaStatusBox(),  // Вот он! Теперь он появится в коде
                createVtubeStatusBox(),   // Твой метод для VTube
                createQdrantStatusBox()   // Твой метод для Базы Данных
        );

        topPanel.getChildren().addAll(statusesRow);
        return topPanel;
    }

    private VBox createVtubeStatusBox() {
        VBox vtubeBox = new VBox(8);
        vtubeBox.setStyle("-fx-border-color: #333333; -fx-padding: 10; -fx-background-color: #0d0d0d; -fx-border-radius: 3;");
        vtubeBox.setPrefWidth(200);

        Label title = new Label("🎭 VTube Studio");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaaaaa;");

        this.vtubeStatusLabel = new Label("● VTube: Отключено");
        this.vtubeStatusLabel.setStyle("-fx-text-fill: #ff4444;");

        this.connectVtubeButton = new Button("🔗 Подключить");
        this.connectVtubeButton.setMaxWidth(Double.MAX_VALUE);
        this.connectVtubeButton.setOnAction(e -> toggleVtubeConnection());

        // Индикатор загрузки (если он у тебя объявлен в полях)
        if (loadingIndicator == null) {
            loadingIndicator = new ProgressIndicator();
            loadingIndicator.setPrefSize(15, 15);
            loadingIndicator.setVisible(false);
        }

        HBox actionRow = new HBox(10, connectVtubeButton, loadingIndicator);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        vtubeBox.getChildren().addAll(title, vtubeStatusLabel, actionRow);
        return vtubeBox;
    }
    private VBox createQdrantStatusBox() {
        VBox qdrantBox = new VBox(8);
        qdrantBox.setStyle("-fx-border-color: #333333; -fx-padding: 10; -fx-background-color: #0d0d0d; -fx-border-radius: 3;");
        qdrantBox.setPrefWidth(200);

        Label title = new Label("📊 Qdrant DB");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaaaaa;");

        this.qdrantStatusLabel = new Label("● База: Ожидание...");
        this.qdrantStatusLabel.setStyle("-fx-text-fill: #ffaa00;");

        this.startQdrantButton = new Button("Запустить БД");
        this.startQdrantButton.setMaxWidth(Double.MAX_VALUE);
        this.startQdrantButton.setOnAction(e -> startQdrantDocker());

        qdrantBox.getChildren().addAll(title, qdrantStatusLabel, startQdrantButton);
        return qdrantBox;
    }


    private VBox createCenterPanel() {
        VBox center = new VBox();
        center.setPadding(new Insets(10));

        // Если chatArea еще не инициализирован
        if (chatArea == null) chatArea = new TextArea();

        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        // Тёмный фон чата с белым текстом
        chatArea.setStyle("-fx-control-inner-background: #0b0e14; -fx-text-fill: #ffffff; -fx-font-size: 11;");

        // Растягиваем текстовое поле во все стороны
        chatArea.setMaxHeight(Double.MAX_VALUE);
        chatArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        center.getChildren().add(chatArea);
        VBox.setVgrow(center, Priority.ALWAYS); // Важно для родителя
        return center;
    }

    private VBox createBottomPanel() {
        VBox bottom = new VBox(10);
        bottom.setPadding(new Insets(15));
        bottom.setStyle("-fx-background-color: #12151f;");

        // Инициализируем поля, если они вдруг null
        if (senderComboBox == null) {
            senderComboBox = new ComboBox<>();
            // ТЕПЕРЬ СТИЛЬ ПРИМЕНИТСЯ:
            senderComboBox.setStyle("-fx-base: #1c2230; -fx-text-fill: white;");
            senderComboBox.getItems().addAll("👤 Пользователь", "🎭 Нина", "✍️ Вручную");
            senderComboBox.setValue("👤 Пользователь");
        }
        if (customSenderField == null) {
            customSenderField = new TextField();
            customSenderField.setPromptText("Имя...");
            customSenderField.setVisible(false);
        }
        if (inputField == null) {
            inputField = new TextField();
        }

        senderComboBox.setOnAction(e -> customSenderField.setVisible(senderComboBox.getValue().contains("✍️")));

        HBox senderRow = new HBox(10);
        senderRow.setAlignment(Pos.CENTER_LEFT);
        // Проверяем каждое поле перед добавлением в Children
        senderRow.getChildren().addAll(new Label("От:"), senderComboBox, customSenderField);

        HBox inputRow = new HBox(10);
        inputField.setPrefHeight(40);
        inputField.setPromptText("Введите сообщение...");
        inputField.setStyle("-fx-background-color: #1c2230; -fx-text-fill: white;");
        inputField.setOnKeyPressed(e -> { if(e.getCode().toString().equals("ENTER")) sendMessage(); });
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendBtn = new Button("ОТПРАВИТЬ");
        sendBtn.setMinWidth(120);
        sendBtn.setPrefHeight(40);
        sendBtn.setStyle("-fx-background-color: #5c6bc0; -fx-text-fill: white; -fx-font-weight: bold;");
        sendBtn.setOnAction(e -> sendMessage());

        inputRow.getChildren().addAll(inputField, sendBtn);
        bottom.getChildren().addAll(senderRow, inputRow);

        return bottom;
    }

    private void initManualEmotionControl() {
        // Если реестр еще не создан, ничего не делаем
        if (expressionRegistry == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                manualEmotionComboBox.getItems().clear();

                // Получаем мапу всех эмоций из реестра
                var allExpressions = expressionRegistry.getAll();

                if (allExpressions != null && !allExpressions.isEmpty()) {
                    // Добавляем названия всех найденных эмоций в выпадающий список
                    manualEmotionComboBox.getItems().addAll(allExpressions.keySet());

                    // Устанавливаем действие при выборе эмоции
                    manualEmotionComboBox.setOnAction(event -> {
                        String selected = manualEmotionComboBox.getValue();
                        if (selected != null && brain != null) {
                            // Отправляем команду в VTube Studio через мозг
                            brain.triggerManualExpression(selected);
                            appendChat("🎭 [GUI] Активирована эмоция: " + selected + "\n");
                        }
                    });

                    // Определяем источник эмоций
                    boolean isFromVTube = allExpressions.containsKey("blush");
                    String source = isFromVTube ? "VTube Studio" : "локальный реестр";
                    appendChat("✅ Список эмоций загружен (" + source + "): " + allExpressions.size() + " шт.");
                } else {
                    appendChat("⚠️ Список эмоций пуст. Проверь настройки.\n");
                }
            } catch (Exception e) {
                appendChat("❌ Ошибка при заполнении списка эмоций: " + e.getMessage() + "\n");
            }
        });
    }

    private void sendMessage() {
        if (isProcessing || brain == null) return;
        String message = inputField.getText().trim();
        if (message.isEmpty()) return;

        inputField.clear();
        isProcessing = true;
        appendChat("[" + timeFormat.format(new Date()) + "] Вы: " + message + "\n");

        new Thread(() -> {
            try {
                String response = brain.think(message);

                Platform.runLater(() -> {
                    String timestamp = "[" + timeFormat.format(new Date()) + "]";

                    // 1. Ищем блоки мыслей в звездочках: *мысли*
                    // Используем регулярку, чтобы вытащить содержимое между звездочек
                    String thoughts = "";
                    String speech = response;

                    if (response.contains("*")) {
                        // Извлекаем первое попадание в звездочках (для примера)
                        // Можно усложнить, если мыслей много по всему тексту
                        int firstAsterisk = response.indexOf("*");
                        int lastAsterisk = response.lastIndexOf("*");

                        if (firstAsterisk != lastAsterisk) {
                            thoughts = response.substring(firstAsterisk + 1, lastAsterisk).trim();
                            // Убираем мысли из основной речи
                            speech = response.replace(response.substring(firstAsterisk, lastAsterisk + 1), "").trim();
                        }
                    }

                    // 2. Выводим мысли отдельной строкой, если они есть
                    if (!thoughts.isEmpty()) {
                        appendChat(timestamp + " 💭 (Мысли): " + thoughts + "\n");
                    }

                    // 3. Выводим саму речь Нины с переносом строки
                    appendChat(timestamp + " 🎭 Нина: " + speech + "\n");

                    isProcessing = false;
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendChat("❌ Ошибка: " + e.getMessage() + "\n");
                    isProcessing = false;
                });
            }
        }).start();
    }

    private void initializeBrainAsync() {
        new Thread(() -> {
            try {
                // 1. Пытаемся создать объект мозга (тут может быть пауза из-за Qdrant)
                this.brain = new AvatarBrain();

                // 2. Если создание прошло успешно, настраиваем зависимые модули
                Platform.runLater(() -> {
                    try {
                        // Инициализируем реестр эмоций и выпадающий список
                        this.expressionRegistry = new ExpressionRegistry(null);
                        initManualEmotionControl();

                        appendChat("🧠 Система инициализирована успешно.\n");
                    } catch (Exception e) {
                        appendChat("⚠️ Ошибка настройки модулей: " + e.getMessage() + "\n");
                    }
                });

            } catch (Exception e) {
                // Сюда попадем, если конструктор AvatarBrain() выдал ошибку (например, Qdrant Offline)
                Platform.runLater(() -> {
                    appendChat("⚠️ Система ожидает запуска сервисов. Запусти БД и Ollama кнопками выше.\n");
                });
            }
        }).start();
    }


    private void updateQdrantStatus(boolean isRunning) {
        Platform.runLater(() -> {
            if (isRunning) {
                qdrantStatusLabel.setText("● Qdrant: ✅ Работает");
                qdrantStatusLabel.setStyle("-fx-text-fill: #44ff44;");
            } else {
                qdrantStatusLabel.setText("● Qdrant: ❌ Не подключена");
                qdrantStatusLabel.setStyle("-fx-text-fill: #ff4444;");
            }
        });
    }

    private void appendChat(String text) {
        Platform.runLater(() -> {
            chatArea.appendText(text);
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void updateVtubeStatus(boolean online) {
        Platform.runLater(() -> {
            vtubeStatusLabel.setText(online ? "● VTube: Подключено" : "● VTube: Отключено");
            vtubeStatusLabel.setStyle("-fx-text-fill: " + (online ? "#44ff44" : "#ff4444") + ";");
        });
    }

    private void triggerAnimationFromUI(String anim) {
        if (brain != null) brain.getLauncher().triggerAnimation(anim);
    }




    /**
     * Очистить память с подтверждением
     */
    private void clearMemory() {
        if (!brain.isMemoryRunning()) {
            appendChat("❌ Система памяти не активна\n");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.WARNING);
        confirmDialog.setTitle("⚠️ Внимание!");
        confirmDialog.setHeaderText("ОЧИСТКА ПАМЯТИ");
        confirmDialog.setContentText("Вы уверены что хотите ПОЛНОСТЬЮ очистить всю память?\nЭто действие необратимо!");

        ButtonType deleteButton = new ButtonType("🗑️ Удалить всё", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButton = new ButtonType("❌ Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

        confirmDialog.getButtonTypes().setAll(deleteButton, cancelButton);

        var result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == deleteButton) {
            try {
                brain.clearAllMemories();
                appendChat("[" + timeFormat.format(new Date()) + "] 🗑️ Память полностью очищена\n");
            } catch (Exception e) {
                appendChat("❌ Ошибка при очистке памяти: " + e.getMessage() + "\n");
            }
        }
    }


    /**
     * Показать информацию о программе
     */
    private void showAbout() {
        String aboutText = """
        NeuralSoul v2.1
        
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
        alert.setHeaderText(null);

        // ===== ROOT STYLE =====
        alert.getDialogPane().setStyle("""
        -fx-background-color: #0b0e14;
        -fx-border-color: #1f2430;
        -fx-border-width: 1;
        """);

        // ===== HEADER =====
        VBox headerBox = new VBox(15);
        headerBox.setAlignment(Pos.CENTER);

        // Увеличенный логотип / заголовок
        HBox titleBox = createNeuralSoulLabel("NeuralSoul", "28", 80);
        titleBox.setScaleX(1.4);
        titleBox.setScaleY(1.4);
        titleBox.setStyle("-fx-padding: 10 0 10 0;");

        headerBox.getChildren().add(titleBox);

        // ===== TEXT AREA =====
        TextArea textArea = new TextArea(aboutText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setStyle("""
        -fx-control-inner-background: #0b0e14;
        -fx-background-color: #0b0e14;
        -fx-background-insets: 0;
        -fx-text-fill: #e0e0e0;
        -fx-highlight-fill: #2d4f67;
        -fx-highlight-text-fill: white;
        -fx-font-size: 13px;
        -fx-border-color: #1f2430;
        -fx-focus-color: transparent;
        -fx-faint-focus-color: transparent;
        """);

        // ===== CONTENT =====
        VBox contentBox = new VBox(15, headerBox, textArea);
        contentBox.setPadding(new Insets(15));
        contentBox.setStyle("-fx-background-color: #0b0e14;");

        alert.getDialogPane().setContent(contentBox);
        alert.getDialogPane().setPrefWidth(550);
        alert.getDialogPane().setPrefHeight(400);

        // ===== BUTTON STYLE =====
        Button okButton = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);

        okButton.setStyle("""
        -fx-background-color: #1f2430;
        -fx-text-fill: #e0e0e0;
        -fx-background-radius: 8;
        -fx-padding: 8 18 8 18;
        -fx-cursor: hand;
        """);

        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void startQdrantDocker() {

        startQdrantButton.setDisable(true);

        appendChat("\n⏳ Запуск Qdrant...\n");

        new Thread(() -> {

            try {

                // START ONLY QDRANT
                dockerService.upQdrant(".");

                appendChat("✅ Команда отправлена. Жду порт 6333...\n");

                int attempts = 0;

                while (attempts < 60) {

                    // SIMPLE PORT CHECK
                    if (dockerService.isServiceReady("127.0.0.1", 6333)) {

                        Platform.runLater(() -> {

                            updateQdrantStatus(true);

                            appendChat("✅ Qdrant ожил на порту 6333!\n");

                            startQdrantButton.setDisable(false);

                            if (brain != null) {
                                new Thread(() ->
                                        brain.initializeMemory()
                                ).start();
                            }
                        });

                        return;
                    }

                    Thread.sleep(1000);

                    attempts++;
                }

                appendChat("❌ Порт 6333 не открылся.\n");

            } catch (Exception e) {

                appendChat("❌ Ошибка: " + e.getMessage() + "\n");

            } finally {

                Platform.runLater(() ->
                        startQdrantButton.setDisable(false)
                );
            }
        }).start();
    }


    /**
     * Создает компонент с изображением mascot.png и текстом
     */
    private HBox createNeuralSoulLabel(String text, String fontSize, int imageSize) {
        HBox container = new HBox(5);
        container.setAlignment(Pos.CENTER);

        try {
            // Загружаем изображение mascot.png
            Image mascotImage = new Image(getClass().getResourceAsStream("/img/mascot.png"));
            ImageView imageView = new ImageView(mascotImage);
            imageView.setFitWidth(imageSize);
            imageView.setFitHeight(imageSize);
            imageView.setPreserveRatio(true);

            Label textLabel = new Label(text);
            textLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: " + fontSize + "; -fx-font-weight: bold;");

            container.getChildren().addAll(imageView, textLabel);
        } catch (Exception e) {
            // Если изображение не загрузилось, просто показываем текст
            Label textLabel = new Label(text);
            textLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: " + fontSize + "; -fx-font-weight: bold;");
            container.getChildren().add(textLabel);
        }

        return container;
    }
}




