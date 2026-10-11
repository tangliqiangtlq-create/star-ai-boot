package org.jeecg.common.constant;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson.JSONObject;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.List;

/**
 * @Description: Provinces and municipalities
 * @author: jeecg-boot
 */
@Component("pca")
public class ProvinceCityArea {
  private static final Logger log = LoggerFactory.getLogger(ProvinceCityArea.class);

    List<Area> areaList;

    public String getText(String code){
        this.initAreaList();
        if(this.areaList!=null || this.areaList.size()>0){
            List<String> ls = new ArrayList<String>();
            getAreaByCode(code,ls);
            return String.join("/",ls);
        }
        return "";
    }

    public String getCode(String text){
        this.initAreaList();
        if(areaList!=null && areaList.size()>0){
            for(int i=areaList.size()-1;i>=0;i--){
                //update-begin-author:taoyan date:2022-5-24 for:VUEN-1088 online import: province/city/district data scrambled after import e.g. Beijing/Municipal District/Xicheng -> Shanxi/Jincheng/Chengqu
                String areaText = areaList.get(i).getText();
                String cityText = areaList.get(i).getAheadText();
                if(text.indexOf(areaText)>=0 && (cityText!=null && text.indexOf(cityText)>=0)){
                    return areaList.get(i).getId();
                }
                //update-end-author:taoyan date:2022-5-24 for:VUEN-1088 online import: province/city/district data scrambled after import e.g. Beijing/Municipal District/Xicheng -> Shanxi/Jincheng/Chengqu
            }
        }
        return null;
    }

    // update-begin-author:sunjianlei date:20220121 for:【JTC-704】Data import error Province and cityComponent，In the file, it is Beijing City. After importing, it is imported into Shanxi Province.
    /**
     * Get province and citycode，Exact match
     * @param texts Text array, province, city, district
     * @return Return to province and citycode
     */
    public String[] getCode(String[] texts) {
        if (texts == null || texts.length == 0) {
            return null;
        }
        this.initAreaList();
        if (areaList == null || areaList.size() == 0) {
            return null;
        }
        String[] codes = new String[texts.length];
        String code = null;
        for (int i = 0; i < texts.length; i++) {
            String text = texts[i];
            Area area;
            if (code == null) {
                area = getAreaByText(text);
            } else {
                area = getAreaByPidAndText(code, text);
            }
            if (area != null) {
                code = area.id;
                codes[i] = code;
            } else {
                return null;
            }
        }
        return codes;
    }

    /**
     * BytextGetarea
     * @param text
     * @return
     */
    public Area getAreaByText(String text) {
        for (Area area : areaList) {
            if (text.equals(area.getText())) {
                return area;
            }
        }
        return null;
    }

    /**
     * passpidget area object
     * @param pCode parentcoding
     * @param text
     * @return
     */
    public Area getAreaByPidAndText(String pCode, String text) {
        this.initAreaList();
        if (this.areaList != null && this.areaList.size() > 0) {
            for (Area area : this.areaList) {
                if (area.getPid().equals(pCode) && area.getText().equals(text)) {
                    return area;
                }
            }
        }
        return null;
    }
    // update-end-author:sunjianlei date:20220121 for:【JTC-704】Data import error Province and cityComponent，In the file, it is Beijing City. After importing, it is imported into Shanxi Province.

    public void getAreaByCode(String code,List<String> ls){
        for(Area area: areaList){
            if(area.getId().equals(code)){
                String pid = area.getPid();
                ls.add(0,area.getText());
                getAreaByCode(pid,ls);
            }
        }
    }

    private void initAreaList(){
        //log.debug("=====================");
        if(this.areaList==null || this.areaList.size()==0){
            this.areaList = new ArrayList<Area>();
            try {
                String jsonData = oConvertUtils.readStatic("classpath:static/pca.json");
                JSONObject baseJson = JSONObject.parseObject(jsonData);
                //First level province
                JSONObject provinceJson = baseJson.getJSONObject("86");
                for(String provinceKey: provinceJson.keySet()){
                    //log.debug("===="+provinceKey);
                    Area province = new Area(provinceKey,provinceJson.getString(provinceKey),"86");
                    this.areaList.add(province);
                    //Second level city
                    JSONObject cityJson = baseJson.getJSONObject(provinceKey);
                    for(String cityKey:cityJson.keySet()){
                        //log.debug("-----"+cityKey);
                        Area city = new Area(cityKey,cityJson.getString(cityKey),provinceKey);
                        this.areaList.add(city);
                        //Third floor area
                        JSONObject areaJson =  baseJson.getJSONObject(cityKey);
                        if(areaJson!=null){
                            for(String areaKey:areaJson.keySet()){
                                //log.debug("········"+areaKey);
                                Area area = new Area(areaKey,areaJson.getString(areaKey),cityKey);
                                //update-begin-author:taoyan date:2022-5-24 for:VUEN-1088 online import: province/city/district data scrambled after import e.g. Beijing/Municipal District/Xicheng -> Shanxi/Jincheng/Chengqu
                                area.setAheadText(cityJson.getString(cityKey));
                                //update-end-author:taoyan date:2022-5-24 for:VUEN-1088 online import: province/city/district data scrambled after import e.g. Beijing/Municipal District/Xicheng -> Shanxi/Jincheng/Chengqu
                                this.areaList.add(area);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("operation failed", e);
            }
        }

    }


    private String jsonRead(File file){
        Scanner scanner = null;
        StringBuilder buffer = new StringBuilder();
        try {
            scanner = new Scanner(file, "utf-8");
            while (scanner.hasNextLine()) {
                buffer.append(scanner.nextLine());
            }
        } catch (Exception e) {

        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return buffer.toString();
    }

    class Area{
        String id;
        String text;
        String pid;
        // Used to store superior text data. The superior text of the district is the data of the city.
        String aheadText;

        public Area(String id,String text,String pid){
            this.id = id;
            this.text = text;
            this.pid = pid;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getPid() {
            return pid;
        }

        public String getAheadText() {
            return aheadText;
        }
        public void setAheadText(String aheadText) {
            this.aheadText = aheadText;
        }
    }
}
