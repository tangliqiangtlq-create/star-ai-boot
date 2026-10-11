<#include "email/_brand_chrome.ftl">
<@brandedEmail pageTitle="${itemTitle!'Notification'}">
            <p class="eyebrow" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;letter-spacing:0.18em;color:#a68b5b;text-transform:uppercase;text-align:center;margin:0 0 12px 0;">
              OPERATIONS
            </p>
            <h1 class="title" style="font-family:Georgia,'Times New Roman',serif;font-size:34px;line-height:1.15;color:#1a1a1a;font-weight:400;text-align:center;margin:0 0 28px 0;">
              ${itemTitle!'Update'}
            </h1>
            <#if badgeText?? && badgeText != ''>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#a68b5b;margin:0 0 16px 0;text-align:center;">
              ${badgeText}
            </p>
            </#if>
            <#if introText?? && introText != ''>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 16px 0;text-align:center;">
              ${introText}
            </p>
            </#if>
            <#if itemType?? && itemType != ''>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0 0 16px 0;text-align:center;">
              Type: ${itemType}
            </p>
            </#if>
            <#if detailText?? && detailText != ''>
            <div class="detail-box" style="background-color:#faf7f2;border:1px solid #ece6dc;border-radius:12px;padding:16px 20px;text-align:left;margin:0 0 24px 0;">
              <p class="detail-pre" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.7;color:#4a4a4a;white-space:pre-wrap;margin:0;">${detailText}</p>
            </div>
            </#if>
            <#if outroText?? && outroText != ''>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0;text-align:center;">
              ${outroText}
            </p>
            </#if>
</@brandedEmail>
