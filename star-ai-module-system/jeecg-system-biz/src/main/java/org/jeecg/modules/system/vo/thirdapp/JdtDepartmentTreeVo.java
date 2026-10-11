package org.jeecg.modules.system.vo.thirdapp;

import com.jeecg.dingtalk.api.department.vo.Department;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DingTalk tree department
 *
 * @author sunjianlei
 */
public class JdtDepartmentTreeVo extends Department {

    private List<JdtDepartmentTreeVo> children;

    public List<JdtDepartmentTreeVo> getChildren() {
        return children;
    }

    public JdtDepartmentTreeVo setChildren(List<JdtDepartmentTreeVo> children) {
        this.children = children;
        return this;
    }

    public JdtDepartmentTreeVo(Department department) {
        BeanUtils.copyProperties(department, this);
    }

    /**
     * Whether it has child items
     */
    public boolean hasChildren() {
        return children != null && children.size() > 0;
    }

    @Override
    public String toString() {
        return "JwDepartmentTree{" +
                "children=" + children +
                "} " + super.toString();
    }

    /**
     * Static helper to convert list to tree
     */
    public static List<JdtDepartmentTreeVo> listToTree(List<Department> allDepartment) {
        // Find all parents first
        List<JdtDepartmentTreeVo> treeList = getByParentId(1, allDepartment);
        Optional<Department> departmentOptional = allDepartment.stream().filter(item -> item.getParent_id() == null).findAny();
        Department department = new Department();
        //Check whether data was found
        if(departmentOptional.isPresent()){
            department = departmentOptional.get();
        }
        getChildrenRecursion(treeList, allDepartment);
        //update-begin---author:wangshuai---date:2024-04-10---for: [issues/6017] DingTalk dept sync missing top name; user sync lacked dept ---
        JdtDepartmentTreeVo treeVo = new JdtDepartmentTreeVo(department);
        treeVo.setChildren(treeList);
        List<JdtDepartmentTreeVo> list = new ArrayList<>();
        list.add(treeVo);
        return list;
        //update-end---author:wangshuai---date:2024-04-10---for: [issues/6017] DingTalk dept sync missing top name; user sync lacked dept ---
    }

    private static List<JdtDepartmentTreeVo> getByParentId(Integer parentId, List<Department> allDepartment) {
        List<JdtDepartmentTreeVo> list = new ArrayList<>();
        for (Department department : allDepartment) {
            if (parentId.equals(department.getParent_id())) {
                list.add(new JdtDepartmentTreeVo(department));
            }
        }
        return list;
    }

    private static void getChildrenRecursion(List<JdtDepartmentTreeVo> treeList, List<Department> allDepartment) {
        for (JdtDepartmentTreeVo departmentTree : treeList) {
            // Recursively find children
            List<JdtDepartmentTreeVo> children = getByParentId(departmentTree.getDept_id(), allDepartment);
            if (children.size() > 0) {
                departmentTree.setChildren(children);
                getChildrenRecursion(children, allDepartment);
            }
        }
    }

}
