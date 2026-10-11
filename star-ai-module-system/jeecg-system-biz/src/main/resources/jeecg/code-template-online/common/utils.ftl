<#---->
<#-- Freemarker utility functions -->
<#---->
<#-- Convert camelCase to another character -->
<#-- @param str       Text to convert -->
<#-- @param character Target separator character -->
<#-- @param case      Case conversion (normal: no change, lower: lowercase, upper: uppercase) -->
<#function camelToChar(str, character, case='normal')>
  <#assign text=str?replace("([a-z])([A-Z]+)","$1${character}$2","r")/>
  <#if case=="upper">
    <#return text?upper_case>
  <#elseif case=="lower">
    <#return text?lower_case>
  <#else>
    <#return text>
  </#if>
</#function>
<#-- Underscore to camelCase -->
<#function dashedToCamel(str)>
    <#assign text=""/>
    <#assign strlist = str?split("_")/>
    <#list strlist as v>
        <#assign text=text+v?cap_first/>
    </#list>
    <#return text?uncap_first>
</#function>
<#-- camelCase to underscore -->
<#function camelToDashed(str, case='lower')>
  <#return camelToChar(str, "_", case)>
</#function>
<#---->
<#-- camelCase to hyphen -->
<#function camelToHorizontal(str, case='normal')>
  <#return camelToChar(str, "-", case)>
</#function>
<#---->
<#-- Get v-model attribute -->
<#function getVModel po,suffix="">
  <#return "v-model=\"queryParam.${po.fieldName}${suffix}\"">
</#function>
<#-- Get placeholder attribute -->
<#function getPlaceholder po,prefix,fillComment=true>
  <#if fillComment>
    <#return "placeholder=\"${prefix}${po.filedComment}\"">
  <#else>
    <#return "placeholder=\"${prefix}\"">
  </#if>
</#function>
<#-- ** Check whether a field has validation configured ** -->
<#function poHasCheck po>
  <#if (po.fieldValidType!'')?trim?length gt 0 || po.nullable == 'N'>
    <#if po.fieldName != 'id'>
      <#if po.nullable == 'N'
      || po.fieldValidType == '*'
      || po.fieldValidType == 'only'
      || po.fieldValidType == 'n6-16'
      || po.fieldValidType == '*6-16'
      || po.fieldValidType == 's6-18'
      || po.fieldValidType == 'url'
      || po.fieldValidType == 'e'
      || po.fieldValidType == 'm'
      || po.fieldValidType == 'p'
      || po.fieldValidType == 's'
      || po.fieldValidType == 'n'
      || po.fieldValidType == 'z'
      || po.fieldValidType == 'money'
      || po.fieldValidType != ''
      >
        <#return true>
      </#if>
    </#if>
  </#if>
  <#return false>
</#function>
<#-- ** Append validatorRules when validation is configured ** -->
<#function autoWriteRules po>
  <#if poHasCheck(po)>
    <#return ", validatorRules.${po.fieldName}">
  <#else>
    <#return "">
  </#if>
</#function>

<#-- ** Use String suffix when field type is Blob ** -->
<#function autoStringSuffix po>
  <#if  po.fieldDbType=='Blob'>
    <#return "'${po.fieldName}String'">
  <#else>
    <#return "'${po.fieldName}'">
  </#if>
</#function>

<#-- ** Use String suffix for model when field type is Blob ** -->
<#function autoStringSuffixForModel po>
    <#if  po.fieldDbType=='Blob'>
        <#return "${po.fieldName}String">
    <#else>
        <#return "${po.fieldName}">
    </#if>
</#function>

