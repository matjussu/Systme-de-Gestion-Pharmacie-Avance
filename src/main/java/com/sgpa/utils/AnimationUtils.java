package com.sgpa.utils;

import javafx.animation.*;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Utilitaires d'animation pour l'interface ApotiCare.
 */
public class AnimationUtils {

    private AnimationUtils() {}

    /**
     * Anime un compteur de fromValue a toValue sur la duree specifiee.
     */
    public static void animateCounter(Label label, int fromValue, int toValue, Duration duration) {
        IntegerProperty counter = new SimpleIntegerProperty(fromValue);
        counter.addListener((obs, oldVal, newVal) ->
                label.setText(String.valueOf(newVal.intValue())));
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(counter, fromValue)),
                new KeyFrame(duration, new KeyValue(counter, toValue, Interpolator.EASE_OUT))
        );
        timeline.play();
    }

    /**
     * Anime un compteur avec formatage personnalise.
     */
    public static void animateCounter(Label label, int fromValue, int toValue, Duration duration, String format) {
        IntegerProperty counter = new SimpleIntegerProperty(fromValue);
        counter.addListener((obs, oldVal, newVal) ->
                label.setText(String.format(format, newVal.intValue())));
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(counter, fromValue)),
                new KeyFrame(duration, new KeyValue(counter, toValue, Interpolator.EASE_OUT))
        );
        timeline.play();
    }

    /**
     * Effet de pulsation lumineuse sur un noeud (pour alertes).
     */
    public static Timeline pulseGlow(Node node, Color glowColor) {
        DropShadow glow = new DropShadow(15, glowColor);
        glow.setSpread(0.1);
        node.setEffect(glow);
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 10)),
                new KeyFrame(Duration.millis(800), new KeyValue(glow.radiusProperty(), 25, Interpolator.EASE_BOTH))
        );
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
        return pulse;
    }

    /**
     * Entree echelonnee avec fade + slide vertical.
     */
    public static void staggerSlideIn(Node... nodes) {
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            node.setOpacity(0);
            node.setTranslateY(20);
            FadeTransition fade = new FadeTransition(Duration.millis(400), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setDelay(Duration.millis(i * 100));
            TranslateTransition slide = new TranslateTransition(Duration.millis(400), node);
            slide.setFromY(20);
            slide.setToY(0);
            slide.setDelay(Duration.millis(i * 100));
            slide.setInterpolator(Interpolator.EASE_OUT);
            fade.play();
            slide.play();
        }
    }

    /**
     * Entree echelonnee avec fade + slide horizontal (depuis la droite).
     */
    public static void staggerSlideInFromRight(Node... nodes) {
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            node.setOpacity(0);
            node.setTranslateX(30);
            FadeTransition fade = new FadeTransition(Duration.millis(400), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setDelay(Duration.millis(i * 100));
            TranslateTransition slide = new TranslateTransition(Duration.millis(400), node);
            slide.setFromX(30);
            slide.setToX(0);
            slide.setDelay(Duration.millis(i * 100));
            slide.setInterpolator(Interpolator.EASE_OUT);
            fade.play();
            slide.play();
        }
    }

    /**
     * Scale + fade d'entree (pour dialogs).
     */
    public static void scaleIn(Node node) {
        node.setOpacity(0);
        node.setScaleX(0.85);
        node.setScaleY(0.85);
        FadeTransition fade = new FadeTransition(Duration.millis(250), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(250), node);
        scale.setFromX(0.85);
        scale.setFromY(0.85);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
        scale.play();
    }

    /**
     * Scale + fade de sortie (pour dialogs).
     */
    public static void scaleOut(Node node, Runnable onFinished) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(1);
        fade.setToValue(0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(200), node);
        scale.setToX(0.9);
        scale.setToY(0.9);
        scale.setInterpolator(Interpolator.EASE_IN);
        fade.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        fade.play();
        scale.play();
    }

    /**
     * Cree un overlay de chargement.
     */
    public static StackPane createLoadingOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        StackPane overlay = new StackPane(spinner);
        overlay.getStyleClass().add("loading-overlay");
        return overlay;
    }

    /**
     * Applique un effet scale au hover sur un noeud.
     */
    public static void applyHoverScale(Node node, double scaleFactor) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), node);
        scaleUp.setToX(scaleFactor);
        scaleUp.setToY(scaleFactor);
        scaleUp.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(200), node);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);
        scaleDown.setInterpolator(Interpolator.EASE_OUT);

        node.setOnMouseEntered(e -> scaleUp.playFromStart());
        node.setOnMouseExited(e -> scaleDown.playFromStart());
    }
}
