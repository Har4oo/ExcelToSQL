package com.example.javafx.tests;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

public class CarDataChaosGenerator {

    public static void main(String[] args) {
        String filePath = "CarChaosStressTest.xlsx";
        Random random = new Random();

        // Data source pools
        String[] makes = {"Toyota", "Ford", "Tesla", "BMW", "Chevrolet", "Honda", "Subaru", "Rivian", "Mercedes", "Ferrari"};
        String[][] models = {
                {"Camry", "Corolla", "RAV4", "Tundra"}, {"F-150", "Mustang", "Explorer", "Bronco"},
                {"Model 3", "Model Y", "Model S", "Cybertruck"}, {"3 Series", "X5", "m3", "i8"},
                {"Silverado", "Tahoe", "Bolt", "Malibu"}, {"Civic", "Accord", "CR-V", "Pilot"},
                {"Outback", "Impreza", "Forester", "WRX"}, {"R1T", "R1S", "R2", "R3"},
                {"C-Class", "E-Class", "G-Wagon", "EQS"}, {"488", "Roma", "F8", "Purosangue"}
        };
        String[] colors = {"Black", "White", "Silver", "Red", "Blue", "Emerald Green", "Carbon Fiber", "Burnt Orange"};
        String[] fuelTypes = {"Gasoline", "Diesel", "Electric", "Hybrid", "Hydrogen"};
        String[] noteSnippets = {"Needs oil change.", "Tires rotated.", "Minor scratch on bumper.", "Engine noise identified.",
                "Supercharger working flawlessly.", "Customer reported weird smell.", "Brakes squeaking.",
                "Passed inspection with flying colors.", "This car is basically a rocket ship.", "Recall serviced."};

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Car Data Chaos");

            // 1. Create different Date Styles for extra robustness testing
            CellStyle dateStyle1 = workbook.createCellStyle(); // YYYY-MM-DD
            dateStyle1.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            CellStyle dateStyle2 = workbook.createCellStyle(); // MM/DD/YYYY
            dateStyle2.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("m/d/yy"));

            // 2. Create Header Row (15 columns)
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "car_id", "car_vin", "make", "model", "year",
                    "price", "odometer_miles", "fuel_type", "is_all_wheel_drive", "color",
                    "engine_l", "num_seats", "date_manufactured", "last_service_date", "maintenance_notes"
            };
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 3. Generate 2,000 Rows of Car Chaos
            for (int i = 1; i <= 2000; i++) {
                Row row = sheet.createRow(i);
                int makeIndex = random.nextInt(makes.length);
                String make = makes[makeIndex];
                String model = models[makeIndex][random.nextInt(models[makeIndex].length)];

                // COL 0: car_id (Integer)
                row.createCell(0).setCellValue(i);

                // COL 1: car_vin (String, Unique UUID)
                row.createCell(1).setCellValue(UUID.randomUUID().toString().substring(0, 17).toUpperCase());

                // COL 2 & 3: Make/Model (String)
                row.createCell(2).setCellValue(make);
                row.createCell(3).setCellValue(model);

                // COL 4: Year (Integer, 1990-2026 - randomly leaving blanks to test NULL integer fix)
                if (random.nextInt(10) > 0) { // 90% chance to have data
                    row.createCell(4).setCellValue(1990 + random.nextInt(37));
                }

                // COL 5: Price (Decimal, $15,000.00 - $150,000.00)
                row.createCell(5).setCellValue(15000 + (150000 - 15000) * random.nextDouble());

                // COL 6: Odometer (Huge Integer - 0 to 200,000)
                row.createCell(6).setCellValue(random.nextInt(200000));

                // COL 7 & 8: Fuel/AWD (String/Boolean - Testing mixed blanks here)
                if (random.nextInt(10) > 2) { // 70% chance to have data
                    row.createCell(7).setCellValue(fuelTypes[random.nextInt(fuelTypes.length)]);
                }
                row.createCell(8).setCellValue(random.nextBoolean());

                // COL 9: Color (String)
                row.createCell(9).setCellValue(colors[random.nextInt(colors.length)]);

                // COL 10: Engine Liters (Small Decimal, e.g. 1.5, 2.0, 5.7 - making 'Electric' cars NULL)
                if (random.nextBoolean() && makeIndex != 2 && makeIndex != 7) {
                    row.createCell(10).setCellValue(1.0 + (6.0 - 1.0) * random.nextDouble());
                }

                // COL 11: Num Seats (Integer - 2 to 8)
                row.createCell(11).setCellValue(2 + random.nextInt(7));

                // COL 12: Date Manufactured (Date Trap 1 - YYYY-MM-DD)
                Cell dateCell1 = row.createCell(12);
                LocalDate manufDate = LocalDate.of(1990 + random.nextInt(37), 1 + random.nextInt(12), 1 + random.nextInt(28));
                dateCell1.setCellValue(Date.from(manufDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                dateCell1.setCellStyle(dateStyle1);

                // COL 13: Last Service Date (Date Trap 2 - MM/DD/YYYY - Extremely high Null rate)
                if (random.nextInt(10) == 0) { // Only 10% chance to have data
                    Cell dateCell2 = row.createCell(13);
                    LocalDate serviceDate = LocalDate.now().minusDays(random.nextInt(1000));
                    dateCell2.setCellValue(Date.from(serviceDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                    dateCell2.setCellStyle(dateStyle2);
                }

                // COL 14: Maintenance Notes (String - Testing long text and blanks)
                if (random.nextBoolean()) {
                    // Generate a long string by combining snippets
                    StringBuilder notes = new StringBuilder();
                    int numSnippets = 1 + random.nextInt(4);
                    for (int j = 0; j < numSnippets; j++) {
                        notes.append(noteSnippets[random.nextInt(noteSnippets.length)]).append(" ");
                    }
                    row.createCell(14).setCellValue(notes.toString().trim());
                }
            }

            // 4. Set Column Widths for readability in Excel (Doesn't affect processing)
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 5. Write the output file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }

            System.out.println("Success! Car Chaos Stress Test file created (2,000 rows, 15 columns) at: " + filePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}