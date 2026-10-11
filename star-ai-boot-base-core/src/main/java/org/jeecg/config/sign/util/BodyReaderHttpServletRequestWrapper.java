package org.jeecg.config.sign.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.Charset;

/**
 * Save stream from inside the filter
 * 
 * @author jeecg
 * @date 20210621
 */
public class BodyReaderHttpServletRequestWrapper extends HttpServletRequestWrapper {
  private static final Logger log = LoggerFactory.getLogger(BodyReaderHttpServletRequestWrapper.class);


    private final byte[] body;

    public BodyReaderHttpServletRequestWrapper(HttpServletRequest request) {

        super(request);
        String sessionStream = getBodyString(request);
        body = sessionStream.getBytes(Charset.forName("UTF-8"));
    }

    /**
     * Get request Body
     *
     * @param request
     * @return
     */
    public String getBodyString(final ServletRequest request) {

        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = cloneInputStream(request.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            log.error("operation failed", e);
        }
        return sb.toString();
    }

    /**
     * Description: copy input stream</br>
     *
     * @param inputStream
     * @return</br>
     */
    public InputStream cloneInputStream(ServletInputStream inputStream) {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buffer)) > -1) {
                byteArrayOutputStream.write(buffer, 0, len);
            }
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            log.error("operation failed", e);
        }
        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }

    @Override
    public BufferedReader getReader() {

        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() {

        final ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {

            @Override
            public int read() {

                return bais.read();
            }

            @Override
            public boolean isFinished() {

                return false;
            }

            @Override
            public boolean isReady() {

                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {

            }
        };
    }
}