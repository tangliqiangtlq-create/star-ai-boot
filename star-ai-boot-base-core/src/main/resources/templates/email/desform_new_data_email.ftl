<html>
<head>
    <meta http-equiv="content-type" content="text/html;charset=utf-8">
</head>

<body>
<div  class="box-content">
    <div  class="info-top">
        <img src="${logo!''}" style="width: 35px;height:35px;" />
        <div style="color:#fff;">
            <strong>[Important] New Data Alert</strong>
        </div>
    </div>
    <div class="info-wrap">
        <div class="tips" style="padding:15px;">
            <p style="margin: 10px 0;">
                Dear ${userName},
            </p>
            Your form <a style="color: #006eff;" href="${formLink}" target="_blank" rel="noopener">[${formName}]</a>
            had 1 new record added on ${createTime}.

            ${dataMarkdown}

            <p>
                To view more, click
                <a style="color: #006eff;" href="${moreLink}" target="_blank" rel="noopener">[View All Data]</a>
            </p>
        </div>
        <div class="footer">QiaoQiao Cloud Platform</div>
        <div class="footer" id="currentTime"></div>
    </div>
    <div style="width: 600px; margin: 0 auto;  margin-top: 50px; font-size: 12px; -webkit-font-smoothing: subpixel-antialiased; text-size-adjust: 100%;">
        <p style="text-align: center; line-height: 20.4px; text-size-adjust: 100%; font-family: 'Microsoft YaHei'!important; padding: 0px !important; margin: 0px !important; color: #7e8890 !important;">
            <span class="appleLinks">Copyright &copy; 2023-2024 Beijing QiaoQiao Cloud Technology Co., Ltd. All rights reserved.</span>
        </p>
        <p style="text-align: center;line-height: 20.4px; text-size-adjust: 100%; font-family: 'Microsoft YaHei'!important; padding: 0px !important; margin: 0px; color: #7e8890 !important; margin-top: 10px;">
            <span class="appleLinks">This email was sent automatically by the system. Please do not reply directly to this email.</span>
        </p>
    </div>
</div>
</body>

<style>
    .box-content{
        width: 80%;
        margin: 20px auto;
        max-width: 800px;
        min-width: 600px;
    }

    .info-top{
        display: flex;
        align-items: center;
        padding: 15px 25px;
        border-top-left-radius: 10px;
        border-top-right-radius: 10px;
        background: #4ea3f2;
        color: #fff;
        overflow: hidden;
        line-height: 32px;
    }

    .info-wrap{
        border-bottom-left-radius: 10px;
        border-bottom-right-radius: 10px;
        border:1px solid #ddd;
        overflow: hidden;
        padding: 15px 15px 20px;
    }

    .footer{
        text-align: right;
        color: #999;
        padding: 0 15px 15px;
    }
</style>

</html>
