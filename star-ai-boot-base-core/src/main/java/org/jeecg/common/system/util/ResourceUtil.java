package org.jeecg.common.system.util;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.system.annotation.EnumDict;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource loading tool class
 * @Author taoYan
 * @Date 2022/7/8 10:40
 **/
@Slf4j
public class ResourceUtil {


    /**
     * Enum dictionary data
     */
    private final static Map<String, List<DictModel>> enumDictData = new HashMap<>(5);

    /**
     * All Java classes
     */
    private final static String CLASS_PATTERN="/**/*.class";

    /**
     * All enum Java classes
     */

    private final static String CLASS_ENUM_PATTERN="/**/*Enum.class";

    /**
     * Package path org.jeecg
     */
    private final static String BASE_PACKAGE = "org.jeecg";

    /**
     * Method name to get dictionary data from enum class
     */
    private final static String METHOD_NAME = "getDictList";

    /**
     * Get the dictionary data corresponding to the enumeration class SysDictServiceImpl#queryAllDictItems()
     * @return
     */
    public static Map<String, List<DictModel>> getEnumDictData(){
        if(enumDictData.keySet().size()>0){
            return enumDictData;
        }
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + ClassUtils.convertClassNameToResourcePath(BASE_PACKAGE) + CLASS_ENUM_PATTERN;
        try {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resourcePatternResolver);
            for (Resource resource : resources) {
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                String classname = reader.getClassMetadata().getClassName();
                Class<?> clazz = Class.forName(classname);
                EnumDict enumDict = clazz.getAnnotation(EnumDict.class);
                if (enumDict != null) {
                    EnumDict annotation = clazz.getAnnotation(EnumDict.class);
                    String key = annotation.value();
                    if(oConvertUtils.isNotEmpty(key)){
                        List<DictModel> list = (List<DictModel>) clazz.getDeclaredMethod(METHOD_NAME).invoke(null);
                        enumDictData.put(key, list);
                    }
                }
            }
        }catch (Exception e){
            log.error("Exception getting enum dictionary data");
            // log.error("operation failed", e);
        }
        return enumDictData;
    }

    /**
     * For backend dict translation SysDictServiceImpl#queryManyDictByKeys(java.util.List, java.util.List)
     * @param dictCodeList
     * @param keys
     * @return
     */
    public static Map<String, List<DictModel>> queryManyDictByKeys(List<String> dictCodeList, List<String> keys){
        if(enumDictData.keySet().size()==0){
            getEnumDictData();
        }
        Map<String, List<DictModel>> map = new HashMap<>();
        for (String code : enumDictData.keySet()) {
            if(dictCodeList.indexOf(code)>=0){
                List<DictModel> dictItemList = enumDictData.get(code);
                for(DictModel dm: dictItemList){
                    String value = dm.getValue();
                    if(keys.indexOf(value)>=0){
                        List<DictModel> list = new ArrayList<>();
                        list.add(new DictModel(value, dm.getText()));
                        map.put(code,list);
                        break;
                    }
                }
            }
        }
        return map;
    }

    /**
     * Get implementation class
     * 
     * @param classPath 
     */
    public static Object getImplementationClass(String classPath){
        try {
            Class<?> aClass = Class.forName(classPath);
            return SpringContextUtils.getBean(aClass);
        } catch (ClassNotFoundException e) {
            log.error("Class not found",e);
            return null;
        } catch (NoSuchBeanDefinitionException e){
            log.error(classPath + "Not implemented",e);
            return null;
        }
    }
}
