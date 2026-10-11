<#-- Brand layer: logo + footer; transparent logo overlaid on beige email background -->
<#macro brandLogo>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="logo-wrap" style="margin:0 auto 24px auto;background:transparent;">
    <tr>
      <td align="center" style="padding:0;background:transparent;">
        <img src="${logo!''}" alt="${brandName!'Mighty Ramp'}" width="180" class="logo-img"
             style="display:block;width:180px;max-width:100%;height:auto;border:0;outline:none;background:transparent;-ms-interpolation-mode:bicubic;" />
      </td>
    </tr>
  </table>
</#macro>

<#macro brandFooter sentDate="">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="footer-wrap" style="margin:28px auto 0 auto;max-width:560px;">
    <tr>
      <td align="center" style="padding:0 8px;">
        <p class="footer-brand" style="margin:0 0 8px 0;font-family:Georgia,'Times New Roman',serif;font-size:16px;font-weight:700;color:#1a1a1a;line-height:1.4;">
          ${brandName!'Mighty Ramp'}
        </p>
        <p class="footer-help" style="margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;color:#8a8278;line-height:1.6;">
          Need help?
          <#if supportEmail?? && supportEmail != ''>
            <a href="mailto:${supportEmail}" class="footer-link" style="color:#8a8278;text-decoration:none;">${supportEmail}</a>
          </#if>
          <#if publicDomain?? && publicDomain != ''>
            · ${publicDomain}
          </#if>
        </p>
        <p class="footer-muted" style="margin:10px 0 0 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:11px;color:#b8b0a6;line-height:1.5;">
          This is an automated account message from ${brandName!'Mighty Ramp'}
        </p>
      </td>
    </tr>
  </table>
</#macro>

<#macro brandedEmail pageTitle>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light only">
  <meta name="supported-color-schemes" content="light">
  <meta name="format-detection" content="email=no, telephone=no">
  <title>${pageTitle} - ${brandName!'Mighty Ramp'}</title>
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
            <#nested>
          </td>
        </tr>
      </table>
      <@brandFooter />
    </td>
  </tr>
</table>
</body>
</html>
</#macro>

