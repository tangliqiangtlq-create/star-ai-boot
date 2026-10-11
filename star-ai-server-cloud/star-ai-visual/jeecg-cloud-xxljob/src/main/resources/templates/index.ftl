<!DOCTYPE html>
<html>
<head>
  	<#import "./common/common.macro.ftl" as netCommon>
	<@netCommon.commonStyle />
    <!-- daterangepicker -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/bower_components/bootstrap-daterangepicker/daterangepicker.css">
    <title>${I18n.admin_name}</title>
</head>
<body class="hold-transition skin-blue sidebar-mini <#if cookieMap?exists && cookieMap["xxljob_adminlte_settings"]?exists && "off" == cookieMap["xxljob_adminlte_settings"].value >sidebar-collapse</#if> ">
<div class="wrapper">
	<!-- header -->
	<@netCommon.commonHeader />
	<!-- left -->
	<@netCommon.commonLeft "index" />
	
	<!-- Content Wrapper. Contains page content -->
	<div class="content-wrapper">
		<!-- Content Header (Page header) -->
		<section class="content-header">
			<h1>${I18n.job_dashboard_name}</h1>
			<!--
			<h1>Runtime Report<small>Job Scheduling Center</small></h1>
			<ol class="breadcrumb">
				<li><a><i class="fa fa-dashboard"></i>Scheduling Center</a></li>
				<li class="active">Tutorial</li>
			</ol>
			-->
		</section>

		<!-- Main content -->
		<section class="content">

            <!-- Job info -->
            <div class="row">

                <#-- Job info -->
                <div class="col-md-4 col-sm-6 col-xs-12">
                    <div class="info-box bg-aqua">
                        <span class="info-box-icon"><i class="fa fa-flag-o"></i></span>

                        <div class="info-box-content">
                            <span class="info-box-text">${I18n.job_dashboard_job_num}</span>
                            <span class="info-box-number">${jobInfoCount}</span>

                            <div class="progress">
                                <div class="progress-bar" style="width: 100%"></div>
                            </div>
                            <span class="progress-description">${I18n.job_dashboard_job_num_tip}</span>
                        </div>
                    </div>
                </div>

                <#-- Trigger info -->
                <div class="col-md-4 col-sm-6 col-xs-12" >
                    <div class="info-box bg-yellow">
                        <span class="info-box-icon"><i class="fa fa-calendar"></i></span>

                        <div class="info-box-content">
                            <span class="info-box-text">${I18n.job_dashboard_trigger_num}</span>
                            <span class="info-box-number">${jobLogCount}</span>

                            <div class="progress">
                                <div class="progress-bar" style="width: 100%" ></div>
                            </div>
                            <span class="progress-description">
                                ${I18n.job_dashboard_trigger_num_tip}
                                <#--<#if jobLogCount gt 0>
                                    Trigger success rate: ${(jobLogSuccessCount*100/jobLogCount)?string("0.00")}<small>%</small>
                                </#if>-->
                            </span>
                        </div>
                    </div>
                </div>

                <#-- Executors -->
                <div class="col-md-4 col-sm-6 col-xs-12">
                    <div class="info-box bg-green">
                        <span class="info-box-icon"><i class="fa ion-ios-settings-strong"></i></span>

                        <div class="info-box-content">
                            <span class="info-box-text">${I18n.job_dashboard_jobgroup_num}</span>
                            <span class="info-box-number">${executorCount}</span>

                            <div class="progress">
                                <div class="progress-bar" style="width: 100%"></div>
                            </div>
                            <span class="progress-description">${I18n.job_dashboard_jobgroup_num_tip}</span>
                        </div>
                    </div>
                </div>

            </div>

            <#-- Trigger report: date range filter, line chart (left) + pie chart (right) -->
            <div class="row">
                <div class="col-md-12">
                    <div class="box">
                        <div class="box-header with-border">
                            <h3 class="box-title">${I18n.job_dashboard_report}</h3>
                            <#--<input type="text" class="form-control" id="filterTime" readonly >-->

                            <!-- tools box -->
                            <div class="pull-right box-tools">
                                <button type="button" class="btn btn-primary btn-sm daterange pull-right" data-toggle="tooltip" id="filterTime" >
                                    <i class="fa fa-calendar"></i>
                                </button>
                                <#--<button type="button" class="btn btn-primary btn-sm pull-right" data-widget="collapse" data-toggle="tooltip" title="" style="margin-right: 5px;" data-original-title="Collapse">
                                    <i class="fa fa-minus"></i>
                                </button>-->
                            </div>
                            <!-- /. tools -->

                        </div>
                        <div class="box-body">
                            <div class="row">
                                <#-- Line chart (left) -->
                                <div class="col-md-8">
                                    <div id="lineChart" style="height: 350px;"></div>
                                </div>
                                <#-- Pie chart (right) -->
                                <div class="col-md-4">
                                    <div id="pieChart" style="height: 350px;"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

		</section>
		<!-- /.content -->
	</div>
	<!-- /.content-wrapper -->
	
	<!-- footer -->
	<@netCommon.commonFooter />
</div>
<@netCommon.commonScript />
<!-- daterangepicker -->
<script src="${request.contextPath}/static/adminlte/bower_components/moment/moment.min.js"></script>
<script src="${request.contextPath}/static/adminlte/bower_components/bootstrap-daterangepicker/daterangepicker.js"></script>
<#-- echarts -->
<script src="${request.contextPath}/static/plugins/echarts/echarts.common.min.js"></script>
<script src="${request.contextPath}/static/js/index.js"></script>
</body>
</html>