<#-- ** Generate advanced query fields ** -->
<#function superQueryFieldList po>
    <#assign superQuery_dictTable="">
    <#assign superQuery_dictText="">
    <#if po.dictTable?default("")?trim?length gt 1>
        <#assign superQuery_dictTable="${po.dictTable}">
    </#if>
    <#if po.dictText?default("")?trim?length gt 1>
        <#assign superQuery_dictText="${po.dictText}">
    </#if>
  <#if po.classType=="popup">
      <#return "{type:'${po.classType}',value:'${po.fieldName}',text:'${po.filedComment}', popup:{code:'${po.dictTable}',field:'${po.dictField?split(',')[0]}',orgFields:'${po.dictField?split(',')[0]}',destFields:'${po.dictText?split(',')[0]}'}}">
  <#elseif po.classType=="sel_user" || po.classType=="sel_depart" || po.classType=="datetime" || po.classType=="date" || po.classType=="pca" || po.classType=="switch">
      <#return "{type:'${po.classType}',value:'${po.fieldName}',text:'${po.filedComment}'}">
  <#else>
      <#if po.classType=="sel_search" || po.classType=="list_multi">
          <#return "{type:'${po.classType}',value:'${po.fieldName}',text:'${po.filedComment}',dictTable:\"${superQuery_dictTable}\", dictText:'${superQuery_dictText}', dictCode:'${po.dictField}'}">
      <#elseif po.dictTable?? && po.dictTable!="" && po.classType!="sel_tree" && po.classType!="cat_tree" && po.classType!="link_down">
          <#return "{type:'${po.fieldDbType}',value:'${po.fieldName}',text:'${po.filedComment}',dictCode:\"${po.dictTable},${po.dictText},${po.dictField}\"}">
      <#elseif po.dictField?? && po.classType!="sel_tree" && po.classType!="cat_tree" && po.classType!="link_down">
          <#return "{type:'${po.fieldDbType}',value:'${po.fieldName}',text:'${po.filedComment}',dictCode:'${po.dictField}'}">
      <#elseif po.fieldDbType=="Text">
          <#return "{type:'string',value:'${po.fieldName}',text:'${po.filedComment}'}">
      <#elseif po.fieldDbType=="Blob">
          <#return "{type:'byte',value:'${po.fieldName}',text:'${po.filedComment}'}">
      <#elseif po.fieldDbType=="BigDecimal" || po.fieldDbType=="double">
          <#return "{type:'number',value:'${po.fieldName}',text:'${po.filedComment}'}">
      <#else>
          <#return "{type:'${po.fieldDbType}',value:'${po.fieldName}',text:'${po.filedComment}'}">
      </#if>
  </#if>
</#function>
<#-- update-begin---author:chenrui ---date:20231228  for:[QQYUN-7527]include advanced query by default in Vue3 code generation---------- -->
<#-- ** Generate advanced query fields (Vue3) ** -->
<#function superQueryFieldListForVue3(po,order)>
<#-- Field view/DB type -->
    <#assign baseAttrs="view: '${po.classType}', type: 'string',">
    <#if po.fieldDbType=='int' || po.fieldDbType=='double' || po.fieldDbType=='BigDecimal'>
        <#assign baseAttrs="view: 'number', type: 'number',">
    </#if>

