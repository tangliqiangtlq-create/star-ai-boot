package org.jeecg.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * @author TD_L @ClassName CurrenyFormatUtils @Desc Currency amount formatting tool class @Date 2021/5/9 10:39 @Version
 */
public class CurrenyFormatUtils {

  /**
   * Convert the amount to Number of digits, add 0 if necessary
   *
   * @param amount
   * @return
   */
  public static String amountFormat(String amount, int num) {
    String amt = String.valueOf(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP));
    amt = amt.replace(".", "");
    amt = getCount(amt, num);
    return amt;
  }

  /**
   * Automatic left completion if Number of digits is insufficient
   *
   * @param amount
   * @return
   */
  /**
   * If the Number of digits is insufficient, it will be automatically left-completed. If it is too long, the first num digits will be intercepted.
   *
   * @param amount input string
   * @param num target length
   * @return the completed or truncated string
   */
  public static String getCount(String amount, int num) {
    int number = amount.length();
    if (number < num) {
      // Insufficient length, pad left with zeros
      for (int j = 0; j < num - number; j++) {
        amount = "0" + amount;
      }
    } else if (number > num) {
      // The length is too long, intercept the first num digits
      amount = amount.substring(0, num);
    }
    return amount;
  }

  /**
   * Format numbers to be displayed in thousandths;
   *
   * @param The number to be formatted;
   * @return
   */
  public static String fmtMicrometer(String text, int num) {
    // Validate input
    if (text == null || text.trim().isEmpty()) {
      return "0" + (num > 0 ? "." + "0".repeat(num) : "");
    }

    // Create DecimalFormat pattern
    StringBuilder pattern = new StringBuilder("###,##0");
    if (num > 0) {
      pattern.append(".").append("0".repeat(num));
    }

    DecimalFormat df = new DecimalFormat(pattern.toString());

    // Parse numbers
    double number;
    try {
      number = Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return "0" + (num > 0 ? "." + "0".repeat(num) : "");
    }

    return df.format(number);
  }
}
