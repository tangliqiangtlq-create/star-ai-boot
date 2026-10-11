<#include "email/_brand_chrome.ftl">
<@brandedEmail pageTitle="Account locked">
            <p class="eyebrow" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;letter-spacing:0.18em;color:#a68b5b;text-transform:uppercase;text-align:center;margin:0 0 12px 0;">
              SECURITY
            </p>
            <h1 class="title" style="font-family:Georgia,'Times New Roman',serif;font-size:34px;line-height:1.15;color:#1a1a1a;font-weight:400;text-align:center;margin:0 0 28px 0;">
              Account locked
            </h1>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 16px 0;text-align:center;">
              Hi ${username!'there'},
            </p>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 24px 0;text-align:center;">
              Your account on <strong class="text-strong" style="color:#1a1a1a;">${brandName!'Mighty Ramp'}</strong> has been temporarily locked due to suspicious activity.
            </p>
            <div class="detail-box" style="background-color:#faf7f2;border:1px solid #ece6dc;border-radius:12px;padding:16px 20px;text-align:left;margin:0 0 24px 0;">
              <p class="kv-row" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.7;color:#4a4a4a;margin:0 0 6px 0;">
                <span class="kv-label" style="color:#8a8278;">Email</span><br />${email!'-'}
              </p>
              <p class="kv-row" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.7;color:#4a4a4a;margin:0 0 6px 0;">
                <span class="kv-label" style="color:#8a8278;">IP address</span><br />${lockedIp!'-'}
              </p>
              <p class="kv-row" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.7;color:#4a4a4a;margin:0 0 6px 0;">
                <span class="kv-label" style="color:#8a8278;">Duration</span><br />${lockDuration!'-'}
              </p>
              <p class="kv-row" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.7;color:#4a4a4a;margin:0;">
                <span class="kv-label" style="color:#8a8278;">Reason</span><br />${lockReasonText!'-'}
              </p>
            </div>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0 0 12px 0;text-align:center;">
              If this was not you, contact support immediately.
              <#if supportEmail?? && supportEmail != ''>
                <br />${supportEmail}
              </#if>
            </p>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0;text-align:center;">
              The account stays locked until verification is complete.
            </p>
</@brandedEmail>
