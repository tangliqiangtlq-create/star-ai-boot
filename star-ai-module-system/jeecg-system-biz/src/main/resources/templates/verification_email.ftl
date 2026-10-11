<#include "email/_brand_chrome.ftl">
<@brandedEmail pageTitle="Verification code">
            <p class="eyebrow" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;letter-spacing:0.18em;color:#a68b5b;text-transform:uppercase;text-align:center;margin:0 0 12px 0;">
              VERIFICATION CODE
            </p>
            <h1 class="title" style="font-family:Georgia,'Times New Roman',serif;font-size:34px;line-height:1.15;color:#1a1a1a;font-weight:400;text-align:center;margin:0 0 28px 0;">
              Enter this code
            </h1>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 16px 0;text-align:center;">
              Hi ${username!'there'},
            </p>
            <p class="body-text" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.65;color:#4a4a4a;margin:0 0 24px 0;text-align:center;">
              Use this code to confirm your action on <strong class="text-strong" style="color:#1a1a1a;">${brandName!'Mighty Ramp'}</strong>.
            </p>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px 0;">
              <tr>
                <td align="center">
                  <!--[if mso]>
                  <v:roundrect xmlns:v="urn:schemas-microsoft-com:vml" style="height:52px;v-text-anchor:middle;width:240px;" arcsize="50%" strokecolor="#1a1a1a" fillcolor="#1a1a1a">
                    <center style="color:#ffffff;font-family:sans-serif;font-size:22px;font-weight:600;letter-spacing:6px;">${verificationCode!''}</center>
                  </v:roundrect>
                  <![endif]-->
                  <!--[if !mso]><!-->
                  <span class="otp-code" style="display:inline-block;background-color:#1a1a1a;color:#ffffff;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:22px;font-weight:600;line-height:1;letter-spacing:0.28em;padding:16px 36px 16px 42px;border-radius:999px;">
                    ${verificationCode!''}
                  </span>
                  <!--<![endif]-->
                </td>
              </tr>
            </table>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0 0 12px 0;text-align:center;">
              This code expires in 5 minutes. ${brandName!'Mighty Ramp'} staff will never ask you for it.
            </p>
            <p class="muted" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.6;color:#8a8278;margin:0;text-align:center;">
              If you didn't request this code, you can safely ignore this email.
            </p>
</@brandedEmail>
