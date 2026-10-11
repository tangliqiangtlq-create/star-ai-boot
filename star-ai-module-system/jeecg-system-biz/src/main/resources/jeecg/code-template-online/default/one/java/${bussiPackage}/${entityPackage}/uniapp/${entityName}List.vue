<template>
	<view>
	   <!--Title and back-->
		<cu-custom :bgColor="NavBarColor" isBack>
			<block slot="backText">Back</block>
			<block slot="content">${tableVo.ftlDescription}</block>
		</cu-custom>
		<!--Scroll-loaded list-->
		<mescroll-body ref="mescrollRef" bottom="88"  @init="mescrollInit" :up="upOption" :down="downOption" @down="downCallback" @up="upCallback">
		    <view class="cu-list menu">
				<view class="cu-item" v-for="(item,index) in list" :key="index" @click="goHome">
					<view class="flex" style="width:100%">
                        <text class="text-lg" style="color: #000;">
                             {{ item.createBy}}
                        </text>
					</view>
				</view>
			</view>
		</mescroll-body>
	</view>
</template>

<script>
	import MescrollMixin from "@/components/mescroll-uni/mescroll-mixins.js";
	import Mixin from "@/common/mixin/Mixin.js";

	export default {
		name: '${tableVo.ftlDescription}',
		mixins: [MescrollMixin,Mixin],
		data() {
			return {
				CustomBar:this.CustomBar,
				NavBarColor:this.NavBarColor,
				url: "/${entityPackage}/${entityName?uncap_first}/list",
			};
		},
		methods: {
			goHome(){
                this.$Router.push({name: "index"})
			}
		}
	}
</script>

