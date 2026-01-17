package com.childsafety.os.vpn

import android.content.Context
import com.childsafety.os.cloud.FirebaseManager
import com.childsafety.os.util.DeviceUtils

object DomainBlocker {

    fun handle(context: Context, domain: String) {
        if (DomainPolicy.shouldBlockDomain(domain)) {
            FirebaseManager.logBlockedDomain(
                domain = domain,
                deviceIdParam = DeviceUtils.getDeviceId(context)
            )
        }
    }
}
