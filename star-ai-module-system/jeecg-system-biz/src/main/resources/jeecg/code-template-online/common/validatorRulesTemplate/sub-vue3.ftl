<#include "../utils.ftl">
    <#if col.isShow == 'Y' && poHasCheck(col)>
        validateRules: [
        <#if col.fieldName != 'id'>
            <#assign subFieldValidType = col.fieldValidType!''>
        <#-- Required validation -->
            <#if col.nullable == 'N' || subFieldValidType == '*'>
          { required: true, message: '${'$'}{title} is required' },
            <#elseif subFieldValidType!=''>
          { required: false},
            </#if>
        <#-- Otherwise, when present, value is validated by regex -->
            <#if subFieldValidType?length gt 0>
            <#assign subMessage = ' has an invalid format'>
            <#if subFieldValidType == 'only' >
                <#assign subMessage = ' must be unique'>
            </#if>
          { pattern: "${subFieldValidType}", message: "${'$'}{title}${subMessage}" }
                <#t>
            </#if>
        </#if>
        ],
    </#if>
