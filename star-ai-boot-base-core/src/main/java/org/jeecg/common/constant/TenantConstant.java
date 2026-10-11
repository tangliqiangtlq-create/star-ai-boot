package org.jeecg.common.constant;

/**
 * @Description: TenantConstant
 * @author: scott
 * @date: 2022Year08moon29day 15:29
 */
public interface TenantConstant {
    /*------【Low-code application parameters]----------------------------------------------*/
    /**
     * lowAppId identifier in the header.
     */
    String X_LOW_APP_ID = "X-Low-App-ID";
    /**
     * App ID — entity field
     */
    String FIELD_LOW_APP_ID = "lowAppId";
    /**
     * App ID — table field
     */
    String DB_FIELD_LOW_APP_ID = "low_app_id";
    /*------【Low-code application parameters]---------------------------------------------*/

    /*--------【TenantParameter】-----------------------------------------------*/
    /**
     * Tenant ID (entity field name and url param name)
     */
    String TENANT_ID = "tenantId";
    /**
     * Tenant ID database column name
     */
    String TENANT_ID_TABLE = "tenant_id";
    /*-------【TenantParameter】-----------------------------------------------*/

    /**
     * super administrator
     */
    String SUPER_ADMIN = "superAdmin";

    /**
     * Organization account admin
     */
    String ACCOUNT_ADMIN = "accountAdmin";

    /**
     * Organization app admin
     */
    String APP_ADMIN = "appAdmin";

}
