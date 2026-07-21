package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun applicationIdIsCorrect() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.aistudio.solidmacro.fctech", appContext.packageName)
  }
}
