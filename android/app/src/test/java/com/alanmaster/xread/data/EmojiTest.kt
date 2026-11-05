package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiTest {
    @Test fun decodesKnownCodes() {
        assertEquals("好家伙，尝尝🤔", Emoji.decode("好家伙，尝尝[思考]"))
    }

    @Test fun keepsUnknownBrackets() {
        assertEquals("见[注]与[1]", Emoji.decode("见[注]与[1]"))
    }
}
