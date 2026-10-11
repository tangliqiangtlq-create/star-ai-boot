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
              Your account on <strong class="text-strong" style="color:#1a1a1a;">${brandName!'Mighty Ramp'}</strong> is ready. Sign in with your email and password to get started.
            </p>
            <#if portalOrigin?? && portalOrigin != ''>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px 0;">
              <tr>
                <td align="center">
                  <!--[if mso]>
                  <v:roundrect xmlns:v="urn:schemas-microsoft-com:vml" href="${portalOrigin}" style="height:46px;v-text-anchor:middle;width:180px;" arcsize="50%" strokecolor="#1a1a1a" fillcolor="#1a1a1a">
                    <w:anchorlock/>
                    <center style="color:#ffffff;font-family:sans-serif;font-size:15px;font-weight:600;">Sign in</center>
                  </v:roundrect>
                  <![endif]-->
                  <!--[if !mso]><!-->
                  <a href="${portalOrigin}" class="btn-primary"
                     style="display:inline-block;background-color:#1a1a1a;color:#ffffff;text-decoration:none;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;font-weight:600;line-height:1;padding:15px 34px;border-radius:999px;">
                    Sign in
                  </a>
                  <!--<![endif]-->
                </td>
              </tr>
            </table>
            </#if>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0 0 12px 0;text-align:center;">
              ${brandName!'Mighty Ramp'} staff will never ask for your password or OTP.
            </p>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0;text-align:center;">
              If you didn't create this account, you can safely ignore this email.
            </p>
</@brandedEmail>
