package org.jeecg.modules.system.vo.thirdapp;

import com.jeecg.qywx.api.department.vo.Department;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * WeCom tree department
 *
 * @author sunjianlei
 */
public class JwDepartmentTreeVo extends Department {

    private List<JwDepartmentTreeVo> children;

    public List<JwDepartmentTreeVo> getChildren() {
        return children;
    }

    public JwDepartmentTreeVo setChildren(List<JwDepartmentTreeVo> children) {
        this.children = children;
        return this;
    }

    public JwDepartmentTreeVo(Department department) {
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
    public static List<JwDepartmentTreeVo> listToTree(List<Department> allDepartment) {
        // Find all parents first
        List<JwDepartmentTreeVo> treeList = getByParentId("1", allDepartment);
        Optional<Department> departmentOptional = allDepartment.stream().filter(item -> "0".equals(item.getParentid())).findAny();
        Department department = new Department();
        //Check whether data was found
        if(departmentOptional.isPresent()){
            department = departmentOptional.get();
        }
        getChildrenRecursion(treeList, allDepartment);
        //update-begin---author:wangshuai---date:2024-04-10---for: [issues/6017] WeCom dept sync missing top name; user sync lacked dept ---
        JwDepartmentTreeVo treeVo = new JwDepartmentTreeVo(department);
        treeVo.setChildren(treeList);
        List<JwDepartmentTreeVo> list = new ArrayList<>();
        list.add(treeVo);
        return list;
        //update-begin---author:wangshuai---date:2024-04-10---for: [issues/6017] WeCom dept missing top name; user sync lacked dept ---
    }

    private static List<JwDepartmentTreeVo> getByParentId(String parentId, List<Department> allDepartment) {
        List<JwDepartmentTreeVo> list = new ArrayList<>();
        for (Department department : allDepartment) {
            if (parentId.equals(department.getParentid())) {
                list.add(new JwDepartmentTreeVo(department));
            }
        }
        return list;
    }

    private static void getChildrenRecursion(List<JwDepartmentTreeVo> treeList, List<Department> allDepartment) {
        for (JwDepartmentTreeVo departmentTree : treeList) {
            // Recursively find children
            List<JwDepartmentTreeVo> children = getByParentId(departmentTree.getId(), allDepartment);
            if (children.size() > 0) {
                departmentTree.setChildren(children);
                getChildrenRecursion(children, allDepartment);
            }
        }
    }

}
