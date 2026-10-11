<#include "email/_brand_chrome.ftl">
<@brandedEmail pageTitle="Welcome">
            <p class="eyebrow" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;letter-spacing:0.18em;color:#a68b5b;text-transform:uppercase;text-align:center;margin:0 0 12px 0;">
              ACCOUNT READY
            </p>
            <h1 class="title" style="font-family:Georgia,'Times New Roman',serif;font-size:34px;line-height:1.15;color:#1a1a1a;font-weight:400;text-align:center;margin:0 0 28px 0;">
              Welcome
            </h1>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 16px 0;text-align:center;">
              Hi ${username!'there'},
            </p>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 24px 0;text-align:center;">
              An account has been created for you on <strong class="text-strong" style="color:#1a1a1a;">${brandName!'Mighty Ramp'}</strong><#if agentEmail?? && agentEmail != ''> by ${agentEmail}</#if>.
              Change your password as soon as you sign in.
            </p>
            <#if temporaryPassword?? && temporaryPassword != ''>
            <div class="detail-box" style="background-color:#faf7f2;border:1px solid #ece6dc;border-radius:12px;padding:16px 20px;text-align:left;margin:0 0 24px 0;">
              <p class="kv-row" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.7;color:#4a4a4a;margin:0 0 6px 0;">
                <span class="kv-label" style="color:#8a8278;">Sign-in email</span><br />${userEmail!'-'}
              </p>
              <p class="kv-row" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:14px;line-height:1.7;color:#4a4a4a;margin:0;">
                <span class="kv-label" style="color:#8a8278;">Temporary password</span><br />${temporaryPassword}
              </p>
            </div>
            </#if>
            <#if portalOrigin?? && portalOrigin != ''>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px 0;">
              <tr>
                <td align="center">
                  <a href="${portalOrigin}" class="btn-primary"
                     style="display:inline-block;background-color:#1a1a1a;color:#ffffff;text-decoration:none;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;font-weight:600;line-height:1;padding:15px 34px;border-radius:999px;">
                    Sign in
                  </a>
                </td>
              </tr>
            </table>
            </#if>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0 0 12px 0;text-align:center;">
              ${brandName!'Mighty Ramp'} staff will never ask for your password or OTP.
            </p>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0;text-align:center;">
              If you weren't expecting this email, contact support immediately.
            </p>
</@brandedEmail>
