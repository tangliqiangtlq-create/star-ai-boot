import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.jeecg.JeecgSystemCloudApplication;
import org.jeecg.modules.system.entity.SysDictItem;
import org.jeecg.modules.system.service.ISysDictItemService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = JeecgSystemCloudApplication.class)
public class Test1 {
  private static final Logger log = LoggerFactory.getLogger(Test1.class);


  @Autowired private ISysDictItemService sysDictItemService;

  @Test
  public void test() {

    List<SysDictItem> sysDictItems =
        sysDictItemService.list(
            new QueryWrapper<SysDictItem>().eq("dict_id", "1800018946106281986"));

    for (SysDictItem sysDictItem : sysDictItems) {

      String text = sysDictItem.getItemText();

      text = text.replaceAll("cn", "zh_CN");

      sysDictItem.setItemText(text);
    }

    sysDictItemService.saveOrUpdateBatch(sysDictItems);

    log.debug("");
  }
}
