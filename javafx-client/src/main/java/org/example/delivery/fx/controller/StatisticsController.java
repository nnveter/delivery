package org.example.delivery.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.example.delivery.fx.util.UiText;
import org.example.delivery.service.DeliveryStatistics;

/** Контроллер окна статистики: отображает рассчитанные Service показатели. */
public class StatisticsController {
    @FXML private Label statTotalClients;
    @FXML private Label statTotalOrders;
    @FXML private Label statActiveOrders;
    @FXML private Label statDelivered;
    @FXML private Label statCancelled;
    @FXML private Label statRevenue;
    @FXML private Label statAvgPrice;
    @FXML private Label statAvgWeight;
    @FXML private Label statMinPrice;
    @FXML private Label statMaxPrice;
    @FXML private Label statDeliveredShare;

    public void show(DeliveryStatistics statistics) {
        statTotalClients.setText(String.valueOf(statistics.totalClients()));
        statTotalOrders.setText(String.valueOf(statistics.totalOrders()));
        statActiveOrders.setText(String.valueOf(statistics.activeOrders()));
        statDelivered.setText(String.valueOf(statistics.deliveredOrders()));
        statCancelled.setText(String.valueOf(statistics.cancelledOrders()));
        statRevenue.setText(UiText.money(statistics.deliveredRevenue()));
        statAvgPrice.setText(UiText.money(statistics.averageOrderPrice()));
        statAvgWeight.setText(UiText.weight(statistics.averageOrderWeight()));
        statMinPrice.setText(UiText.money(statistics.minOrderPrice()));
        statMaxPrice.setText(UiText.money(statistics.maxOrderPrice()));
        statDeliveredShare.setText(String.format("%.1f %%", statistics.deliveredSharePercent()));
    }

    @FXML
    private void onClose() {
        statTotalClients.getScene().getWindow().hide();
    }
}
