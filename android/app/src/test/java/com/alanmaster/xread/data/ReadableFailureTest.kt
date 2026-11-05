package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadableFailureTest {
    @Test fun translatesTransportTimeout() {
        // 核心层在注册/请求超时时透出的是裸 "timeout"，直接上屏没人看得懂
        assertEquals("网络超时（内容域名在当前网络下不可达）", readableFailure("timeout"))
        assertEquals("网络超时（内容域名在当前网络下不可达）", readableFailure("Request TIMEOUT"))
    }

    @Test fun translatesDnsAndConnect() {
        assertEquals("域名解析失败", readableFailure("dns error"))
        assertEquals("连接被拒绝或中断", readableFailure("connection reset"))
    }

    @Test fun keepsUnknownTextAndBlankFallback() {
        assertEquals("上游返回 500", readableFailure("上游返回 500"))
        assertEquals("内容加载失败", readableFailure("   "))
    }
}
