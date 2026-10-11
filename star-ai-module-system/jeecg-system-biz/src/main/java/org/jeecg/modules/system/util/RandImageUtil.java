package org.jeecg.modules.system.util;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

/**
 * Login captcha utility
 * @author: jeecg-boot
 */
public class RandImageUtil {

    public static final String KEY = "JEECG_LOGIN_KEY";

    /**
     * Define image size
     */
    private static final int WIDTH = 105;
    /**
     * Define image size
     */
    private static final int HEIGHT = 35;

    /**
     * Define interference line count
     */
    private static final int COUNT = 200;

    /**
     * Interference line length = 1.414 * lineWidth
     */
    private static final int LINE_WIDTH = 2;

    /**
     * Image format
     */
    private static final String IMG_FORMAT = "JPEG";

    /**
     * Base64 image prefix.
     */
    private static final String BASE64_PRE = "data:image/jpg;base64,";

    /**
     * Return image directly via response
     * @param response
     * @param resultCode
     * @throws IOException
     */
    public static void generate(HttpServletResponse response, String resultCode) throws IOException {
        BufferedImage image = getImageBuffer(resultCode);
        // Output image to page
        ImageIO.write(image, IMG_FORMAT, response.getOutputStream());
    }

    /**
     * Generate base64 string
     * @param resultCode
     * @return
     * @throws IOException
     */
    public static String generate(String resultCode) throws IOException {
        BufferedImage image = getImageBuffer(resultCode);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        //Write into stream
        ImageIO.write(image, IMG_FORMAT, byteStream);
        //Convert to bytes
        byte[] bytes = byteStream.toByteArray();
        //Convert to base64 string
        String base64 = Base64.getEncoder().encodeToString(bytes).trim();
        //Delete \r\n
        base64 = base64.replaceAll("\n", "").replaceAll("\r", "");

        //Write to specified position
        //ImageIO.write(bufferedImage, "png", new File(""));

        return BASE64_PRE+base64;
    }

    private static BufferedImage getImageBuffer(String resultCode){
        // Create image in memory
        final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        // Get graphics context
        final Graphics2D graphics = (Graphics2D) image.getGraphics();
        // Set background color
        // ---1
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        // Set border color
//		graphics.setColor(getRandColor(100, 200)); // ---2
        graphics.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        // SHA1PRNG is a common PRNG used to mitigate weak randomness
        SecureRandom random;
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            random = new SecureRandom();
        }
        // Draw noise lines so captcha is harder to detect
        for (int i = 0; i < COUNT; i++) {
            // ---3
            graphics.setColor(getRandColor(150, 200));

            // Keep drawing inside the border
            final int x = random.nextInt(WIDTH - LINE_WIDTH - 1) + 1;
            final int y = random.nextInt(HEIGHT - LINE_WIDTH - 1) + 1;
            final int xl = random.nextInt(LINE_WIDTH);
            final int yl = random.nextInt(LINE_WIDTH);
            graphics.drawLine(x, y, x + xl, y + yl);
        }
        // Generate random captcha code
        for (int i = 0; i < resultCode.length(); i++) {
            // Draw captcha on image; avoid identical colors from close seeds by generating directly
            // graphics.setColor(new Color(20 + random.nextInt(130), 20 + random
            // .nextInt(130), 20 + random.nextInt(130)));
            // Set font color
            graphics.setColor(Color.BLACK);
            // Set font style
//			graphics.setFont(new Font("Arial Black", Font.ITALIC, 18));
            graphics.setFont(new Font("Times New Roman", Font.BOLD, 24));
            // Set characters, spacing, and top margin
            graphics.drawString(String.valueOf(resultCode.charAt(i)), (23 * i) + 8, 26);
        }
        // Make image take effect
        graphics.dispose();
        return image;
    }

    private static Color getRandColor(int fc, int bc) { // Get random color in given range
        final Random random = new Random();
        int length = 255;
        if (fc > length) {
            fc = length;
        }
        if (bc > length) {
            bc = length;
        }

        final int r = fc + random.nextInt(bc - fc);
        final int g = fc + random.nextInt(bc - fc);
        final int b = fc + random.nextInt(bc - fc);

        return new Color(r, g, b);
    }
}