<#-- Extended attributes for special control types -->
    <#assign extAttrs="">
    <#assign dictCode="">
    <#if po.dictTable?default('')?trim?length gt 1 && po.dictText?default('')?trim?length gt 1 && po.dictField?default("")?trim?length gt 1>
        <#-- update-begin---author:chenrui ---date:20231228  for:fix JS compile error caused by single quotes in conditional dict---------- -->
        <#assign dictCode="dictTable: \"${po.dictTable}\", dictCode: '${po.dictField}', dictText: '${po.dictText}'">
        <#-- update-begin---author:chenrui ---date:20231228  for:fix JS compile error caused by single quotes in conditional dict---------- -->
    <#elseif po.dictField?default("")?trim?length gt 1>
        <#assign dictCode="dictCode: '${po.dictField}'">
    </#if>

    <#if po.classType=='list' || po.classType=='list_multi' || po.classType=='sel_search' || po.classType=='checkbox' || po.classType=='radio'>
        <#assign extAttrs="${dictCode},">
    <#elseif po.classType=='cat_tree'>
    <#-- Category dict tree -->
        <#-- update-begin---author:chenrui ---date:20240109  for:[issue/5787]add null checks to prevent NPE during code generation---------- -->
        <#assign extAttrs="pcode: '${po.dictField?default('')}',">
        <#-- update-end---author:chenrui ---date:20240109  for:[issue/5787]add null checks to prevent NPE during code generation---------- -->
    <#elseif po.classType=='sel_tree'>
    <#-- Custom tree -->
        <#if po.dictText??>
        <#-- dictText example: id,pid,name,has_child -->
            <#if po.dictText?split(',')[2]?? && po.dictText?split(',')[0]??>
                <#assign extAttrs="dict: '${po.dictTable},${po.dictText?split(',')[2]},${po.dictText?split(',')[0]}'">
            <#elseif po.dictText?split(',')[1]??>
                <#assign extAttrs="pidField: '${po.dictText?split(',')[1]}'">
            <#elseif po.dictText?split(',')[3]??>
                <#assign extAttrs="hasChildField: '${po.dictText?split(',')[3]}'">
            </#if>
        </#if>
        <#-- update-begin---author:chenrui ---date:20240109  for:[issue/5787]add null checks to prevent NPE during code generation---------- -->
        <#assign extAttrs="${extAttrs}, pidValue: '${po.dictField?default('')}',">
        <#-- update-end---author:chenrui ---date:20240109  for:[issue/5787]add null checks to prevent NPE during code generation---------- -->
    <#elseif po.classType=='popup'>
    <#-- popup -->
        <#-- update-begin---author:chenrui ---date:20240109  for:[issue/5787]add null checks to prevent NPE during code generation---------- -->
        <#if po.dictText?default("")?trim?length gt 1 && po.dictText?index_of(',') gt 0 && po.dictField?default("")?trim?length gt 1>
        <#-- update-begin---author:chenrui ---date:20240109  for:[issue/5787]add null checks to prevent NPE during code generation---------- -->
        <#-- When multiple backfill fields exist, find the popup field source -->
            <#assign orgFieldIx=po.dictText?split(',')?seq_index_of(po.fieldDbName)>
            <#assign orgField=po.dictField?split(',')[orgFieldIx]>
        <#else>
            <#assign orgField=po.dictField?default("")>
        </#if>
        <#assign extAttrs="code: '${po.dictTable?default('')}', orgFields: '${orgField}', destFields: '${po.fieldName}', popupMulti: false,">
    </#if>

    <#return "${po.fieldName}: {title: '${po.filedComment}',order: ${order},${baseAttrs}${extAttrs}}" >
</#function>
<#-- update-end---author:chenrui ---date:20231228  for:[QQYUN-7527]include advanced query by default in Vue3 code generation---------- -->


<#-- Get form modal width for Vue3 -->
<#function getModalWidth fieldRowNum>
    <#assign modal_width = 800>
    <#if fieldRowNum==2>
        <#assign modal_width = 896>
    <#elseif fieldRowNum==3>
        <#assign modal_width = 1024>
    <#elseif fieldRowNum==4>
        <#assign modal_width = 1280>
    </#if>
    <#return modal_width>
</#function>

<#-- Get form colspan for Vue3 -->
<#function getFormSpan fieldRowNum>
    <#assign form_span = 24>
    <#if fieldRowNum==2>
        <#assign form_span = 12>
    <#elseif fieldRowNum==3>
        <#assign form_span = 8>
    <#elseif fieldRowNum==4>
        <#assign form_span = 6>
    </#if>
    <#return form_span>
</#function>

<#-- Vue3 native: check field name is not pidField -->
<#function isNotPidField(tableVo, fieldDbName) >
  <#assign flag = true>
  <#if tableVo??>
    <#if tableVo.extendParams??>
      <#if tableVo.extendParams.pidField?default("")?trim == fieldDbName>
        <#assign flag = false>
      </#if>
    </#if>
  </#if>
  <#return flag>
</#function>
