package org.example.delivery.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.delivery.model.Client;
import org.example.delivery.model.DeliveryOrder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ExcelExporter {
    public Path export(Path path, List<Client> clients, List<DeliveryOrder> orders) {
        try (Workbook workbook = new XSSFWorkbook()) {
            writeClients(workbook.createSheet("Клиенты"), clients);
            writeOrders(workbook.createSheet("Заказы"), orders);
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                workbook.write(outputStream);
            }
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось экспортировать данные в Excel.", exception);
        }
    }

    private void writeClients(Sheet sheet, List<Client> clients) {
        Row header = sheet.createRow(0);
        String[] columns = {"ID", "ФИО", "Телефон", "Email", "Адрес"};
        writeHeader(header, columns);
        for (int i = 0; i < clients.size(); i++) {
            Client client = clients.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(client.getId());
            row.createCell(1).setCellValue(client.getFullName());
            row.createCell(2).setCellValue(client.getPhone());
            row.createCell(3).setCellValue(client.getEmail() == null ? "" : client.getEmail());
            row.createCell(4).setCellValue(client.getAddress());
        }
        autoSize(sheet, columns.length);
    }

    private void writeOrders(Sheet sheet, List<DeliveryOrder> orders) {
        Row header = sheet.createRow(0);
        String[] columns = {
                "ID", "Клиент ID", "Клиент", "Адрес отправления", "Адрес доставки",
                "Описание", "Вес, кг", "Стоимость, руб.", "Создан", "Статус"
        };
        writeHeader(header, columns);
        for (int i = 0; i < orders.size(); i++) {
            DeliveryOrder order = orders.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(order.getId());
            row.createCell(1).setCellValue(order.getClientId());
            row.createCell(2).setCellValue(order.getClientName());
            row.createCell(3).setCellValue(order.getPickupAddress());
            row.createCell(4).setCellValue(order.getDeliveryAddress());
            row.createCell(5).setCellValue(order.getDescription());
            row.createCell(6).setCellValue(order.getWeight().doubleValue());
            row.createCell(7).setCellValue(order.getPrice().doubleValue());
            row.createCell(8).setCellValue(order.getCreatedAt().toString());
            row.createCell(9).setCellValue(order.getStatus().name());
        }
        autoSize(sheet, columns.length);
    }

    private void writeHeader(Row row, String[] columns) {
        for (int i = 0; i < columns.length; i++) {
            row.createCell(i).setCellValue(columns[i]);
        }
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
