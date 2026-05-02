package org.example.vision;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Утилита для снятия и сохранения скриншотов
 */
public class ScreenshotUtil {
    private static final Logger LOG = Logger.getLogger(ScreenshotUtil.class.getName());

    private static final String SCREENSHOT_DIR = "./screenshots";

    static {
        // Создаём директорию для скриншотов при инициализации
        try {
            Files.createDirectories(Paths.get(SCREENSHOT_DIR));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Ошибка создания директории скриншотов: " + e.getMessage());
        }
    }

    /**
     * Делает скриншот экрана и сохраняет его
     *
     * @return Путь к сохранённому файлу
     */
    public static String captureScreen() throws Exception {
        LOG.log(Level.FINE, "📸 Снимаем скриншот...");

        try {
            // Получаем размер экрана
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

            // Снимаем скриншот
            BufferedImage screenshot = new Robot().createScreenCapture(screenRect);

            // Генерируем имя файла с timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());
            String filename = SCREENSHOT_DIR + "/screenshot_" + timestamp + ".png";

            // Сохраняем файл
            File outputFile = new File(filename);
            ImageIO.write(screenshot, "PNG", outputFile);

            LOG.log(Level.FINE, "✅ Скриншот сохранён: " + filename);
            return filename;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "❌ Ошибка при снятии скриншота: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Снимает скриншот конкретного окна
     *
     * @param windowName Имя окна (опционально)
     * @return Путь к файлу
     */
    public static String captureWindow(String windowName) throws Exception {
        // На Windows это сложнее - используем Robot для всего экрана
        // Полная реализация требует JNI или специальных библиотек
        return captureScreen();
    }

    /**
     * Получает размеры экрана
     */
    public static Dimension getScreenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    /**
     * Очищает старые скриншоты (старше N дней)
     */
    public static void cleanupOldScreenshots(int daysOld) {
        try {
            long now = System.currentTimeMillis();
            long millisecondsPerDay = 24 * 60 * 60 * 1000;
            long cutoffTime = now - (daysOld * millisecondsPerDay);

            File dir = new File(SCREENSHOT_DIR);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.startsWith("screenshot_") && name.endsWith(".png"));

                if (files != null) {
                    int deleted = 0;
                    for (File file : files) {
                        if (file.lastModified() < cutoffTime) {
                            if (file.delete()) {
                                deleted++;
                            }
                        }
                    }
                    LOG.log(Level.INFO, "🗑️ Удалено старых скриншотов: " + deleted);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Ошибка при очистке скриншотов: " + e.getMessage());
        }
    }
}

