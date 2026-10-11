package org.jeecg.modules.message.enums;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.enums.MessageTypeEnum;
import org.jeecg.common.system.annotation.EnumDict;
import org.jeecg.common.system.vo.DictModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Message data query [vue3]
 * New system notification query conditions
 * @Author taoYan
 * @Date 2022/8/19 20:41
 **/
@Slf4j
@EnumDict("rangeDate")
public enum RangeDateEnum {

    JT("jt", "Today"),
    ZT("zt", "Yesterday"),
    QT("qt", "Day before yesterday"),
    BZ("bz","This week"),
    SZ("sz", "Last week"),
    BY("by", "This month"),
    SY("sy", "Last month"),
    SEVENDAYS("7day", "7 days"),
    ZDY("zdy", "Custom date");

    String key;

    String title;

    RangeDateEnum(String key, String title){
        this.key = key;
        this.title = title;
    }

    /**
     * GetDictionaryData
     * @return
     */
    public static List<DictModel> getDictList(){
        List<DictModel> list = new ArrayList<>();
        DictModel dictModel = null;
        for(RangeDateEnum e: RangeDateEnum.values()){
            dictModel = new DictModel();
            dictModel.setValue(e.key);
            dictModel.setText(e.title);
            list.add(dictModel);
        }
        return list;
    }

    /**
     * Get range time value by key
     * @param key
     * @return
     */
    public static Date[] getRangeArray(String key){
        Calendar calendar1 = Calendar.getInstance();
        Calendar calendar2 = Calendar.getInstance();
        Date[] array = new Date[2];
        boolean flag = false;
        if(JT.key.equals(key)){
            // Today
        } else if(ZT.key.equals(key)){
            // Yesterday
            calendar1.add(Calendar.DAY_OF_YEAR, -1);
            calendar2.add(Calendar.DAY_OF_YEAR, -1);
        } else if(QT.key.equals(key)){
            // Day before yesterday
            calendar1.add(Calendar.DAY_OF_YEAR, -2);
            calendar2.add(Calendar.DAY_OF_YEAR, -2);
        } else if(BZ.key.equals(key)){
            // This week
            calendar1.set(Calendar.DAY_OF_WEEK, 2);

            calendar2.add(Calendar.WEEK_OF_MONTH,1);
            calendar2.add(Calendar.DAY_OF_WEEK,-1);
        } else if(SZ.key.equals(key)){
            // This Monday minus one week
            calendar1.set(Calendar.DAY_OF_WEEK, 2);
            calendar1.add(Calendar.WEEK_OF_MONTH, -1);

            // This Monday minus one day
            calendar2.set(Calendar.DAY_OF_WEEK, 2);
            calendar2.add(Calendar.DAY_OF_WEEK,-1);
        } else if(BY.key.equals(key)){
            // This month
            calendar1.set(Calendar.DAY_OF_MONTH, 1);

            calendar2.set(Calendar.DAY_OF_MONTH, 1);
            calendar2.add(Calendar.MONTH, 1);
            calendar2.add(Calendar.DAY_OF_MONTH, -1);
        } else if(SY.key.equals(key)){
            // First day of this month minus one month
            calendar1.set(Calendar.DAY_OF_MONTH, 1);
            calendar1.add(Calendar.MONTH, -1);

            // First day of this month minus one day
            calendar2.set(Calendar.DAY_OF_MONTH, 1);
            calendar2.add(Calendar.DAY_OF_MONTH, -1);
        } else if (SEVENDAYS.key.equals(key)){
            // First day of seven-day period
            calendar1.setTime(new Date());
            calendar1.add(Calendar.DATE, -7);
        }else{
            flag = true;
        }
        if(flag){
            return null;
        }
        // Start 00:00:00, end 23:59:59
        calendar1.set(Calendar.HOUR, 0);
        calendar1.set(Calendar.MINUTE, 0);
        calendar1.set(Calendar.SECOND, 0);
        calendar1.set(Calendar.MILLISECOND, 0);
        calendar2.set(Calendar.HOUR, 23);
        calendar2.set(Calendar.MINUTE, 59);
        calendar2.set(Calendar.SECOND, 59);
        calendar2.set(Calendar.MILLISECOND, 999);
        array[0] = calendar1.getTime();
        array[1] = calendar2.getTime();
        return array;
    }
    
    public String getKey(){
        return this.key;
    }

}
