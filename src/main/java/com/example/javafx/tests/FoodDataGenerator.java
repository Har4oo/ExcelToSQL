package com.example.javafx.tests;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Random;

public class FoodDataGenerator {

    public static void main(String[] args) {
        String filePath = "UltimateFoodTest.xlsx";
        Random random = new Random();

        String[] dishes = {"Spaghetti", "Tacos", "Sushi", "Burger", "Salad", "Pizza", "Curry", "Ice Cream", "Steak", "Pancakes"};
        String[] adjectives = {"Spicy", "Sweet", "Sour", "Savory", "Crunchy", "Mushy", "Gourmet", "Questionable", "Burnt", "Raw"};

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Food Chaos");

            // Create a Date CellStyle for our date column
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            // 1. Create Headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {"food_id", "dish_name", "calories", "price", "is_vegan", "date_invented", "chef_notes"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 2. Generate 1000 rows of random food data
            for (int i = 1; i <= 1000; i++) {
                Row row = sheet.createRow(i);

                // food_id (Integer)
                row.createCell(0).setCellValue(i);

                // dish_name (String)
                String randomFood = adjectives[random.nextInt(adjectives.length)] + " " + dishes[random.nextInt(dishes.length)];
                row.createCell(1).setCellValue(randomFood);

                // calories (Integer - randomly leaving some blank to test your NULL fix!)
                if (random.nextInt(10) > 1) { // 80% chance to have data
                    row.createCell(2).setCellValue(random.nextInt(2000) + 50);
                }

                // price (Decimal)
                row.createCell(3).setCellValue(Math.round((random.nextDouble() * 100) * 100.0) / 100.0);

                // is_vegan (Boolean)
                row.createCell(4).setCellValue(random.nextBoolean());

                // date_invented (Date)
                Cell dateCell = row.createCell(5);
                // Random date between 1900 and 2026
                LocalDate randomDate = LocalDate.of(1900 + random.nextInt(127), 1 + random.nextInt(12), 1 + random.nextInt(28));
                dateCell.setCellValue(randomDate);
                dateCell.setCellStyle(dateStyle);

                // chef_notes (String - lots of nulls here to test robustness)
                if (random.nextBoolean()) {
                    row.createCell(6).setCellValue("Needs more salt.");
                }
            }

            // Write the output to a file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }

            System.out.println("Success! Created 1000 rows of random food data at: " + filePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}