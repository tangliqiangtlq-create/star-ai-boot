package org.jeecg.common.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;

public class ExcelUtilExt {

  public static CellStyle buildAmountCellStyle(Sheet sheet) {

    CellStyle styleRow = sheet.getWorkbook().createCellStyle();
    // styleRow.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleRow.setAlignment(HorizontalAlignment.RIGHT);
    styleRow.setVerticalAlignment(VerticalAlignment.CENTER);
    styleRow.setBorderTop(BorderStyle.THIN);
    styleRow.setBorderBottom(BorderStyle.THIN);
    styleRow.setBorderLeft(BorderStyle.THIN);
    styleRow.setBorderRight(BorderStyle.THIN);
    styleRow.setWrapText(true);

    Font font = sheet.getWorkbook().createFont();
    font.setFontHeight((short) 10);
    font.setFontHeightInPoints((short) 10);
    styleRow.setFont(font);

    styleRow.setFont(font);

    return styleRow;
  }

  public static CellStyle buildLeftCellStyle(Sheet sheet) {

    CellStyle styleRow = sheet.getWorkbook().createCellStyle();
    // styleRow.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    styleRow.setAlignment(HorizontalAlignment.LEFT);
    styleRow.setVerticalAlignment(VerticalAlignment.CENTER);
    styleRow.setBorderTop(BorderStyle.THIN);
    styleRow.setBorderBottom(BorderStyle.THIN);
    styleRow.setBorderLeft(BorderStyle.THIN);
    styleRow.setBorderRight(BorderStyle.THIN);
    styleRow.setWrapText(true);

    Font font = sheet.getWorkbook().createFont();
    font.setFontHeight((short) 10);
    font.setFontHeightInPoints((short) 10);
    styleRow.setFont(font);

    styleRow.setFont(font);

    return styleRow;
  }

  public static void customCellStyle(Sheet sheet) {
    int lastNum = sheet.getRow(0).getLastCellNum();
    int lastRowNum = sheet.getLastRowNum();

    // Create style object
    CellStyle style = sheet.getWorkbook().createCellStyle();
    style.setFillForegroundColor(IndexedColors.GREY_40_PERCENT.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
    style.setWrapText(true);

    CellStyle styleRow = sheet.getWorkbook().createCellStyle();
    styleRow.setAlignment(HorizontalAlignment.CENTER);
    styleRow.setVerticalAlignment(VerticalAlignment.CENTER);
    styleRow.setBorderTop(BorderStyle.THIN);
    styleRow.setBorderBottom(BorderStyle.THIN);
    styleRow.setBorderLeft(BorderStyle.THIN);
    styleRow.setBorderRight(BorderStyle.THIN);
    styleRow.setWrapText(true);

    Font font = sheet.getWorkbook().createFont();
    font.setFontHeight((short) 10);
    font.setFontHeightInPoints((short) 10);
    styleRow.setFont(font);
    style.setFont(font);

    // Adaptive column width
    for (int i = 0; i < lastNum; i++) {
      sheet.getRow(0).getCell(i).setCellStyle(style);

      // Calculate the maximum width of a column
      int maxColumnWidth = 0;
      for (int j = 0; j <= lastRowNum; j++) {
        if (sheet.getRow(j) != null && sheet.getRow(j).getCell(i) != null) {
          String cellValue = sheet.getRow(j).getCell(i).toString();
          if (cellValue != null) {
            // Optimized width calculation: Chinese character width coefficient 2 times, English character 1 times, add minimum width guarantee
            int textWidth = 0;
            for (char c : cellValue.toCharArray()) {
              if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                textWidth += 512; // Increase the Chinese character width factor to2times
              } else {
                textWidth += 256; // English character width
              }
            }
            // Add extra width to fit borders and spacing
            textWidth += 512; // extra buffer
            maxColumnWidth = Math.max(maxColumnWidth, textWidth);
          }
        }
      }

      // Set column width, limit maximum width and ensure minimum width
      maxColumnWidth = Math.max(maxColumnWidth, 10 * 256); // minimum width10characters
      maxColumnWidth = Math.min(maxColumnWidth, 255 * 256); // Maximum width limit
      sheet.setColumnWidth(i, maxColumnWidth);

      // Apply cell style
      for (int j = 1; j <= lastRowNum; j++) {
        if (sheet.getRow(j) != null && sheet.getRow(j).getCell(i) != null) {
          sheet.getRow(j).getCell(i).setCellStyle(styleRow);
        }
      }
    }
  }
}
