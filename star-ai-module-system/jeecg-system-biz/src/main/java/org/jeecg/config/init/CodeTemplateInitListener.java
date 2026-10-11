package org.jeecg.config.init;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Auto-initialize code generator templates
 * <p>
 * Fixes manual code generator template setup required for JAR deployment
 * @author zhang
 */
@Slf4j
@Component
public class CodeTemplateInitListener implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info(" Init Code Generate Template [ Detect JAR startup environment, copy template to config directory ] ");
            this.initJarConfigCodeGeneratorTemplate();
        } catch (Exception e) {
            log.error("operation failed", e);
        }
    }

    /**
     * ::Jar startup mode::
     * Initialize code generator template files
     */
    private void initJarConfigCodeGeneratorTemplate() throws Exception {
        //1. Get config path next to jar
        String configPath = System.getProperty("user.dir") + File.separator + "config" + File.separator;
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:jeecg/code-template-online/**/*");
        for (Resource re : resources) {
            URL url = re.getURL();
            String filepath = url.getPath();
            //log.debug("native url= " + filepath);
            filepath = java.net.URLDecoder.decode(filepath, "utf-8");
            //log.debug("decode url= " + filepath);

            //2. Under config, create jeecg/code-template-online/* templates
            String createFilePath = configPath + filepath.substring(filepath.indexOf("jeecg/code-template-online"));

            // Do not generate template in non-jar mode
            // Do not generate directory; only generate specific template file
            if (!filepath.contains(".jar!/BOOT-INF/lib/") || !createFilePath.contains(".")) {
                continue;
            }
            if (!FileUtil.exist(createFilePath)) {
                log.info("create file codeTemplate = " + createFilePath);
                FileUtil.writeString(IOUtils.toString(url, StandardCharsets.UTF_8), createFilePath, "UTF-8");
            }
        }
    }
}
