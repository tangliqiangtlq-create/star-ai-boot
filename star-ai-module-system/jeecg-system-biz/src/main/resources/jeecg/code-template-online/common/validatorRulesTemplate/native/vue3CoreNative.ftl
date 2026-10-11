<#assign fieldValidType = po.fieldValidType!''>
<#-- Required validation -->
<#if po.nullable == 'N' || fieldValidType == '*'>
{ required: true, message: 'Please enter ${po.filedComment}!'}<#rt>,
<#elseif fieldValidType!=''>
{ required: false}<#rt>,
</#if>
<#-- Uniqueness validation -->
<#if fieldValidType == 'only'>
 { validator: ${po.fieldName}Duplicatevalidate }<#rt>
<#-- 6 to 16 digits -->
<#elseif fieldValidType == 'n6-16'>
 { pattern: /^\d{6,16}$|^(?=\d+\.\d+)[\d.]{7,17}$/, message: 'Please enter 6 to 16 digits!'}<#rt>,
<#-- 6 to 16 characters of any type -->
<#elseif fieldValidType == '*6-16'>
 { pattern: /^.{6,16}$/, message: 'Please enter 6 to 16 characters!'}<#rt>,
<#-- 6 to 18 letter string -->
<#elseif fieldValidType == 's6-18'>
 { pattern: /^[a-z|A-Z]{6,18}$/, message: 'Please enter 6 to 18 letters!'},<#rt>,
<#-- URL -->
<#elseif fieldValidType == 'url'>
 { pattern: /^((ht|f)tps?):\/\/[\w\-]+(\.[\w\-]+)+([\w\-.,@?^=%&:\/~+#]*[\w\-@?^=%&\/~+#])?$/, message: 'Please enter a valid URL!'}<#rt>,
<#-- Email -->
<#elseif fieldValidType == 'e'>
 { pattern: /^([\w]+\.*)([\w]+)@[\w]+\.\w{3}(\.\w{2}|)$/, message: 'Please enter a valid email address!'}<#rt>,
<#-- Mobile phone number -->
<#elseif fieldValidType == 'm'>
 { pattern: /^1[3456789]\d{9}$/, message: 'Please enter a valid mobile phone number!'}<#rt>,
<#-- Postal code -->
<#elseif fieldValidType == 'p'>
 { pattern: /^[0-9]\d{5}$/, message: 'Please enter a valid postal code!'}<#rt>,
<#-- Letters -->
<#elseif fieldValidType == 's'>
 { pattern: /^[A-Z|a-z]+$/, message: 'Please enter letters only!'}<#rt>,
<#-- Number -->
<#elseif fieldValidType == 'n'>
 { pattern: /^-?\d+\.?\d*$/, message: 'Please enter a number!'}<#rt>,
<#-- Integer -->
<#elseif fieldValidType == 'z'>
 { pattern: /^-?\d+$/, message: 'Please enter an integer!'}<#rt>,
<#-- Amount -->
<#elseif fieldValidType == 'money'>
 { pattern: /^(([1-9][0-9]*)|([0]\.\d{0,2}|[1-9][0-9]*\.\d{0,2}))$/, message: 'Please enter a valid amount!'}<#rt>,
<#-- Regex validation -->
<#elseif fieldValidType != '' && fieldValidType != '*'>
 { pattern: '${fieldValidType}', message: 'Does not match the validation rule!'}<#rt>,
<#else>
	<#t>
</#if>