<#macro emailStyles>
  :root {
    color-scheme: light only;
    supported-color-schemes: light;
  }
  body {
    margin: 0; padding: 0;
    background-color: #faf7f2 !important;
    color: #1a1a1a !important;
    -webkit-text-size-adjust: 100%;
    -ms-text-size-adjust: 100%;
  }
  .email-shell {
    width: 100%;
    background-color: #faf7f2 !important;
    background-image: linear-gradient(#faf7f2, #faf7f2) !important;
  }
  .email-card {
    background-color: #fffffe !important;
    background-image: linear-gradient(#fffffe, #fffffe) !important;
    border: 1px solid #ece6dc;
    border-radius: 16px;
  }
  .eyebrow {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 11px;
    letter-spacing: 0.18em;
    color: #a68b5b !important;
    text-transform: uppercase;
    text-align: center;
    margin: 0 0 12px 0;
  }
  .title {
    font-family: Georgia, 'Times New Roman', serif;
    font-size: 34px;
    line-height: 1.15;
    color: #1a1a1a !important;
    font-weight: 400;
    text-align: center;
    margin: 0 0 28px 0;
  }
  .body-text {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 15px;
    line-height: 1.65;
    color: #4a4a4a !important;
    margin: 0 0 16px 0;
  }
  .text-strong { color: #1a1a1a !important; }
  .btn-primary {
    display: inline-block;
    background-color: #1a1a1a !important;
    color: #ffffff !important;
    text-decoration: none !important;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 15px;
    font-weight: 600;
    line-height: 1;
    padding: 15px 34px;
    border-radius: 999px;
    mso-padding-alt: 0;
  }
  .muted {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 13px;
    line-height: 1.6;
    color: #8a8278 !important;
    margin: 0;
  }
  .muted-sub { font-size: 12px; color: #b8b0a6 !important; }
  .otp-code {
    display: inline-block;
    background-color: #1a1a1a !important;
    color: #ffffff !important;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 22px;
    font-weight: 600;
    letter-spacing: 0.28em;
    line-height: 1;
    padding: 16px 36px 16px 42px;
    border-radius: 999px;
  }
  .paste-label {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 13px;
    line-height: 1.6;
    color: #6f6a63 !important;
    margin: 0 0 8px 0;
  }
  .paste-url {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 13px;
    line-height: 1.6;
    color: #a68b5b !important;
    word-break: break-all;
    text-decoration: underline;
  }
  .divider {
    border-top: 1px solid #e0d5c5 !important;
    height: 1px;
    line-height: 1px;
    font-size: 0;
  }
  .detail-box {
    background-color: #faf7f2 !important;
    border: 1px solid #ece6dc;
    border-radius: 12px;
    padding: 16px 20px;
    text-align: left;
    margin: 0 0 24px 0;
  }
  .detail-pre {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 14px;
    line-height: 1.7;
    color: #4a4a4a !important;
    white-space: pre-wrap;
    margin: 0;
  }
  .kv-row {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: 14px;
    line-height: 1.7;
    color: #4a4a4a !important;
    margin: 0 0 6px 0;
  }
  .kv-row:last-child { margin-bottom: 0; }
  .kv-label { color: #8a8278 !important; }
  .logo-wrap,
  .logo-img {
    background: transparent !important;
    background-color: transparent !important;
  }
  .footer-brand { color: #1a1a1a !important; }
  .footer-help, .footer-link { color: #8a8278 !important; }
  .footer-muted { color: #b8b0a6 !important; }

  /* Dark-mode email clients: force light appearance (do not follow system inversion) */
  @media (prefers-color-scheme: dark) {
    body, .email-shell {
      background-color: #faf7f2 !important;
      color: #1a1a1a !important;
    }
    .email-card {
      background-color: #fffffe !important;
      background-image: linear-gradient(#fffffe, #fffffe) !important;
      border-color: #ece6dc !important;
    }
    .eyebrow { color: #a68b5b !important; }
    .title, .text-strong, .footer-brand { color: #1a1a1a !important; }
    .body-text { color: #4a4a4a !important; }
    .muted, .footer-help, .footer-link { color: #8a8278 !important; }
    .muted-sub, .footer-muted { color: #b8b0a6 !important; }
    .otp-code {
      background-color: #1a1a1a !important;
      color: #ffffff !important;
    }
    .paste-label { color: #6f6a63 !important; }
    .paste-url { color: #a68b5b !important; }
    .divider { border-top-color: #e0d5c5 !important; }
    .detail-box {
      background-color: #faf7f2 !important;
      border-color: #ece6dc !important;
    }
    .detail-pre, .kv-row { color: #4a4a4a !important; }
    .kv-label { color: #8a8278 !important; }
    .btn-primary {
      background-color: #1a1a1a !important;
      color: #ffffff !important;
    }
    .logo-wrap,
    .logo-img {
      background: transparent !important;
      background-color: transparent !important;
    }
  }

  /* Gmail dark mode */
  u + .body .email-shell,
  u + .body body {
    background-color: #faf7f2 !important;
  }
  u + .body .email-card {
    background-color: #fffffe !important;
    background-image: linear-gradient(#fffffe, #fffffe) !important;
    border-color: #ece6dc !important;
  }
  u + .body .title,
  u + .body .text-strong,
  u + .body .footer-brand { color: #1a1a1a !important; }
  u + .body .body-text { color: #4a4a4a !important; }
  u + .body .eyebrow { color: #a68b5b !important; }
  u + .body .muted,
  u + .body .footer-help { color: #8a8278 !important; }
  u + .body .otp-code {
    background-color: #1a1a1a !important;
    color: #ffffff !important;
  }
  u + .body .paste-url { color: #a68b5b !important; }
  u + .body .btn-primary {
    background-color: #1a1a1a !important;
    color: #ffffff !important;
  }
  u + .body .logo-wrap,
  u + .body .logo-img {
    background: transparent !important;
    background-color: transparent !important;
  }

  /* Outlook.com dark mode */
  [data-ogsc] .email-shell,
  [data-ogsc] body {
    background-color: #faf7f2 !important;
  }
  [data-ogsc] .email-card {
    background-color: #fffffe !important;
    background-image: linear-gradient(#fffffe, #fffffe) !important;
    border-color: #ece6dc !important;
  }
  [data-ogsc] .title,
  [data-ogsc] .text-strong,
  [data-ogsc] .footer-brand { color: #1a1a1a !important; }
  [data-ogsc] .body-text { color: #4a4a4a !important; }
  [data-ogsc] .eyebrow { color: #a68b5b !important; }
  [data-ogsc] .muted,
  [data-ogsc] .footer-help { color: #8a8278 !important; }
  [data-ogsc] .otp-code {
    background-color: #1a1a1a !important;
    color: #ffffff !important;
  }
  [data-ogsc] .paste-url { color: #a68b5b !important; }
  [data-ogsc] .btn-primary {
    background-color: #1a1a1a !important;
    color: #ffffff !important;
  }
  [data-ogsc] .logo-wrap,
  [data-ogsc] .logo-img {
    background: transparent !important;
    background-color: transparent !important;
  }
</#macro>
