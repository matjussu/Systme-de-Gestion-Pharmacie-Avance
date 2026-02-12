package com.sgpa.utils;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FontLoader {

    private static final Logger logger = LoggerFactory.getLogger(FontLoader.class);

    public static void loadFonts() {
        String[] fonts = {
            "/fonts/Inter-Regular.ttf",
            "/fonts/Inter-Medium.ttf",
            "/fonts/Inter-SemiBold.ttf",
            "/fonts/Inter-Bold.ttf",
            "/fonts/Poppins-SemiBold.ttf",
            "/fonts/Poppins-Bold.ttf"
        };

        for (String fontPath : fonts) {
            try {
                Font font = Font.loadFont(FontLoader.class.getResourceAsStream(fontPath), 14);
                if (font != null) {
                    logger.info("Police chargee: {}", font.getName());
                } else {
                    logger.warn("Impossible de charger la police: {}", fontPath);
                }
            } catch (Exception e) {
                logger.warn("Erreur chargement police {}: {}", fontPath, e.getMessage());
            }
        }
    }
}
