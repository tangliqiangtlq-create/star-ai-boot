<#include "/common/utils.ftl">
<#-- update-begin---author:chenrui ---date:20240108  for:[issues/5755]Do not include logic-delete field in Vue code---------- -->
<#if po.isQuery=='Y' && po.fieldName !='delFlag'>
<#-- update- end---author:chenrui ---date:20240108  for:[issues/5755]Do not include logic-delete field in Vue code---------- -->
<#assign query_flag=true>
	<#if query_field_no==2>
          <template v-if="toggleSearchStatus">
	</#if>
	<#assign query_field_dictCode="">
	<#if po.dictTable?default("")?trim?length gt 1>
	    <#assign need_select_tag = true>
	    <#assign query_field_dictCode="${po.dictTable},${po.dictText},${po.dictField}">
	<#elseif po.dictField?default("")?trim?length gt 1>
	    <#assign need_select_tag = true>
	    <#assign query_field_dictCode="${po.dictField}">
	</#if>
	<#if po.queryMode=='single'>
          <#if query_field_no gt 1>  </#if><a-col :lg="6">
            <#if query_field_no gt 1>  </#if><a-form-item name="${autoStringSuffixForModel(po)}">
              <#if query_field_no gt 1>  </#if><template #label><span title="${po.filedComment}"><#if po.filedComment?default("")?trim?length gt 4>${po.filedComment?substring(0,4)}<#else>${po.filedComment}</#if></span></template>
            <#if po.classType=='sel_search'>
              <#if query_field_no gt 1>  </#if><j-search-select placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" dict="${po.dictTable},${po.dictText},${po.dictField}" allow-clear />
            <#elseif po.classType=='sel_user'>
              <#-- update-begin---author:chenrui ---date:20240102  for:[issue/#5711]Fix user selector becoming department-user selector after codegen---------- -->
              <#if query_field_no gt 1>  </#if><j-select-user placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" @change="(value)=>handleFormJoinChange('${po.fieldName}',value)" allow-clear />
              <#-- update- end---author:chenrui ---date:20240102  for:[issue/#5711]Fix user selector becoming department-user selector after codegen---------- -->
            <#elseif po.classType=='switch'>
              <#if query_field_no gt 1>  </#if><j-switch placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" <#if po.dictField!= 'is_open'>:options="${po.dictField}"</#if> query />
            <#elseif po.classType=='sel_depart'>
              <#if query_field_no gt 1>  </#if><j-select-dept placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" checkStrictly allow-clear />
            <#elseif po.classType=='list_multi'>
              <#if query_field_no gt 1>  </#if><j-select-multiple placeholder="Please select ${po.filedComment}" dictCode="${query_field_dictCode?default("")}" v-model:value="queryParam.${po.fieldName}" allow-clear />
            <#elseif po.classType=='cat_tree'>
              <#if query_field_no gt 1>  </#if><j-category-select placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" pcode="${po.dictField?default("")}" @change="(value) => handleFormChange('${po.fieldName}', value)" allow-clear />
            <#elseif po.classType=='date'>
              <#if query_field_no gt 1>  </#if><a-date-picker valueFormat="YYYY-MM-DD" placeholder="Please select ${po.filedComment}" <#if po.extendParams?exists && po.extendParams.picker?exists>picker="${po.extendParams.picker}"</#if> v-model:value="queryParam.${po.fieldName}" allow-clear />
            <#elseif po.classType=='datetime'>
              <#if query_field_no gt 1>  </#if><a-date-picker showTime valueFormat="YYYY-MM-DD HH:mm:ss" placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" allow-clear />
            <#elseif po.classType=='time'>
              <#if query_field_no gt 1>  </#if><time-picker valueFormat="HH:mm:ss" placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" allow-clear />
            <#elseif po.classType=='pca'>
              <#if query_field_no gt 1>  </#if><j-area-linkage v-model:value="queryParam.${po.fieldName}" placeholder="Please select ${po.filedComment}" saveCode="region" @change="(value)=>handleFormJoinChange('${po.fieldName}',value)" allow-clear />
            <#elseif po.classType=='sel_tree'>
              <#if query_field_no gt 1>  </#if><j-tree-select v-model:value="queryParam.${po.fieldName}" placeholder="Please select ${po.filedComment}" <#if po.dictText??><#if po.dictText?split(',')[2]?? && po.dictText?split(',')[0]??>dict="${po.dictTable},${po.dictText?split(',')[2]},${po.dictText?split(',')[0]}" <#elseif po.dictText?split(',')[1]??>pidField:"${po.dictText?split(',')[1]}", <#elseif po.dictText?split(',')[3]??>hasChildField:"${po.dictText?split(',')[3]}"</#if> </#if>pidValue="${po.dictField}" allow-clear />
            <#elseif po.classType=='popup'>
              <#assign sourceFields = po.dictField?default("")?trim?split(",")/>
              <#assign targetFields = po.dictText?default("")?trim?split(",")/>
              <#if query_field_no gt 1>  </#if><j-popup
                <#if query_field_no gt 1>  </#if>placeholder="Please select ${po.filedComment}" 
                <#if query_field_no gt 1>  </#if>v-model:value="queryParam.${po.fieldName}" 
                <#if query_field_no gt 1>  </#if>code="${po.dictTable}"
                <#if query_field_no gt 1>  </#if>:fieldConfig="[
                <#list sourceFields as fieldName>
                  <#if query_field_no gt 1>  </#if>{ source: '${fieldName}', target: '${dashedToCamel(targetFields[fieldName_index])}' },
                </#list>
                <#if query_field_no gt 1>  </#if>]"
                <#if query_field_no gt 1>  </#if>:multi="${po.extendParams.popupMulti?c}"
                <#if query_field_no gt 1>  </#if>:setFieldsValue="setFieldsValue" allow-clear />
            <#elseif po.classType=='list' || po.classType=='radio' || po.classType=='checkbox'>
            <#--  ---------------------------Dropdown/radio: table dict vs normal dict------------------------------- -->
            <#if po.dictTable?default("")?trim?length gt 1>
              <#if query_field_no gt 1>  </#if><j-dict-select-tag placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" dictCode="${po.dictTable},${po.dictText},${po.dictField}" allow-clear />
            <#elseif po.dictField?default("")?trim?length gt 1>
              <#if query_field_no gt 1>  </#if><j-dict-select-tag placeholder="Please select ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" dictCode="${po.dictField}" allow-clear />
            <#else>
              <#if query_field_no gt 1>  </#if><a-input placeholder="Please enter ${po.filedComment}" v-model:value="queryParam.${po.fieldName}" allow-clear ></a-input>
            </#if>
            <#elseif po.fieldDbType=='int' || po.fieldDbType=='double' || po.fieldDbType=='BigDecimal'>
              <#if query_field_no gt 1>  </#if><a-input-number placeholder="Please enter ${po.filedComment}" v-model:value="queryParam.${po.fieldName}"></a-input-number>           
      <#else>
              <#if query_field_no gt 1>  </#if><a-input placeholder="Please enter ${po.filedComment}" v-model:value="queryParam.${autoStringSuffixForModel(po)}" allow-clear ></a-input>
      </#if>
            <#if query_field_no gt 1>  </#if></a-form-item>
          <#if query_field_no gt 1>  </#if></a-col>
	<#else>
          <#if query_field_no gt 1>  </#if><a-col :lg="6">
            <#if query_field_no gt 1>  </#if><a-form-item>
               <#if query_field_no gt 1>  </#if><template #label><span title="${po.filedComment}"><#if po.filedComment?default("")?trim?length gt 4>${po.filedComment?substring(0,4)}<#else>${po.filedComment}</#if></span></template>
      <#if po.classType=='date'>
               <#if query_field_no gt 1>  </#if><div style="display: flex">
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_begin" style="margin-bottom: 0;">
                   <#if query_field_no gt 1>  </#if><a-date-picker value-format="YYYY-MM-DD" placeholder="Please select start date" <#if po.extendParams?exists && po.extendParams.picker?exists>picker="${po.extendParams.picker}"</#if> v-model:value="queryParam.${po.fieldName}_begin" class="query-group-cust" allow-clear />
                 <#if query_field_no gt 1>  </#if></a-form-item>  
                 <#if query_field_no gt 1>  </#if><span class="query-group-split-cust">~</span>
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_end" style="margin-bottom: 0;">
                   <#if query_field_no gt 1>  </#if><a-date-picker value-format="YYYY-MM-DD" placeholder="Please select end date" <#if po.extendParams?exists && po.extendParams.picker?exists>picker="${po.extendParams.picker}"</#if> v-model:value="queryParam.${po.fieldName}_end" class="query-group-cust" allow-clear />
                 <#if query_field_no gt 1>  </#if></a-form-item>
               <#if query_field_no gt 1>  </#if></div>
      <#elseif po.classType=='time'>
               <#if query_field_no gt 1>  </#if><div style="display: flex">
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_begin" style="margin-bottom: 0;">
                   <#if query_field_no gt 1>  </#if><time-picker value-format="HH:mm:ss" placeholder="Please select start time" v-model:value="queryParam.${po.fieldName}_begin" class="query-group-cust" allow-clear />
                 <#if query_field_no gt 1>  </#if></a-form-item>
                 <#if query_field_no gt 1>  </#if><span class="query-group-split-cust">~</span>
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_end" style="margin-bottom: 0;">
                   <#if query_field_no gt 1>  </#if><time-picker value-format="HH:mm:ss" placeholder="Please select end date" v-model:value="queryParam.${po.fieldName}_end" class="query-group-cust" allow-clear />
                 <#if query_field_no gt 1>  </#if></a-form-item>
               <#if query_field_no gt 1>  </#if></div>
      <#elseif po.classType=='datetime'>
               <#if query_field_no gt 1>  </#if><div style="display: flex">
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_begin" style="margin-bottom: 0;">
                   <#if query_field_no gt 1>  </#if><a-date-picker showTime value-format="YYYY-MM-DD HH:mm:ss" placeholder="Please select start time" v-model:value="queryParam.${po.fieldName}_begin" class="query-group-cust" allow-clear />
                 <#if query_field_no gt 1>  </#if></a-form-item>
                 <#if query_field_no gt 1>  </#if><span class="query-group-split-cust">~</span>
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_end" style="margin-bottom: 0;">
                   <#if query_field_no gt 1>  </#if><a-date-picker showTime value-format="YYYY-MM-DD HH:mm:ss" placeholder="Please select end time" v-model:value="queryParam.${po.fieldName}_end" class="query-group-cust" allow-clear />
                 <#if query_field_no gt 1>  </#if></a-form-item>
               <#if query_field_no gt 1>  </#if></div>
      <#else>
               <#if query_field_no gt 1>  </#if><div style="display: flex">
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_begin" style="margin-bottom: 0;">
                   <#if query_field_no gt 1>  </#if><a-input placeholder="Please enter min value" v-model:value="queryParam.${po.fieldName}_begin" class="query-group-cust" allow-clear ></a-input>
                 <#if query_field_no gt 1>  </#if></a-form-item>       
                 <#if query_field_no gt 1>  </#if><span class="query-group-left query-group-split-cust">~</span>
                 <#if query_field_no gt 1>  </#if><a-form-item name="${po.fieldName}_end" style="margin-bottom: 0;"> 
                  <#if query_field_no gt 1>  </#if><a-input placeholder="Please enter max value" v-model:value="queryParam.${po.fieldName}_end" class="query-group-cust" allow-clear ></a-input>
                 <#if query_field_no gt 1>  </#if></a-form-item>
               <#if query_field_no gt 1>  </#if></div>
      </#if>
            <#if query_field_no gt 1>  </#if></a-form-item>
          <#if query_field_no gt 1>  </#if></a-col>
  </#if>
      <#assign query_field_no=query_field_no+1>
  </#if>