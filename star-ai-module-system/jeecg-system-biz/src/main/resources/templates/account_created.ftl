<#include "email/_brand_chrome.ftl">
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light only">
  <meta name="supported-color-schemes" content="light">
  <title>Account created - ${brandName!'Mighty Ramp'}</title>
  <style>
    <@emailStyles />
  </style>
</head>
<body class="body" style="margin:0;padding:0;background-color:#faf7f2;color:#1a1a1a;">
<table role="presentation" class="email-shell" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="#faf7f2" style="background-color:#faf7f2;">
  <tr>
    <td align="center" style="padding:40px 16px 48px 16px;">

      <@brandLogo />

      <table role="presentation" class="email-card" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="#ffffff"
             style="max-width:560px;background-color:#ffffff;border:1px solid #ece6dc;border-radius:16px;">
        <tr>
          <td style="padding:40px 36px 36px 36px;">

            <p class="eyebrow" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;letter-spacing:0.18em;color:#a68b5b;text-transform:uppercase;text-align:center;margin:0 0 12px 0;">
              ACCOUNT CREATED
            </p>

            <h1 class="title" style="font-family:Georgia,'Times New Roman',serif;font-size:34px;line-height:1.15;color:#1a1a1a;font-weight:400;text-align:center;margin:0 0 28px 0;">
              Set your password
            </h1>

            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 16px 0;text-align:center;">
              Hi ${firstName!'there'},
            </p>

            <#if setPasswordUrl?? && setPasswordUrl != "">
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 24px 0;text-align:center;">
              An account has been created for you on <strong class="text-strong" style="color:#1a1a1a;">${brandName!'Mighty Ramp'}</strong>.
              Set a password to sign in and start moving funds.
            </p>

            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px 0;">
              <tr>
                <td align="center">
                  <!--[if mso]>
                  <v:roundrect xmlns:v="urn:schemas-microsoft-com:vml" href="${setPasswordUrl}" style="height:46px;v-text-anchor:middle;width:220px;" arcsize="50%" strokecolor="#1a1a1a" fillcolor="#1a1a1a">
                    <w:anchorlock/>
                    <center style="color:#ffffff;font-family:sans-serif;font-size:15px;font-weight:600;">Set your password</center>
                  </v:roundrect>
                  <![endif]-->
                  <!--[if !mso]><!-->
                  <a href="${setPasswordUrl}" class="btn-primary"
                     style="display:inline-block;background-color:#1a1a1a;color:#ffffff;text-decoration:none;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;font-weight:600;line-height:1;padding:15px 34px;border-radius:999px;">
                    Set your password
                  </a>
                  <!--<![endif]-->
                </td>
              </tr>
            </table>

            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0 0 12px 0;text-align:center;">
              This link expires in 24 hours. If it stops working, ask your administrator to resend the invite.
              <#if linkExpireTime?? && linkExpireTime != ''>
              <br /><span class="muted-sub" style="font-size:12px;color:#b8b0a6;">(${linkExpireTime})</span>
              </#if>
            </p>

            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0;text-align:center;">
              If you weren't expecting this email, you can safely ignore it.
            </p>

            <#else>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 16px 0;text-align:center;">
              An account has been created for you on <strong class="text-strong" style="color:#1a1a1a;">${brandName!'Mighty Ramp'}</strong>.
            </p>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0;text-align:center;">
              Your password setup link could not be generated automatically. Please contact support at ${supportEmail!''} to finish activating your account.
            </p>
            </#if>

          </td>
        </tr>
      </table>

      <#if setPasswordUrl?? && setPasswordUrl != "">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;margin:24px auto 0 auto;">
        <tr>
          <td align="center" style="padding:0 8px;">
            <p class="paste-label" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#6f6a63;margin:0 0 8px 0;">
              Button not working? Paste this link into your browser:
            </p>
            <p style="margin:0;">
              <a href="${setPasswordUrl}" class="paste-url"
                 style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#a68b5b;word-break:break-all;text-decoration:underline;">
                ${setPasswordUrl}
              </a>
            </p>
          </td>
        </tr>
      </table>

      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;margin:24px auto 0 auto;">
        <tr>
          <td class="divider" style="border-top:1px solid #e0d5c5;height:1px;line-height:1px;font-size:0;">&nbsp;</td>
        </tr>
      </table>
      </#if>

      <@brandFooter sentDate=sentDate!'' />

    </td>
  </tr>
</table>
</body>
</html>
