package com.sgpa.controller;

import com.sgpa.model.Utilisateur;
import com.sgpa.utils.AnimationUtils;
import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.util.Duration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseController {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    protected Utilisateur currentUser;
    protected DashboardController dashboardController;

    public void setCurrentUser(Utilisateur user) {
        this.currentUser = user;
        onUserSet();
    }

    public void setDashboardController(DashboardController controller) {
        this.dashboardController = controller;
    }

    protected void onUserSet() {
    }

    /**
     * Appelee quand une vue cachee est re-affichee.
     * Les sous-controleurs peuvent surcharger pour rafraichir les donnees.
     */
    public void onViewDisplayed() {
    }

    public Utilisateur getCurrentUser() {
        return currentUser;
    }

    protected static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    protected void runAsync(javafx.concurrent.Task<?> task) {
        EXECUTOR.submit(task);
    }

    // --- Methodes d'animation utilitaires ---

    protected void fadeIn(Node node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(100), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    protected void fadeOut(Node node, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(100), node);
        ft.setFromValue(node.getOpacity());
        ft.setToValue(0);
        if (onFinished != null) {
            ft.setOnFinished(e -> onFinished.run());
        }
        ft.play();
    }

    protected void slideInFromRight(Node node) {
        node.setOpacity(0);
        node.setTranslateX(15);
        FadeTransition fade = new FadeTransition(Duration.millis(100), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(100), node);
        slide.setFromX(15);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
        slide.play();
    }

    protected void staggerFadeIn(Node... nodes) {
        AnimationUtils.staggerSlideIn(nodes);
    }

    protected void scaleOnHover(Node node, double scaleFactor) {
        AnimationUtils.applyHoverScale(node, scaleFactor);
    }

    protected void setupResponsiveTable(TableView<?> tableView) {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }
}
