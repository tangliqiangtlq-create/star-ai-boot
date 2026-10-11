/*
 *
 *  *  Copyright (c) 2019-2020, Leng Leng (wangiegie@gmail.com).
 *  *  <p>
 *  *  Licensed under the GNU Lesser General Public License 3.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *  <p>
 *  * https://www.gnu.org/licenses/lgpl.html
 *  *  <p>
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.jeecg.common.constant;

/**
 * @author scott
 * @date 2019Year05moon18day
 * Servename
 */
public interface ServiceNameConstants {

	/**
	 * Microservice name: system management module
	 */
	String SERVICE_SYSTEM = "jeecg-system";
	/**
	 * Microservice name: demo module
	 */
	String SERVICE_DEMO = "jeecg-demo";
	/**
	 * Microservice name: joa module
	 */
	String SERVICE_JOA = "jeecg-joa";
	
//	/**
//	 * Microservice name:onlineOnline modules
//	 */
//	String SERVICE_ONLINE = "jeecg-online";
//	/**
//	 * Microservice name:OAmodule
//	 */
//	String SERVICE_EOA = "jeecg-eoa";
//	/**
//	 * Microservice name: form design module
//	 */
//	String SERVICE_FORM = "jeecg-desform";

	/**
	 * gateway passes root path basePath via header
	 */
	String X_GATEWAY_BASE_PATH = "X_GATEWAY_BASE_PATH";

}
