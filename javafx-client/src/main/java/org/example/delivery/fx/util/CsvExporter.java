package org.example.delivery.fx.util;

import org.example.delivery.model.Client;
import org.example.delivery.model.DeliveryOrder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Экспорт данных в CSV (UTF-8 с BOM, разделитель ';') —
 * файл корректно открывается в Excel с русскими региональными настройками.
 */
public final class CsvExporter {
    private CsvExporter() {
    }

    public static void exportOrders(Path path, List<DeliveryOrder> orders) {
        String[] header = {
                "ID", "Клиент", "Адрес отправления", "Адрес доставки",
                "Описание", "Вес, кг", "Стоимость, руб.", "Создан", "Статус"
        };
        try (PrintWriter writer = open(path)) {
            writer.println(join(header));
            for (DeliveryOrder order : orders) {
                writer.println(join(new String[]{
                        String.valueOf(order.getId()),
                        nullSafe(order.getClientName()),
                        nullSafe(order.getPickupAddress()),
                        nullSafe(order.getDeliveryAddress()),
                        nullSafe(order.getDescription()),
                        order.getWeight().toPlainString(),
                        order.getPrice().toPlainString(),
                        order.getCreatedAt().format(UiText.DATE_TIME),
                        UiText.statusName(order.getStatus())
                }));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить файл " + path.toAbsolutePath()
                    + ". Проверьте, что файл не открыт в другой программе.", exception);
        }
    }

    public static void exportClients(Path path, List<Client> clients) {
        String[] header = {"ID", "ФИО", "Телефон", "Email", "Адрес"};
        try (PrintWriter writer = open(path)) {
            writer.println(join(header));
            for (Client client : clients) {
                writer.println(join(new String[]{
                        String.valueOf(client.getId()),
                        nullSafe(client.getFullName()),
                        nullSafe(client.getPhone()),
                        nullSafe(client.getEmail()),
                        nullSafe(client.getAddress())
                }));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить файл " + path.toAbsolutePath()
                    + ". Проверьте, что файл не открыт в другой программе.", exception);
        }
    }

    private static PrintWriter open(Path path) throws IOException {
        OutputStream outputStream = Files.newOutputStream(path);
        // BOM, чтобы Excel правильно определил кодировку UTF-8
        outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        return new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    private static String join(String[] values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(';');
            }
            line.append(escape(values[i]));
        }
        return line.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(";") || escaped.contains("\"") || escaped.contains("\n")) {
            return '"' + escaped + '"';
        }
        return escaped;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
