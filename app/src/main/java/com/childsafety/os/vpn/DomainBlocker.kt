package com.childsafety.os.vpn

import android.content.Context
import com.childsafety.os.cloud.EventUploader
import com.childsafety.os.util.DeviceUtils

object DomainBlocker {

    fun handle(context: Context, domain: String) {
        if (DomainPolicy.shouldBlockDomain(domain)) {
            EventUploader.logBlockedDomain(
                domain = domain,
                deviceId = DeviceUtils.getDeviceId(context)
            )
        }
    }
}
